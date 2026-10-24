package com.koolikanaku.connect

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koolikanaku.connect.data.ConnectBackend
import com.koolikanaku.connect.location.CoarseLocation
import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.ConnectConstants
import com.koolikanaku.connect.model.ConnectUser
import com.koolikanaku.connect.model.EvidenceSummary
import com.koolikanaku.connect.model.Role
import com.koolikanaku.connect.model.WorkerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Drives the connect-layer screens (Welcome/SignIn through Rate worker). Talks only to the
 * repository interfaces, never to the offline core beyond the small, explicit hand-off in
 * [AppViewModel] (a bookingId on Job, an EvidenceSummary back out). See extra_feature.md Section 8.
 */
class ConnectViewModel(
    private val auth: com.koolikanaku.connect.data.AuthRepository = ConnectBackend.auth,
    private val workers: com.koolikanaku.connect.data.WorkerRepository = ConnectBackend.workers,
    private val bookings: com.koolikanaku.connect.data.BookingRepository = ConnectBackend.bookings,
    private val ratings: com.koolikanaku.connect.data.RatingRepository = ConnectBackend.ratings
) : ViewModel() {

    private var appContext: Context? = null

    var user by mutableStateOf<ConnectUser?>(null)
        private set

    // ---- Sign in / sign up form ----
    var phoneText by mutableStateOf("")
        private set
    var pinText by mutableStateOf("")
        private set
    var nameText by mutableStateOf("")
        private set
    var isSignUp by mutableStateOf(true)
        private set
    var authError by mutableStateOf<String?>(null)
        private set
    var pendingRole by mutableStateOf<Role?>(null)
        private set

    // ---- Worker profile form ----
    var profileLanguages by mutableStateOf("")
        private set
    var profileServices by mutableStateOf(setOf<String>())
        private set
    var profileRateMin by mutableStateOf("")
        private set
    var profileRateMax by mutableStateOf("")
        private set
    var profileBio by mutableStateOf("")
        private set

    // ---- Availability ----
    var openForWork by mutableStateOf(false)
        private set
    var areaLabel by mutableStateOf<String?>(null)
        private set
    var ownerAreaLabel by mutableStateOf<String?>(null)
        private set
    private var ownerGeohash: String? = null

    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        ConnectBackend.init(appContext!!)
        user = auth.currentUser()
        val u = user ?: return
        viewModelScope.launch { openForWork = workers.isOpen(u.uid) }
    }

    val availableServices = listOf("kitchen", "bathroom", "bedroom", "living room", "general")

    fun changePhone(v: String) { phoneText = v.filter { it.isDigit() }.take(10) }
    fun changePin(v: String) { pinText = v.filter { it.isDigit() }.take(6) }
    fun changeName(v: String) { nameText = v }
    fun toggleAuthMode() { isSignUp = !isSignUp; authError = null }
    fun choosePendingRole(role: Role) { pendingRole = role }

    /** Validates the sign-up form before bouncing to Role select (the account isn't created until a role is picked there). */
    fun validateFormForRoleSelect(): Boolean {
        authError = null
        if (nameText.isBlank()) { authError = "Enter your name"; return false }
        if (phoneText.length < 10) { authError = "Enter a 10-digit phone number"; return false }
        if (pinText.length != 6) { authError = "PIN must be 6 digits"; return false }
        return true
    }

    /** The counterpart's phone number for the Call button, once a booking is accepted. Demo sample workers have no real account, so a clearly-fake number is used instead of breaking the demo. */
    suspend fun phoneFor(bookingId: String, requesterId: String): String =
        bookings.counterpartPhone(bookingId, requesterId) ?: "9000000000"

    fun submitAuth(onDone: () -> Unit) {
        val context = appContext ?: return
        authError = null
        if (phoneText.length < 10) { authError = "Enter a 10-digit phone number"; return }
        if (pinText.length != 6) { authError = "PIN must be 6 digits"; return }
        viewModelScope.launch {
            val result = if (isSignUp) {
                if (nameText.isBlank()) { authError = "Enter your name"; return@launch }
                auth.signUp(phoneText, pinText, pendingRole ?: Role.OWNER, nameText)
            } else {
                auth.signIn(phoneText, pinText)
            }
            result.onSuccess {
                user = it
                openForWork = workers.isOpen(it.uid)
                onDone()
            }.onFailure {
                authError = it.message ?: "Something went wrong"
            }
        }
    }

    fun signOut() {
        auth.signOut()
        user = null
        openForWork = false
    }

    // ---- Worker profile ----

    fun loadProfileIfAny() {
        val uid = user?.uid ?: return
        viewModelScope.launch {
            val p = workers.getProfile(uid) ?: return@launch
            profileLanguages = p.languages.joinToString(", ")
            profileServices = p.services.toSet()
            profileRateMin = p.rateMinInr.takeIf { it > 0 }?.toInt()?.toString() ?: ""
            profileRateMax = p.rateMaxInr.takeIf { it > 0 }?.toInt()?.toString() ?: ""
            profileBio = p.bio
        }
    }

    fun changeProfileLanguages(v: String) { profileLanguages = v }
    fun toggleProfileService(service: String) {
        profileServices = if (service in profileServices) profileServices - service else profileServices + service
    }
    fun changeProfileRateMin(v: String) { profileRateMin = v.filter { it.isDigit() } }
    fun changeProfileRateMax(v: String) { profileRateMax = v.filter { it.isDigit() } }
    fun changeProfileBio(v: String) { profileBio = v }

    fun saveProfile(onDone: () -> Unit) {
        val u = user ?: return
        val profile = WorkerProfile(
            uid = u.uid, name = u.name,
            languages = profileLanguages.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            services = profileServices.toList(),
            rateMinInr = profileRateMin.toDoubleOrNull() ?: 0.0,
            rateMaxInr = profileRateMax.toDoubleOrNull() ?: 0.0,
            bio = profileBio
        )
        viewModelScope.launch { workers.saveProfile(profile); onDone() }
    }

    fun workerRateMidpoint(): Double {
        val lo = profileRateMin.toDoubleOrNull() ?: 0.0
        val hi = profileRateMax.toDoubleOrNull() ?: 0.0
        return if (lo > 0 && hi > 0) (lo + hi) / 2 else if (lo > 0) lo else hi
    }

    // ---- Availability ----

    fun toggleOpenForWork(context: Context) {
        val u = user ?: return
        if (openForWork) {
            openForWork = false
            viewModelScope.launch { workers.setAvailability(u.uid, false, null, null, null) }
            return
        }
        val loc = CoarseLocation.lastKnownLocation(context)
        val geohash = loc?.let { CoarseLocation.geohashOf(it) } ?: CoarseLocation.fallbackGeohash()
        val label = if (loc != null) "your area" else CoarseLocation.FALLBACK_AREA_LABEL
        areaLabel = label
        openForWork = true
        viewModelScope.launch {
            workers.setAvailability(u.uid, true, geohash, label, System.currentTimeMillis() + ConnectConstants.AVAILABILITY_WINDOW_MS)
        }
    }

    /** Owner side: resolves a coarse search origin, falling back to Kochi if permission/fix is unavailable. */
    fun resolveOwnerArea(context: Context) {
        val loc = CoarseLocation.lastKnownLocation(context)
        if (loc != null) {
            ownerGeohash = CoarseLocation.geohashOf(loc)
            ownerAreaLabel = "your area"
        } else {
            ownerGeohash = CoarseLocation.fallbackGeohash()
            ownerAreaLabel = CoarseLocation.FALLBACK_AREA_LABEL
        }
    }

    fun nearbyWorkersFlow() = workers.nearbyOpenWorkers(listOfNotNull(ownerGeohash))

    suspend fun workerCard(uid: String) = workers.cardFor(uid)

    fun ratingsFor(workerId: String): Flow<List<com.koolikanaku.connect.model.Rating>> = ratings.ratingsFor(workerId)

    // ---- Bookings ----

    fun bookingsForUser(): Flow<List<Booking>> = bookings.observeForUser(user?.uid ?: "")

    fun booking(id: String): Flow<Booking?> = bookings.observe(id)

    fun requestBooking(workerUid: String, onDone: (String) -> Unit) {
        val u = user ?: return
        viewModelScope.launch {
            val b = bookings.request(
                ownerId = u.uid, workerId = workerUid, whenIso = "now",
                areaLabel = ownerAreaLabel ?: CoarseLocation.FALLBACK_AREA_LABEL, sizeHint = "not specified"
            )
            onDone(b.id)
        }
    }

    fun respondToBooking(bookingId: String, accept: Boolean) {
        viewModelScope.launch { bookings.respond(bookingId, accept) }
    }

    fun setBookingStatus(bookingId: String, status: BookingStatus) {
        viewModelScope.launch { bookings.setStatus(bookingId, status) }
    }

    fun cancelBooking(bookingId: String, reason: String) {
        viewModelScope.launch { bookings.cancel(bookingId, reason) }
    }

    fun completeBooking(bookingId: String, summary: EvidenceSummary) {
        viewModelScope.launch { bookings.complete(bookingId, summary) }
    }

    // ---- Rating ----

    fun submitRating(bookingId: String, stars: Int, tags: List<String>, comment: String, onDone: () -> Unit) {
        viewModelScope.launch { ratings.rate(bookingId, stars, tags, comment); onDone() }
    }
}
