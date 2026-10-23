package com.koolikanaku.connect

import com.koolikanaku.connect.data.InMemorySessionStore
import com.koolikanaku.connect.data.SupabaseConnectRepository
import com.koolikanaku.connect.logic.BookingStateMachine.Action
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.EvidenceSummary
import com.koolikanaku.connect.model.ServiceType
import com.koolikanaku.connect.model.UserRole
import com.koolikanaku.connect.model.WorkerProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import kotlin.concurrent.thread

/** Talks to a tiny local fake of Supabase so request shapes, headers, errors and token renewal are all checked. */
class SupabaseConnectRepositoryTest {

    private class Seen(val method: String, val path: String, val headers: Map<String, String>, val body: String)

    /** A bare-bones HTTP server (the unit-test classpath has no com.sun.net.httpserver). One request per connection. */
    private class MiniServer(private val serve: (Seen) -> Pair<Int, String>) {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port get() = socket.localPort
        @Volatile private var running = true

        init {
            thread(isDaemon = true) {
                while (running) {
                    val client = try { socket.accept() } catch (e: Exception) { break }
                    thread(isDaemon = true) {
                        client.use { c ->
                            val input = c.getInputStream()
                            fun line(): String {
                                val out = ByteArrayOutputStream()
                                while (true) { val b = input.read(); if (b < 0 || b == '\n'.code) break; if (b != '\r'.code) out.write(b) }
                                return out.toString("UTF-8")
                            }
                            val request = line().split(" ")
                            if (request.size < 2) return@use
                            val headers = mutableMapOf<String, String>()
                            while (true) { val h = line(); if (h.isEmpty()) break; headers[h.substringBefore(":").trim().lowercase()] = h.substringAfter(":").trim() }
                            val length = headers["content-length"]?.toIntOrNull() ?: 0
                            val body = ByteArray(length).also { var n = 0; while (n < length) { val r = input.read(it, n, length - n); if (r < 0) break; n += r } }
                            val (code, text) = serve(Seen(request[0], request[1], headers, body.decodeToString()))
                            val bytes = text.toByteArray()
                            c.getOutputStream().apply {
                                write("HTTP/1.1 $code X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                write(bytes); flush()
                            }
                        }
                    }
                }
            }
        }

        fun stop() { running = false; runCatching { socket.close() } }
    }

    private lateinit var server: MiniServer
    private val seen = mutableListOf<Seen>()
    private var handler: (Seen) -> Pair<Int, String> = { 404 to "{}" }
    private val store = InMemorySessionStore()
    private lateinit var repo: SupabaseConnectRepository
    private val base get() = "http://127.0.0.1:${server.port}"

    private val tokenBody = """{"access_token":"tok1","refresh_token":"ref1","user":{"id":"u-1","user_metadata":{"name":"Asha","role":"WORKER","phone":"9000000001"}}}"""

    @Before fun start() {
        server = MiniServer { s -> synchronized(seen) { seen += s }; handler(s) }
        repo = SupabaseConnectRepository(base, "anon-key-1234567890123456", store, pollIntervalMs = 50)
    }

    @After fun stop() { server.stop() }

    private fun decoded(path: String) = URLDecoder.decode(path, "UTF-8")

    // ---------- sign in ----------

    @Test fun anExistingAccountSignsInWithItsPin() = runBlocking {
        handler = { if (it.path.startsWith("/auth/v1/token?grant_type=password")) 200 to tokenBody else 404 to "{}" }
        val user = repo.signIn("+91 90000 00001", "123456", "", UserRole.WORKER).getOrThrow()
        assertEquals("u-1", user.uid)
        assertEquals(UserRole.WORKER, user.role)
        assertEquals(user, repo.currentUser())
        assertEquals("tok1", store.load()?.accessToken)
        val login = JSONObject(seen.single().body)
        assertEquals("9000000001@users.koolikanaku.app", login.getString("email"))
        assertEquals("KK123456", login.getString("password"))
        assertEquals("anon-key-1234567890123456", seen.single().headers["apikey"])
    }

    @Test fun aNewNumberCreatesAnAccountWithNameRoleAndPhone() = runBlocking {
        handler = {
            when {
                it.path.startsWith("/auth/v1/token") -> 400 to """{"error":"invalid_grant","error_description":"Invalid login credentials"}"""
                it.path.startsWith("/auth/v1/signup") -> 200 to tokenBody.replace("WORKER", "OWNER")
                else -> 404 to "{}"
            }
        }
        val user = repo.signIn("9000000001", "123456", "  Omar ", UserRole.OWNER).getOrThrow()
        assertEquals(UserRole.OWNER, user.role)
        val signup = JSONObject(seen.first { it.path.startsWith("/auth/v1/signup") }.body)
        assertEquals("Omar", signup.getJSONObject("data").getString("name"))
        assertEquals("OWNER", signup.getJSONObject("data").getString("role"))
        assertEquals("9000000001", signup.getJSONObject("data").getString("phone"))
    }

    @Test fun aWrongPinForAnExistingNumberIsExplained() = runBlocking {
        handler = {
            when {
                it.path.startsWith("/auth/v1/token") -> 400 to """{"error_description":"Invalid login credentials"}"""
                it.path.startsWith("/auth/v1/signup") -> 422 to """{"msg":"User already registered","error_code":"user_already_exists"}"""
                else -> 404 to "{}"
            }
        }
        val result = repo.signIn("9000000001", "000000", "Asha", UserRole.WORKER)
        assertEquals("Wrong PIN for this number", result.exceptionOrNull()?.message)
        assertNull(repo.currentUser())
    }

    @Test fun anUnknownNumberWithoutANameAsksForOne() = runBlocking {
        handler = { 400 to """{"error_description":"Invalid login credentials"}""" }
        val result = repo.signIn("9000000001", "123456", "", UserRole.OWNER)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("name"))
        assertEquals("no sign-up attempted", 1, seen.size)
    }

    @Test fun serverWithEmailConfirmationOnTellsTheTeamWhatToChange() = runBlocking {
        handler = {
            if (it.path.startsWith("/auth/v1/token")) 400 to "{}" else 200 to """{"id":"u-1","email":"x"}"""
        }
        val result = repo.signIn("9000000001", "123456", "Omar", UserRole.OWNER)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Confirm email"))
    }

    @Test fun signingInWithTheWrongRoleIsRefused() = runBlocking {
        handler = { 200 to tokenBody }   // account is a WORKER
        val result = repo.signIn("9000000001", "123456", "", UserRole.OWNER)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("registered as a worker"))
        assertNull(repo.currentUser())
        assertNull(store.load())
    }

    @Test fun badInputNeverReachesTheServer() = runBlocking {
        assertTrue(repo.signIn("123", "123456", "A", UserRole.OWNER).isFailure)
        assertTrue(repo.signIn("9000000001", "1234", "A", UserRole.OWNER).isFailure)
        assertTrue(seen.isEmpty())
    }

    @Test fun theSessionIsRememberedByANewInstanceAndClearedOnSignOut() = runBlocking {
        handler = { 200 to tokenBody }
        repo.signIn("9000000001", "123456", "", UserRole.WORKER).getOrThrow()
        val reopened = SupabaseConnectRepository(base, "anon-key-1234567890123456", store)
        assertEquals("u-1", reopened.currentUser()?.uid)
        reopened.signOut()
        assertNull(reopened.currentUser())
        assertNull(store.load())
    }

    // ---------- requests carry the signed-in token ----------

    private fun signedIn() = runBlocking {
        val prev = handler
        handler = { 200 to tokenBody }
        repo.signIn("9000000001", "123456", "", UserRole.WORKER).getOrThrow()
        handler = prev
        seen.clear()
    }

    @Test fun savingAProfileUpsertsOnlyTheEditableColumns() = runBlocking {
        signedIn()
        handler = { 201 to "" }
        repo.saveProfile(WorkerProfile("u-1", "Asha", "x", listOf("Malayalam"), listOf(ServiceType.KITCHEN, ServiceType.GENERAL), 200, 300, "hi")).getOrThrow()
        val call = seen.single()
        assertEquals("POST", call.method)
        assertEquals("/rest/v1/workers?on_conflict=id", call.path)
        assertEquals("Bearer tok1", call.headers["authorization"])
        assertEquals("resolution=merge-duplicates,return=minimal", call.headers["prefer"])
        val body = JSONObject(call.body)
        assertEquals(setOf("id", "name", "languages", "services", "rate_min", "rate_max", "bio"), body.keys().asSequence().toSet())
        assertEquals("KITCHEN", body.getJSONArray("services").getString(0))
    }

    @Test fun goingOpenSendsACoarseGeohashAndAnIsoExpiry() = runBlocking {
        signedIn()
        handler = { 200 to """{"id":"u-1","open":true}""" }
        repo.setAvailability("u-1", true, "ezs42e", 1_800_000_000_000L).getOrThrow()
        val call = seen.single()
        assertEquals("/rest/v1/rpc/set_availability", call.path)
        val body = JSONObject(call.body)
        assertTrue(body.getBoolean("p_open"))
        assertEquals("ezs42e", body.getString("p_geohash"))
        assertEquals("2027-01-15T08:00:00Z", body.getString("p_expires"))
        repo.setAvailability("u-1", false, null, null).getOrThrow()
        val off = JSONObject(seen.last().body)
        assertFalse(off.getBoolean("p_open")); assertTrue(off.isNull("p_geohash")); assertTrue(off.isNull("p_expires"))
    }

    @Test fun nearbySearchAsksForOpenUnexpiredWorkersInTheCellsAndParsesThem() = runBlocking {
        signedIn()
        handler = {
            """[{"id":"w-1","name":"Lakshmi","languages":["Malayalam"],"services":["KITCHEN","BOGUS"],"rate_min":220,"rate_max":300,"bio":"","is_demo":false,
                "open":true,"geohash":"ezs42e","expires_at":"2027-01-15T08:00:00+00:00","rating_count":24,"verified_count":19,
                "weighted_star_sum":110.4,"weight_sum":21.5,"completed_count":30,"cancel_count":1}]""".let { json -> 200 to json }
        }
        val listing = withTimeout(5000) { repo.observeOpenWorkers(listOf("ezs42", "ezs43")).first() }.single()
        val path = decoded(seen.first { it.path.contains("/rest/v1/workers") }.path)
        assertTrue(path, path.contains("open=eq.true"))
        assertTrue(path, path.contains("or=(geohash.like.ezs42*,geohash.like.ezs43*)"))
        assertTrue(path, path.contains("expires_at=gt."))
        assertEquals("Lakshmi", listing.profile.name)
        assertEquals(listOf(ServiceType.KITCHEN), listing.profile.services)
        assertEquals("ezs42e", listing.availability.geohash)
        assertEquals(1_800_000_000_000L, listing.availability.expiresAtMs)
        assertEquals(24, listing.stats.ratingCount)
        assertEquals(19, listing.stats.verifiedCount)
        assertEquals(110.4, listing.stats.weightedStarSum, 1e-9)
    }

    private fun bookingJson(status: String, ownerPhone: String? = null) = """{"id":"b-1","owner_id":"o-1","worker_id":"u-1","owner_name":"Omar","worker_name":"Asha",
        "status":"$status","requested_for":"now","area_label":"Edappally","size_hint":"2BHK","owner_phone":${ownerPhone?.let { "\"$it\"" } ?: "null"},"worker_phone":null,
        "cancelled_by":null,"evidence":null,"job_id":null,"created_at":"2026-10-24T05:00:00.123456+00:00","updated_at":"2026-10-24T05:01:00+00:00"}"""

    @Test fun requestingABookingCallsTheRpcAndParsesTheBooking() = runBlocking {
        signedIn()
        handler = { 200 to bookingJson("REQUESTED") }
        val b = repo.request("o-1", "u-1", "now", "Edappally", "2BHK").getOrThrow()
        val call = seen.single()
        assertEquals("/rest/v1/rpc/request_booking", call.path)
        assertEquals("u-1", JSONObject(call.body).getString("p_worker"))
        assertEquals(BookingStatus.REQUESTED, b.status)
        assertEquals("Edappally", b.areaLabel)
        assertNull(b.workerPhone)
        assertEquals(1_792_818_000_123L, b.createdAtMs)
    }

    @Test fun performSendsTheActionEvidenceAndJobAndReadsRevealedPhones() = runBlocking {
        signedIn()
        handler = { 200 to bookingJson("COMPLETED", ownerPhone = "9000000009") }
        val evidence = EvidenceSummary("a".repeat(64), 400, 520, 1, 4, listOf("b".repeat(64)), "2026-10-24T05:00:00Z")
        val b = repo.perform("b-1", "u-1", Action.COMPLETE, evidence = evidence, jobId = "job-1").getOrThrow()
        val body = JSONObject(seen.single().body)
        assertEquals("COMPLETE", body.getString("p_action"))
        assertEquals("job-1", body.getString("p_job"))
        assertEquals("b".repeat(64), body.getJSONObject("p_evidence").getJSONArray("afterPhotoHashes").getString(0))
        assertEquals("9000000009", b.ownerPhone)
    }

    @Test fun serverRuleErrorsAreShownInPlainWords() = runBlocking {
        signedIn()
        handler = { 400 to """{"code":"P0001","message":"Finish your current job before accepting another","details":null,"hint":null}""" }
        val r = repo.perform("b-1", "u-1", Action.ACCEPT)
        assertEquals("Finish your current job before accepting another", r.exceptionOrNull()?.message)
        handler = { 500 to "<html>gateway</html>" }
        assertEquals("Server error (500)", repo.perform("b-1", "u-1", Action.ACCEPT).exceptionOrNull()?.message)
    }

    @Test fun ratingSendsStarsAndTagsAndParsesTheVerifiedFlag() = runBlocking {
        signedIn()
        handler = { 200 to """{"id":"r-1","booking_id":"b-1","worker_id":"u-1","owner_id":"o-1","stars":5,"tags":["on_time"],"comment":"Great","verified":true,"created_at":"2026-10-24T06:00:00+00:00"}""" }
        val r = repo.rate("b-1", "o-1", 5, listOf("on_time"), "Great").getOrThrow()
        assertTrue(r.verified)
        assertEquals(listOf("on_time"), r.tags)
        val body = JSONObject(seen.single().body)
        assertEquals(5, body.getInt("p_stars"))
        assertEquals("on_time", body.getJSONArray("p_tags").getString(0))
    }

    @Test fun bookingListExpiresStaleRequestsFirstAndKeepsOnlyMine() = runBlocking {
        signedIn()
        handler = {
            if (it.method == "POST") 200 to ""
            else 200 to "[" + bookingJson("REQUESTED") + "," + bookingJson("RATED").replace("\"b-1\"", "\"b-2\"").replace("\"u-1\"", "\"other\"").replace("\"o-1\"", "\"other2\"") + "]"
        }
        val list = withTimeout(5000) { repo.observeFor("u-1").first() }
        assertEquals(listOf("b-1"), list.map { it.id })
        assertEquals("/rest/v1/rpc/expire_stale_bookings", seen.first().path)
        assertTrue(decoded(seen.last().path).contains("order=created_at.desc"))
    }

    // ---------- tokens and connection problems ----------

    @Test fun anExpiredTokenIsRenewedOnceAndTheRequestRetried() = runBlocking {
        signedIn()
        var rpcCalls = 0
        handler = {
            when {
                it.path.startsWith("/auth/v1/token?grant_type=refresh_token") ->
                    200 to """{"access_token":"tok2","refresh_token":"ref2","user":{"id":"u-1"}}"""
                it.path.contains("request_booking") -> { rpcCalls++; if (it.headers["authorization"] == "Bearer tok1") 401 to """{"message":"JWT expired"}""" else 200 to bookingJson("REQUESTED") }
                else -> 404 to "{}"
            }
        }
        repo.request("o-1", "u-1", "now", "x", "y").getOrThrow()
        assertEquals(2, rpcCalls)
        assertEquals("tok2", store.load()?.accessToken)
        assertEquals("ref1", JSONObject(seen.first { it.path.contains("refresh_token") }.body).getString("refresh_token"))
        assertEquals("the same person is still signed in", "u-1", repo.currentUser()?.uid)
    }

    @Test fun aFailedRenewalSignsThePersonOut() = runBlocking {
        signedIn()
        handler = { if (it.path.contains("refresh_token")) 400 to """{"error_description":"Invalid Refresh Token"}""" else 401 to """{"message":"JWT expired"}""" }
        assertTrue(repo.request("o-1", "u-1", "now", "x", "y").isFailure)
        assertNull(repo.currentUser())
    }

    @Test fun anUnreachableServerGivesAReadableErrorAndSetsTheStatus() = runBlocking {
        signedIn()
        server.stop()
        val r = repo.request("o-1", "u-1", "now", "x", "y")
        assertEquals("No connection to the server", r.exceptionOrNull()?.message)
        assertEquals("No connection to the server", repo.lastError.value)
    }

    @Test fun requestsBeforeSigningInUseTheAnonymousKeyOnly() = runBlocking {
        handler = { 200 to "[]" }
        withTimeout(5000) { repo.ratingsFor("w-1").first() }
        assertEquals("Bearer anon-key-1234567890123456", seen.first().headers["authorization"])
        assertFalse(repo.isDemo)
        assertNotNull(repo.lastError)
    }
}
