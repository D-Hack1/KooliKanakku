package com.koolikanaku.connect.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.ui.components.DarkModeToggle

/** extra_feature.md Section 9: "Worker profile" - name is already known from sign-up. */
@Composable
fun WorkerProfileScreen(
    languagesText: String,
    onLanguagesChange: (String) -> Unit,
    availableServices: List<String>,
    selectedServices: Set<String>,
    onToggleService: (String) -> Unit,
    rateMin: String,
    onRateMinChange: (String) -> Unit,
    rateMax: String,
    onRateMaxChange: (String) -> Unit,
    bio: String,
    onBioChange: (String) -> Unit,
    onSave: () -> Unit,
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = padding.calculateTopPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Your worker profile", style = MaterialTheme.typography.headlineMedium)
                        Text("Owners see this when they browse nearby workers.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                }
            }
            item {
                OutlinedTextField(
                    value = languagesText, onValueChange = onLanguagesChange,
                    label = { Text("Languages (comma separated)") }, placeholder = { Text("Malayalam, English") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Services offered", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableServices.take(3).forEach { service ->
                            FilterChip(selected = service in selectedServices, onClick = { onToggleService(service) }, label = { Text(service) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableServices.drop(3).forEach { service ->
                            FilterChip(selected = service in selectedServices, onClick = { onToggleService(service) }, label = { Text(service) })
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = rateMin, onValueChange = onRateMinChange, label = { Text("Rate from (₹/hr)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rateMax, onValueChange = onRateMaxChange, label = { Text("Rate to (₹/hr)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = bio, onValueChange = onBioChange, label = { Text("Short bio (optional)") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            }
            item {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("Save profile", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** extra_feature.md Section 9: "Worker home" - the big toggle, visibility banner, and incoming requests. */
@Composable
fun WorkerHomeScreen(
    workerName: String,
    openForWork: Boolean,
    onToggleOpen: () -> Unit,
    areaLabel: String?,
    incoming: List<Booking>,
    active: Booking?,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onOpenBooking: (String) -> Unit,
    onEditProfile: () -> Unit,
    onOpenEstimator: () -> Unit,
    onSignOut: () -> Unit,
    onSeeAllRequests: () -> Unit = {},
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = padding.calculateTopPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Hi, $workerName", style = MaterialTheme.typography.headlineMedium)
                        Text("Worker home", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onEditProfile) { Text("Edit profile") }
                        DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                    }
                }
            }
            item {
                val bannerColor by animateColorAsState(
                    if (openForWork) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    animationSpec = tween(320, easing = FastOutSlowInEasing), label = "bannerColor"
                )
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = bannerColor,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = if (openForWork) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(if (openForWork) "Open for work" else "Not visible to owners", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (openForWork) "You are visible to owners near ${areaLabel ?: "you"}. Turns off automatically after 4 hours."
                                else "Turn this on so owners nearby can find and book you.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = openForWork, onCheckedChange = { onToggleOpen() })
                    }
                }
            }
            if (active != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Active booking", style = MaterialTheme.typography.titleMedium)
                        BookingCard(booking = active, meIsWorker = true, onClick = { onOpenBooking(active.id) })
                    }
                }
            }
            if (incoming.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Incoming requests", style = MaterialTheme.typography.titleMedium)
                        if (incoming.size > 2) TextButton(onClick = onSeeAllRequests) { Text("See all (${incoming.size})") }
                    }
                }
                itemsIndexed(incoming.take(2)) { index, b ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(360, delayMillis = index * 60, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(360, delayMillis = index * 60, easing = FastOutSlowInEasing)) { it / 5 }
                    ) {
                        RequestCard(booking = b, onAccept = { onAccept(b.id) }, onDecline = { onDecline(b.id) })
                    }
                }
            } else {
                item {
                    Text(
                        "No requests yet. They'll show up here as soon as an owner books you.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                OutlinedButton(onClick = onOpenEstimator, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Open offline estimator") }
            }
            item {
                TextButton(onClick = onSignOut) { Text("Sign out") }
            }
        }
    }
}

/** extra_feature.md Section 9: "Request inbox" - the full list, reachable from Worker home. */
@Composable
fun RequestInboxScreen(
    requests: List<Booking>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onBack: () -> Unit,
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = padding.calculateTopPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) { Text("< Back") }
                        DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                    }
                    Text("Request inbox", style = MaterialTheme.typography.headlineMedium)
                }
            }
            if (requests.isEmpty()) {
                item { Text("No pending requests.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            itemsIndexed(requests) { index, b ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(340, delayMillis = index * 50, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(340, delayMillis = index * 50, easing = FastOutSlowInEasing)) { it / 5 }
                ) {
                    RequestCard(booking = b, onAccept = { onAccept(b.id) }, onDecline = { onDecline(b.id) })
                }
            }
        }
    }
}

@Composable
internal fun RequestCard(booking: Booking, onAccept: () -> Unit, onDecline: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(booking.ownerName, style = MaterialTheme.typography.titleMedium)
            Text("${booking.areaLabel} · ${booking.sizeHint} · ${booking.requestedForIso}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onAccept, modifier = Modifier.weight(1f).height(44.dp)) { Text("Accept") }
                OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f).height(44.dp)) { Text("Decline") }
            }
        }
    }
}

@Composable
internal fun BookingCard(booking: Booking, meIsWorker: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.98f else 1f, label = "bookingCardScale")
    Surface(
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (meIsWorker) booking.ownerName else booking.workerName, style = MaterialTheme.typography.titleMedium)
                Text(booking.areaLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.wrapContentWidth()) {
                Text(booking.status.name.lowercase().replace('_', ' '), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

internal fun bookingStatusLabel(status: BookingStatus): String = status.name.lowercase().replace('_', ' ')
