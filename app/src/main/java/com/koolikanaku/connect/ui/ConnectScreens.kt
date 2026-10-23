@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.koolikanaku.connect.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.koolikanaku.R
import com.koolikanaku.connect.logic.BookingStateMachine.Action
import com.koolikanaku.connect.logic.TrustScore
import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.ServiceType
import com.koolikanaku.connect.model.UserRole
import com.koolikanaku.connect.model.WorkerCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================ shared pieces

@Composable
private fun Header(title: String, subtitle: String? = null, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, top = 8.dp, end = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.connect_back)) }
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action?.invoke()
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) { Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
}

@Composable
private fun Chip(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer) {
    Surface(shape = RoundedCornerShape(999.dp), color = color) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
    }
}

@Composable
private fun Banners(vm: ConnectViewModel) {
    if (vm.isDemo) Text(stringResource(R.string.connect_demo_banner), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    val problem by vm.serverProblem.collectAsState()
    problem?.let { Text(stringResource(R.string.connect_server_problem, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    vm.message?.takeIf { it != problem }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
}

private fun serviceLabel(s: ServiceType) = s.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun timeText(ms: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
private fun statusRes(s: BookingStatus) = when (s) {
    BookingStatus.REQUESTED -> R.string.connect_status_REQUESTED
    BookingStatus.ACCEPTED -> R.string.connect_status_ACCEPTED
    BookingStatus.DECLINED -> R.string.connect_status_DECLINED
    BookingStatus.EXPIRED -> R.string.connect_status_EXPIRED
    BookingStatus.CANCELLED -> R.string.connect_status_CANCELLED
    BookingStatus.ARRIVED -> R.string.connect_status_ARRIVED
    BookingStatus.IN_PROGRESS -> R.string.connect_status_IN_PROGRESS
    BookingStatus.COMPLETED -> R.string.connect_status_COMPLETED
    BookingStatus.RATED -> R.string.connect_status_RATED
}

@Composable
private fun StarsText(card: WorkerCard) {
    val avg = card.stats.averageStars
    Text(
        if (avg == null) stringResource(R.string.connect_new_worker) else stringResource(R.string.connect_stars, "%.1f".format(Locale.US, card.trustScore)),
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary
    )
}

// ============================================================ sign in

@Composable
fun ConnectAuthScreen(vm: ConnectViewModel, onBack: () -> Unit, onSignedIn: () -> Unit) {
    var role by remember { mutableStateOf(UserRole.OWNER) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    Scaffold(topBar = { Header(stringResource(R.string.connect_auth_title), stringResource(R.string.connect_auth_subtitle), onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Banners(vm)
            Text(stringResource(R.string.connect_role_question), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoleCard(stringResource(R.string.connect_role_owner), stringResource(R.string.connect_role_owner_desc), role == UserRole.OWNER, Modifier.weight(1f)) { role = UserRole.OWNER }
                RoleCard(stringResource(R.string.connect_role_worker), stringResource(R.string.connect_role_worker_desc), role == UserRole.WORKER, Modifier.weight(1f)) { role = UserRole.WORKER }
            }
            OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.connect_name_label)) }, supportingText = { Text(stringResource(R.string.connect_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16) }, label = { Text(stringResource(R.string.connect_phone_label)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, label = { Text(stringResource(R.string.connect_pin_label)) }, supportingText = { Text(stringResource(R.string.connect_pin_hint)) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.signIn(phone, pin, name, role, onSignedIn) }, enabled = !vm.busy && phone.length >= 10 && pin.length == 6, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connect_sign_in_button))
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connect_without_account)) }
        }
    }
}

@Composable
private fun RoleCard(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================================================ home (worker or owner)

@Composable
fun ConnectHomeScreen(vm: ConnectViewModel, onBack: () -> Unit, onOpenBooking: (String) -> Unit) {
    val me = vm.user ?: return
    Scaffold(
        topBar = {
            Header(stringResource(R.string.connect_hello, me.name), stringResource(if (me.role == UserRole.WORKER) R.string.connect_role_worker else R.string.connect_role_owner), onBack) {
                TextButton(onClick = { vm.signOut(); onBack() }) { Text(stringResource(R.string.connect_sign_out)) }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Banners(vm) }
            if (me.role == UserRole.WORKER) workerSections(vm, onOpenBooking) else ownerSections(vm, onOpenBooking)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.workerSections(vm: ConnectViewModel, onOpenBooking: (String) -> Unit) {
    item { WorkerProfilePanel(vm) }
    item { VisibilityPanel(vm) }
    item {
        Panel {
            Text(stringResource(R.string.connect_requests_title), style = MaterialTheme.typography.titleLarge)
            val requests = vm.bookings.filter { it.status == BookingStatus.REQUESTED }
            if (requests.isEmpty()) Text(stringResource(R.string.connect_no_requests), color = MaterialTheme.colorScheme.onSurfaceVariant)
            requests.forEach { b ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.connect_request_line, b.ownerName, b.areaLabel), style = MaterialTheme.typography.titleSmall)
                    if (b.sizeHint.isNotBlank()) Text(stringResource(R.string.connect_size_line, b.sizeHint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.perform(b.id, Action.ACCEPT) { onOpenBooking(b.id) } }, enabled = !vm.busy) { Text(stringResource(R.string.connect_accept)) }
                        OutlinedButton(onClick = { vm.perform(b.id, Action.DECLINE) }, enabled = !vm.busy) { Text(stringResource(R.string.connect_decline)) }
                    }
                }
            }
        }
    }
    item { BookingList(vm, onOpenBooking) }
}

@Composable
private fun WorkerProfilePanel(vm: ConnectViewModel) {
    val me = vm.user ?: return
    val saved = vm.profile
    var editing by remember(saved) { mutableStateOf(saved == null) }
    var name by remember(saved) { mutableStateOf(saved?.name ?: me.name) }
    var services by remember(saved) { mutableStateOf(saved?.services ?: listOf(ServiceType.GENERAL)) }
    var languages by remember(saved) { mutableStateOf(saved?.languages ?: listOf("Malayalam")) }
    var rateMin by remember(saved) { mutableStateOf((saved?.rateMinInr ?: 200).toString()) }
    var rateMax by remember(saved) { mutableStateOf((saved?.rateMaxInr ?: 300).toString()) }
    var bio by remember(saved) { mutableStateOf(saved?.bio ?: "") }

    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.connect_profile_title), style = MaterialTheme.typography.titleLarge)
            if (saved != null && !editing) TextButton(onClick = { editing = true }) { Text(stringResource(R.string.connect_profile_edit)) }
        }
        if (!editing && saved != null) {
            Text(stringResource(R.string.connect_rate_range, saved.rateMinInr, saved.rateMaxInr), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                saved.services.forEach { Chip(serviceLabel(it)) }
                saved.languages.forEach { Chip(it, MaterialTheme.colorScheme.surfaceVariant) }
            }
            if (saved.bio.isNotBlank()) Text(saved.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            if (saved == null) Text(stringResource(R.string.connect_profile_setup), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.connect_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.connect_services_label), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ServiceType.values().forEach { s ->
                    FilterChip(selected = s in services, onClick = { services = if (s in services) services - s else services + s }, label = { Text(serviceLabel(s)) })
                }
            }
            Text(stringResource(R.string.connect_languages_label), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Malayalam", "English", "Hindi", "Tamil").forEach { l ->
                    FilterChip(selected = l in languages, onClick = { languages = if (l in languages) languages - l else languages + l }, label = { Text(l) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rateMin, { rateMin = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.connect_rate_min)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(rateMax, { rateMax = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.connect_rate_max)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            OutlinedTextField(bio, { bio = it.take(280) }, label = { Text(stringResource(R.string.connect_bio_label)) }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { vm.saveProfile(name, languages, services.ifEmpty { listOf(ServiceType.GENERAL) }, rateMin.toIntOrNull() ?: 0, rateMax.toIntOrNull() ?: 0, bio) { editing = false } },
                enabled = !vm.busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.connect_profile_save)) }
        }
    }
}

@Composable
private fun VisibilityPanel(vm: ConnectViewModel) {
    val context = LocalContext.current
    var hours by remember { mutableStateOf(4) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setOpen(true, hours) else vm.onPermissionDenied()
    }
    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.connect_open_title), style = MaterialTheme.typography.titleLarge)
            Switch(
                checked = vm.isVisibleNow,
                enabled = !vm.busy && vm.profile != null,
                onCheckedChange = { on ->
                    if (!on) vm.setOpen(false, hours)
                    else if (com.koolikanaku.connect.location.CoarseLocation.hasPermission(context)) vm.setOpen(true, hours)
                    else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            )
        }
        Text(
            if (vm.isVisibleNow) stringResource(R.string.connect_visible_until, timeText(vm.visibleUntilMs!!)) else stringResource(R.string.connect_not_visible),
            style = MaterialTheme.typography.titleSmall, color = if (vm.isVisibleNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(stringResource(R.string.connect_open_for), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(2, 4, 8).forEach { h -> FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text(stringResource(R.string.connect_hours, h)) }) }
        }
        Text(stringResource(R.string.connect_open_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BookingList(vm: ConnectViewModel, onOpenBooking: (String) -> Unit) {
    val me = vm.user ?: return
    Panel {
        Text(stringResource(R.string.connect_my_bookings), style = MaterialTheme.typography.titleLarge)
        val others = vm.bookings.filter { it.status != BookingStatus.REQUESTED || me.role == UserRole.OWNER }
        if (others.isEmpty()) Text(stringResource(R.string.connect_no_bookings), color = MaterialTheme.colorScheme.onSurfaceVariant)
        others.forEach { b ->
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().clickable { onOpenBooking(b.id) }) {
                Row(modifier = Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (me.role == UserRole.OWNER) b.workerName else b.ownerName, style = MaterialTheme.typography.titleSmall)
                        Text(b.areaLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Chip(stringResource(statusRes(b.status)))
                }
            }
        }
    }
}

// ============================================================ owner

private fun androidx.compose.foundation.lazy.LazyListScope.ownerSections(vm: ConnectViewModel, onOpenBooking: (String) -> Unit) {
    item { OwnerSearchPanel(vm) }
    if (vm.ownerLocation != null) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = vm.serviceFilter == null, onClick = { vm.serviceFilter = null }, label = { Text(stringResource(R.string.connect_filter_all)) })
                ServiceType.values().filter { it != ServiceType.GENERAL }.forEach { s ->
                    FilterChip(selected = vm.serviceFilter == s, onClick = { vm.serviceFilter = s }, label = { Text(serviceLabel(s)) })
                }
            }
        }
        val cards = vm.cards
        if (cards.isEmpty()) item { Text(stringResource(R.string.connect_no_workers), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(cards.size) { index -> WorkerCardRow(vm, cards[index], onOpenBooking) }
    }
    item { BookingList(vm, onOpenBooking) }
}

@Composable
private fun OwnerSearchPanel(vm: ConnectViewModel) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.findLocation() else vm.onPermissionDenied()
    }
    Panel {
        Text(stringResource(R.string.connect_nearby_title), style = MaterialTheme.typography.titleLarge)
        when (vm.locationState) {
            LocationState.LOADING -> Text(stringResource(R.string.connect_finding), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LocationState.NO_PERMISSION -> Text(stringResource(R.string.connect_location_denied), color = MaterialTheme.colorScheme.error)
            LocationState.UNAVAILABLE -> Text(stringResource(R.string.connect_location_unavailable), color = MaterialTheme.colorScheme.error)
            else -> {}
        }
        Button(
            onClick = {
                if (com.koolikanaku.connect.location.CoarseLocation.hasPermission(context)) vm.findLocation()
                else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            enabled = vm.locationState != LocationState.LOADING, modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.connect_find_me)) }
        OutlinedButton(onClick = { vm.useFallbackLocation() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connect_use_sample_area)) }
    }
}

@Composable
private fun WorkerCardRow(vm: ConnectViewModel, card: WorkerCard, onOpenBooking: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Panel(onClick = { open = true }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(card.profile.name, style = MaterialTheme.typography.titleLarge)
                    if (card.profile.isDemo) Chip(stringResource(R.string.connect_demo_chip), MaterialTheme.colorScheme.tertiaryContainer)
                }
                Text(card.distanceBand, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StarsText(card)
        }
        Text(stringResource(R.string.connect_rate_range, card.profile.rateMinInr, card.profile.rateMaxInr), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            card.profile.services.forEach { Chip(serviceLabel(it)) }
        }
        if (card.stats.ratingCount > 0) Text(stringResource(R.string.connect_rating_line, card.stats.ratingCount, card.stats.verifiedCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (card.stats.completedCount > 0) Text(stringResource(R.string.connect_jobs_line, card.stats.completedCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) WorkerSheet(vm, card, onDismiss = { open = false }, onBooked = { open = false; onOpenBooking(it) })
}

@Composable
private fun WorkerSheet(vm: ConnectViewModel, card: WorkerCard, onDismiss: () -> Unit, onBooked: (String) -> Unit) {
    var area by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(card.profile.uid) { vm.loadRatings(card.profile.uid) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.connect_request_title, card.profile.name), style = MaterialTheme.typography.headlineSmall)
            if (card.profile.bio.isNotBlank()) Text(card.profile.bio, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { card.profile.languages.forEach { Chip(it, MaterialTheme.colorScheme.surfaceVariant) } }
            Text(stringResource(R.string.connect_ratings_title), style = MaterialTheme.typography.titleMedium)
            val list = vm.ratings[card.profile.uid].orEmpty()
            if (list.isEmpty()) Text(stringResource(R.string.connect_no_ratings), color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.take(5).forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("★".repeat(r.stars), color = MaterialTheme.colorScheme.primary)
                    if (r.verified) Chip(stringResource(R.string.connect_verified_chip), MaterialTheme.colorScheme.tertiaryContainer)
                    if (r.comment.isNotBlank()) Text(r.comment, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            OutlinedTextField(area, { area = it.take(80) }, label = { Text(stringResource(R.string.connect_area_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(size, { size = it.take(80) }, label = { Text(stringResource(R.string.connect_size_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.connect_address_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { vm.requestBooking(card.profile.uid, area, size) { onBooked(it.id) } }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connect_send_request))
            }
        }
    }
}

// ============================================================ one booking

@Composable
fun BookingScreen(
    vm: ConnectViewModel,
    bookingId: String,
    estimateStartedHere: Boolean,
    onBack: () -> Unit,
    onStartEstimate: (Booking) -> Unit,
    onContinueEstimate: () -> Unit
) {
    val context = LocalContext.current
    val me = vm.user ?: return
    val b = vm.booking(bookingId)
    Scaffold(topBar = { Header(stringResource(R.string.connect_booking_title), b?.let { stringResource(statusRes(it.status)) }, onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Banners(vm)
            if (b == null) { Text(stringResource(R.string.connect_no_bookings)); return@Column }
            val iAmOwner = me.role == UserRole.OWNER
            Panel {
                Text(stringResource(if (iAmOwner) R.string.connect_with_worker else R.string.connect_with_owner, if (iAmOwner) b.workerName else b.ownerName), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.connect_area_line, b.areaLabel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (b.sizeHint.isNotBlank()) Text(stringResource(R.string.connect_size_line, b.sizeHint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                val phone = if (iAmOwner) b.workerPhone else b.ownerPhone
                if (phone != null && b.status.revealsPhones && b.status != BookingStatus.RATED) {
                    Button(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (iAmOwner) R.string.connect_call_worker else R.string.connect_call_owner, if (iAmOwner) b.workerName else b.ownerName))
                    }
                    Text(stringResource(R.string.connect_call_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (b.status) {
                BookingStatus.REQUESTED -> {
                    if (iAmOwner) OutlinedButton(onClick = { vm.perform(b.id, Action.CANCEL) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connect_cancel_booking)) }
                    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.perform(b.id, Action.ACCEPT) }, enabled = !vm.busy) { Text(stringResource(R.string.connect_accept)) }
                        OutlinedButton(onClick = { vm.perform(b.id, Action.DECLINE) }, enabled = !vm.busy) { Text(stringResource(R.string.connect_decline)) }
                    }
                }
                BookingStatus.ACCEPTED -> {
                    Button(onClick = { vm.perform(b.id, Action.ARRIVE) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connect_arrived)) }
                    OutlinedButton(onClick = { vm.perform(b.id, Action.CANCEL) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connect_cancel_booking)) }
                }
                BookingStatus.ARRIVED -> Button(onClick = { onStartEstimate(b) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connect_start_estimate)) }
                BookingStatus.IN_PROGRESS -> {
                    if (estimateStartedHere) Button(onClick = onContinueEstimate, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connect_continue_estimate)) }
                    else Text(stringResource(R.string.connect_estimate_elsewhere), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.connect_finish_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { vm.perform(b.id, Action.COMPLETE) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connect_mark_complete)) }
                }
                BookingStatus.COMPLETED -> if (iAmOwner) RatingForm(vm, b) else Text(stringResource(R.string.connect_waiting_rating), color = MaterialTheme.colorScheme.onSurfaceVariant)
                BookingStatus.RATED -> Text(stringResource(R.string.connect_thanks_rated), color = MaterialTheme.colorScheme.primary)
                else -> {}
            }
        }
    }
}

@Composable
private fun RatingForm(vm: ConnectViewModel, b: Booking) {
    var stars by remember { mutableStateOf(5) }
    var tags by remember { mutableStateOf(setOf<String>()) }
    var comment by remember { mutableStateOf("") }
    val tagOptions = listOf("on_time" to R.string.connect_tag_on_time, "careful" to R.string.connect_tag_careful, "fair_price" to R.string.connect_tag_fair_price, "polite" to R.string.connect_tag_polite)
    Panel {
        Text(stringResource(R.string.connect_rate_title, b.workerName), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.connect_rate_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { i ->
                Text(if (i <= stars) "★" else "☆", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { stars = i })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tagOptions.forEach { (key, label) ->
                FilterChip(selected = key in tags, onClick = { tags = if (key in tags) tags - key else tags + key }, label = { Text(stringResource(label)) })
            }
        }
        OutlinedTextField(comment, { comment = it.take(280) }, label = { Text(stringResource(R.string.connect_comment_label)) }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.rate(b.id, stars, tags.toList(), comment) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connect_rate_submit)) }
    }
}
