package com.koolikanaku.connect.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.Rating
import com.koolikanaku.connect.model.WorkerCard
import com.koolikanaku.ui.components.DarkModeToggle

/** extra_feature.md Section 9: "Nearby workers (owner)" - sorted by trust/distance, with an empty state. */
@Composable
fun NearbyWorkersScreen(
    areaLabel: String?,
    workers: List<WorkerCard>,
    onWorkerClick: (String) -> Unit,
    onOpenEstimator: () -> Unit,
    onSignOut: () -> Unit,
    onViewHiredWorkers: () -> Unit = {},
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Find a worker", style = MaterialTheme.typography.headlineMedium)
                        Text("Open for work near ${areaLabel ?: "you"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onViewHiredWorkers) { Text("People I've hired") }
                        DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                    }
                }
            }
            if (workers.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("No one's open for work right now", style = MaterialTheme.typography.titleMedium)
                            Text("Check back soon, or ask a worker you know to turn on \"Open for work\".", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            itemsIndexed(workers) { index, w ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(360, delayMillis = index * 50, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(360, delayMillis = index * 50, easing = FastOutSlowInEasing)) { it / 6 }
                ) {
                    WorkerCardRow(worker = w, onClick = { onWorkerClick(w.uid) })
                }
            }
            item {
                OutlinedButton(onClick = onOpenEstimator, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Open offline estimator") }
            }
            item { TextButton(onClick = onSignOut) { Text("Sign out") } }
        }
    }
}

@Composable
private fun WorkerCardRow(worker: WorkerCard, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "workerCardScale")
    Surface(
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(worker.name, style = MaterialTheme.typography.titleLarge)
                Text(worker.distanceBandLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(16.dp))
                Text(
                    if (worker.ratingCount > 0) "${"%.1f".format(worker.ratingAvg)} · ${worker.ratingCount} ratings · ${worker.verifiedCount} verified" else "No ratings yet",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("${worker.languages.joinToString()} · ₹${worker.rateMinInr.toInt()}-₹${worker.rateMaxInr.toInt()}/hr", style = MaterialTheme.typography.bodyMedium)
            Text(worker.services.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** extra_feature.md Section 9: "Worker detail" - rating breakdown, verified-job count, recent comments. */
@Composable
fun WorkerDetailScreen(
    worker: WorkerCard?,
    recentRatings: List<Rating>,
    onRequestBooking: () -> Unit,
    onBack: () -> Unit,
    /** False when a worker is viewing their own card (extra_feature.md Section 9: "worker ratings") - requesting yourself makes no sense. */
    showRequestButton: Boolean = true,
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { padding ->
        if (worker == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("< Back") }
                    DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                }
                Text("This worker is no longer available.")
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = padding.calculateTopPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("< Back") }
                    DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(worker.name, style = MaterialTheme.typography.headlineMedium)
                    Text(worker.distanceBandLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            if (worker.ratingCount > 0) "${"%.1f".format(worker.ratingAvg)} · ${worker.ratingCount} ratings · ${worker.verifiedCount} with verified photos" else "No ratings yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("A verified rating comes from a completed, hashed job - not just a tap.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Languages: ${worker.languages.joinToString()}", style = MaterialTheme.typography.bodyMedium)
                    Text("Services: ${worker.services.joinToString()}", style = MaterialTheme.typography.bodyMedium)
                    Text("Rate: ₹${worker.rateMinInr.toInt()}-₹${worker.rateMaxInr.toInt()}/hr", style = MaterialTheme.typography.bodyMedium)
                    if (worker.bio.isNotBlank()) Text(worker.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (recentRatings.isNotEmpty()) {
                item { Text("Recent comments", style = MaterialTheme.typography.titleMedium) }
                itemsIndexed(recentRatings.take(5)) { index, r ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(320, delayMillis = index * 50, easing = FastOutSlowInEasing))
                    ) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${"★".repeat(r.stars)}${if (r.verified) "  verified" else ""}", style = MaterialTheme.typography.bodyMedium)
                                if (r.comment.isNotBlank()) Text(r.comment, style = MaterialTheme.typography.bodySmall)
                                if (r.tags.isNotEmpty()) Text(r.tags.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (showRequestButton) {
                item {
                    Button(onClick = onRequestBooking, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text("Request booking (now)", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/** One row of [HiredWorkersScreen]: this owner's own summary of one worker, not that worker's full public history. */
data class HiredWorkerSummary(
    val workerId: String,
    val workerName: String,
    val bookingCount: Int,
    val lastStatus: BookingStatus,
    val lastDateText: String
)

/**
 * extra_feature.md Section 9: "People I've hired" - the owner's OWN history with each worker
 * they've booked (never the worker's history with anyone else), one row per worker, most recent first.
 */
@Composable
fun HiredWorkersScreen(
    workers: List<HiredWorkerSummary>,
    onWorkerClick: (String) -> Unit,
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
                    Text("People I've hired", style = MaterialTheme.typography.headlineMedium)
                    Text("Your own booking history with each worker.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (workers.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "You haven't booked anyone yet.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(22.dp)
                        )
                    }
                }
            }
            itemsIndexed(workers) { index, w ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(320, delayMillis = index * 50, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(320, delayMillis = index * 50, easing = FastOutSlowInEasing)) { it / 6 }
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clickable { onWorkerClick(w.workerId) }
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(w.workerName, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    if (w.bookingCount == 1) "1 booking · last ${w.lastDateText}" else "${w.bookingCount} bookings · last ${w.lastDateText}",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(bookingStatusLabel(w.lastStatus), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * extra_feature.md Section 9: drill-down from [HiredWorkersScreen] - every booking this owner has
 * had with one specific worker (dates, status, price if the job finished with evidence attached).
 */
@Composable
fun WorkerHistoryScreen(
    workerName: String,
    bookings: List<Booking>,
    onOpenBooking: (String) -> Unit,
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
                    Text("Your history with $workerName", style = MaterialTheme.typography.headlineMedium)
                }
            }
            itemsIndexed(bookings) { index, b ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(320, delayMillis = index * 50, easing = FastOutSlowInEasing))
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenBooking(b.id) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(java.text.DateFormat.getDateInstance().format(java.util.Date(b.createdAtEpochMs)), style = MaterialTheme.typography.titleMedium)
                                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(bookingStatusLabel(b.status), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Text("${b.areaLabel} · ${b.sizeHint}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            b.evidence?.let { e ->
                                Text("₹${e.totalPriceLowInr.toInt()}-₹${e.totalPriceHighInr.toInt()} · ${e.roomCount} room(s)", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
