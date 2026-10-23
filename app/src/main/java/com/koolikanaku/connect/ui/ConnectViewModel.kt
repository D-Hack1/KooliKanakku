package com.koolikanaku.connect.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.koolikanaku.connect.data.ConnectBackend
import com.koolikanaku.connect.data.ConnectServices
import com.koolikanaku.connect.location.CoarseLocation
import com.koolikanaku.connect.logic.BookingStateMachine.Action
import com.koolikanaku.connect.logic.Geo
import com.koolikanaku.connect.logic.NearbyRanking
import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.ConnectUser
import com.koolikanaku.connect.model.EvidenceSummary
import com.koolikanaku.connect.model.Rating
import com.koolikanaku.connect.model.ServiceType
import com.koolikanaku.connect.model.UserRole
import com.koolikanaku.connect.model.WorkerCard
import com.koolikanaku.connect.model.WorkerListing
import com.koolikanaku.connect.model.WorkerProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How the owner's position was found. */
enum class LocationState { NOT_ASKED, LOADING, FOUND, NO_PERMISSION, UNAVAILABLE }

/** State and actions for the whole find-a-worker layer. The estimate flow has its own ViewModel. */
class ConnectViewModel(app: Application) : AndroidViewModel(app) {

    private val backend: ConnectBackend = ConnectServices.get(app)
    val isDemo: Boolean get() = backend.isDemo
    val serverProblem get() = backend.lastError

    // ---- account ----
    var user by mutableStateOf(backend.currentUser())
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    fun clearMessage() { message = null }

    // ---- worker side ----
    var profile by mutableStateOf<WorkerProfile?>(null)
        private set
    var visibleUntilMs by mutableStateOf<Long?>(null)
        private set
    var nowMs by mutableStateOf(System.currentTimeMillis())
        private set
    val isVisibleNow: Boolean get() = (visibleUntilMs ?: 0L) > nowMs

    // ---- owner side ----
    var ownerLocation by mutableStateOf<Geo.LatLng?>(null)
        private set
    var locationState by mutableStateOf(LocationState.NOT_ASKED)
        private set
    var serviceFilter by mutableStateOf<ServiceType?>(null)
    private var rawListings by mutableStateOf<List<WorkerListing>>(emptyList())
    val cards: List<WorkerCard>
        get() = ownerLocation?.let { NearbyRanking.rank(rawListings, it, nowMs, service = serviceFilter) }.orEmpty()
    var ratings by mutableStateOf<Map<String, List<Rating>>>(emptyMap())
        private set

    // ---- bookings (both roles) ----
    var bookings by mutableStateOf<List<Booking>>(emptyList())
        private set
    fun booking(id: String): Booking? = bookings.firstOrNull { it.id == id }

    private var bookingsJob: Job? = null
    private var searchJob: Job? = null
    private var tickJob: Job? = null

    init {
        if (user != null) afterSignIn()
    }

    // ------------------------------------------------------------ account

    fun signIn(phone: String, pin: String, name: String, role: UserRole, onDone: () -> Unit = {}) {
        if (busy) return
        busy = true; message = null
        viewModelScope.launch {
            backend.signIn(phone, pin, name, role)
                .onSuccess { user = it; afterSignIn(); onDone() }
                .onFailure { message = it.message }
            busy = false
        }
    }

    fun signOut() {
        bookingsJob?.cancel(); searchJob?.cancel(); tickJob?.cancel()
        backend.signOut()
        user = null; profile = null; visibleUntilMs = null; bookings = emptyList(); rawListings = emptyList()
        ownerLocation = null; locationState = LocationState.NOT_ASKED; ratings = emptyMap()
    }

    private fun afterSignIn() {
        val me = user ?: return
        bookingsJob?.cancel()
        bookingsJob = viewModelScope.launch { backend.observeFor(me.uid).collect { bookings = it } }
        tickJob?.cancel()
        tickJob = viewModelScope.launch { while (true) { nowMs = System.currentTimeMillis(); delay(15_000) } }
        if (me.role == UserRole.WORKER) viewModelScope.launch {
            profile = backend.profileOf(me.uid)
            visibleUntilMs = backend.availabilityOf(me.uid)?.takeIf { it.open }?.expiresAtMs
        }
    }

    // ------------------------------------------------------------ worker

    fun saveProfile(name: String, languages: List<String>, services: List<ServiceType>, rateMin: Int, rateMax: Int, bio: String, onDone: () -> Unit = {}) {
        val me = user ?: return
        busy = true; message = null
        viewModelScope.launch {
            val p = WorkerProfile(me.uid, name.trim().take(60), me.phone, languages, services, rateMin, rateMax, bio.trim().take(280))
            backend.saveProfile(p).onSuccess { profile = p; onDone() }.onFailure { message = it.message }
            busy = false
        }
    }

    /** Goes visible for [hours] hours using an approximate location, or goes invisible. */
    fun setOpen(open: Boolean, hours: Int) {
        val me = user ?: return
        if (profile == null) { message = "Save your profile first"; return }
        busy = true; message = null
        viewModelScope.launch {
            if (!open) {
                backend.setAvailability(me.uid, false, null, null).onSuccess { visibleUntilMs = null }.onFailure { message = it.message }
            } else {
                val here = CoarseLocation.current(getApplication())
                if (here == null) {
                    locationState = if (CoarseLocation.hasPermission(getApplication())) LocationState.UNAVAILABLE else LocationState.NO_PERMISSION
                    message = if (locationState == LocationState.NO_PERMISSION) "Allow location so owners nearby can find you" else "Could not find your location. Check that location is on"
                } else {
                    val expires = System.currentTimeMillis() + hours * 3_600_000L
                    backend.setAvailability(me.uid, true, Geo.encode(here.lat, here.lng, Geo.STORED_PRECISION), expires)
                        .onSuccess { visibleUntilMs = expires; locationState = LocationState.FOUND }
                        .onFailure { message = it.message }
                }
            }
            busy = false
        }
    }

    // ------------------------------------------------------------ owner

    fun findLocation() {
        if (locationState == LocationState.LOADING) return
        locationState = LocationState.LOADING
        viewModelScope.launch {
            val here = CoarseLocation.current(getApplication())
            if (here == null) {
                locationState = if (CoarseLocation.hasPermission(getApplication())) LocationState.UNAVAILABLE else LocationState.NO_PERMISSION
            } else {
                useLocation(here)
            }
        }
    }

    fun onPermissionDenied() {
        locationState = LocationState.NO_PERMISSION
        if (user?.role == UserRole.WORKER) message = "Allow location so owners nearby can find you"
    }

    /** Searches around a fixed town when no location can be had (clearly labelled as a fallback in the UI). */
    fun useFallbackLocation() = useLocation(Geo.LatLng(9.9312, 76.2673))   // Kochi

    private fun useLocation(center: Geo.LatLng) {
        ownerLocation = center
        locationState = LocationState.FOUND
        backend.prepareForLocation(center)
        searchJob?.cancel()
        searchJob = viewModelScope.launch { backend.observeOpenWorkers(Geo.searchPrefixes(center.lat, center.lng)).collect { rawListings = it } }
    }

    fun loadRatings(workerId: String) {
        viewModelScope.launch { backend.ratingsFor(workerId).collect { ratings = ratings + (workerId to it) } }
    }

    fun requestBooking(workerId: String, area: String, size: String, onDone: (Booking) -> Unit) {
        val me = user ?: return
        if (area.isBlank()) { message = "Tell the worker which area you are in"; return }
        busy = true; message = null
        viewModelScope.launch {
            backend.request(me.uid, workerId, "now", area.trim().take(80), size.trim().take(80))
                .onSuccess { onDone(it) }.onFailure { message = it.message }
            busy = false
        }
    }

    // ------------------------------------------------------------ bookings

    fun perform(bookingId: String, action: Action, evidence: EvidenceSummary? = null, jobId: String? = null, onDone: (Booking) -> Unit = {}) {
        val me = user ?: return
        busy = true; message = null
        viewModelScope.launch {
            backend.perform(bookingId, me.uid, action, evidence, jobId).onSuccess { onDone(it) }.onFailure { message = it.message }
            busy = false
        }
    }

    fun rate(bookingId: String, stars: Int, tags: List<String>, comment: String, onDone: () -> Unit = {}) {
        val me = user ?: return
        busy = true; message = null
        viewModelScope.launch {
            backend.rate(bookingId, me.uid, stars, tags, comment).onSuccess { onDone() }.onFailure { message = it.message }
            busy = false
        }
    }

    /** The worker's own rate, used to prefill the estimate for both parties. */
    suspend fun workerRateFor(booking: Booking): Int =
        backend.profileOf(booking.workerId)?.let { (it.rateMinInr + it.rateMaxInr) / 2 } ?: 250

    val activeBookings: List<Booking>
        get() = bookings.filter { it.status == BookingStatus.REQUESTED || it.status == BookingStatus.ACCEPTED || it.status == BookingStatus.ARRIVED || it.status == BookingStatus.IN_PROGRESS || it.status == BookingStatus.COMPLETED }
}
