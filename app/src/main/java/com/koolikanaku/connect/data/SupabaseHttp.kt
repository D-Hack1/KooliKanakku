package com.koolikanaku.connect.data

import com.koolikanaku.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Supabase client (no SDK dependency - this project otherwise ships with no networking
 * library at all, so a small hand-rolled client over HttpURLConnection + org.json, matching the
 * style JobStorage.kt already uses for JSON, is less risk than pulling in a multiplatform SDK and
 * its Ktor/serialization transitive graph). Covers two APIs:
 *  - auth/v1 endpoints - real Supabase Auth (email+password under the hood; see SupabaseConnectRepository
 *    for why email+password rather than phone+SMS OTP, which this project has no provider for).
 *  - rest/v1 endpoints - PostgREST, for RPC calls and public-read tables/views.
 * Only used by [SupabaseConnectRepository] - never by ai/domain/capture/export (OfflineCoreGuardTest).
 */
object SupabaseHttp {

    val isConfigured: Boolean get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    class ApiException(message: String) : Exception(message)

    /**
     * The signed-in user's access token, used as the `Authorization` bearer for every `/rest/v1`
     * call so PostgREST's `auth.uid()` resolves to them (schema.sql's RPC functions all rely on
     * this). Falls back to the anon key when nobody is signed in yet. Set by
     * [SupabaseConnectRepository] right after sign-up/sign-in/refresh.
     */
    @Volatile var sessionAccessToken: String? = null

    // ---------------- Auth (/auth/v1) ----------------

    /** POST /auth/v1/signup. Project must have "Confirm email" OFF (see schema.sql) or no session comes back. */
    suspend fun authSignUp(email: String, password: String): JSONObject = authCall("signup", mapOf("email" to email, "password" to password))

    /** POST /auth/v1/token?grant_type=password */
    suspend fun authSignIn(email: String, password: String): JSONObject = authCall("token?grant_type=password", mapOf("email" to email, "password" to password))

    /** POST /auth/v1/token?grant_type=refresh_token */
    suspend fun authRefresh(refreshToken: String): JSONObject = authCall("token?grant_type=refresh_token", mapOf("refresh_token" to refreshToken))

    private suspend fun authCall(path: String, body: Map<String, Any?>): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${BuildConfig.SUPABASE_URL}/auth/v1/$path")
        val headers = mapOf(
            "apikey" to BuildConfig.SUPABASE_ANON_KEY,
            "Authorization" to "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
            "Content-Type" to "application/json"
        )
        request("POST", url, JSONObject(body.mapValues { it.value }).toString(), headers) as JSONObject
    }

    // ---------------- PostgREST (/rest/v1) ----------------

    /** Calls `POST /rest/v1/rpc/<function>` with named params matching the SQL function's p_* args. */
    suspend fun rpc(function: String, params: Map<String, Any?>, expectSingleObject: Boolean = true): Any = withContext(Dispatchers.IO) {
        val url = URL("${BuildConfig.SUPABASE_URL}/rest/v1/rpc/$function")
        val body = JSONObject(params.filterValues { true }.mapValues { (_, v) -> wrapValue(v) }).toString()
        request("POST", url, body, restHeaders(expectSingleObject, hasBody = true))
    }

    /** Calls `GET /rest/v1/<table or view>?<query>` (public-read tables/views only, e.g. open_workers, ratings). */
    suspend fun get(path: String, query: String): JSONArray = withContext(Dispatchers.IO) {
        val url = URL("${BuildConfig.SUPABASE_URL}/rest/v1/$path?$query")
        request("GET", url, null, restHeaders(expectSingleObject = false, hasBody = false)) as JSONArray
    }

    private fun restHeaders(expectSingleObject: Boolean, hasBody: Boolean): Map<String, String> {
        val token = sessionAccessToken ?: BuildConfig.SUPABASE_ANON_KEY
        val base = mutableMapOf(
            "apikey" to BuildConfig.SUPABASE_ANON_KEY,
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json"
        )
        if (expectSingleObject) base["Accept"] = "application/vnd.pgrst.object+json"
        if (hasBody) base["Prefer"] = "return=representation"
        return base
    }

    private fun wrapValue(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is List<*> -> JSONArray(v)
        is Map<*, *> -> JSONObject(v)
        else -> v
    }

    private fun request(method: String, url: URL, body: String?, headers: Map<String, String>): Any {
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text) }.getOrNull()?.let { err ->
                    err.optString("error_description").takeIf { it.isNotBlank() }
                        ?: err.optString("msg").takeIf { it.isNotBlank() }
                        ?: err.optString("message").takeIf { it.isNotBlank() }
                        ?: err.optString("error").takeIf { it.isNotBlank() }
                } ?: "Request failed ($code)"
                throw ApiException(message)
            }
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return JSONObject()
            return when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONObject(trimmed)
                trimmed == "null" -> JSONObject.NULL
                trimmed.startsWith("\"") -> JSONArray("[$trimmed]").getString(0) // reuse org.json's string unescaping
                trimmed == "true" || trimmed == "false" -> trimmed.toBoolean()
                else -> trimmed.toDoubleOrNull() ?: trimmed
            }
        } finally {
            conn.disconnect()
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
