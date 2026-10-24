package com.koolikanaku.connect.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.ui.components.DarkModeToggle

private val TIMELINE = listOf(
    BookingStatus.REQUESTED, BookingStatus.ACCEPTED, BookingStatus.ARRIVED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED
)

/**
 * extra_feature.md Section 9: "Booking status" - timeline, Call button (after acceptance), Arrived,
 * Start the estimate. Either party can set ARRIVED (Section 4.4), which is why both buttons exist here.
 */
@Composable
fun BookingStatusScreen(
    booking: Booking?,
    meIsWorker: Boolean,
    onCall: () -> Unit,
    onArrived: () -> Unit,
    onStartEstimate: () -> Unit,
    onCancel: () -> Unit,
    onRate: () -> Unit,
    onBack: () -> Unit,
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { padding ->
        if (booking == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("< Back") }
                    DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                }
                Text("This booking is no longer available.")
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = padding.calculateTopPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("< Back") }
                    DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (meIsWorker) booking.ownerName else booking.workerName, style = MaterialTheme.typography.headlineMedium)
                    Text("${booking.areaLabel} · ${booking.sizeHint}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { StatusTimeline(current = booking.status) }

            if (booking.status == BookingStatus.DECLINED || booking.status == BookingStatus.CANCELLED || booking.status == BookingStatus.EXPIRED) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "This booking was ${booking.status.name.lowercase()}" + (booking.cancelReason?.let { ": $it" } ?: "."),
                            modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (booking.status == BookingStatus.ACCEPTED || booking.status == BookingStatus.ARRIVED || booking.status == BookingStatus.IN_PROGRESS) {
                item {
                    Button(onClick = onCall, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text("Call ${if (meIsWorker) "owner" else "worker"} - share the address by voice", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (booking.status == BookingStatus.ACCEPTED) {
                item {
                    OutlinedButton(onClick = onArrived, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("I've arrived / worker arrived") }
                }
            }
            if (booking.status == BookingStatus.ARRIVED || booking.status == BookingStatus.IN_PROGRESS) {
                item {
                    Button(onClick = onStartEstimate, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text(if (meIsWorker) "Start the estimate" else "Start the estimate (quotation)", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (booking.status == BookingStatus.COMPLETED && !meIsWorker) {
                item {
                    Button(onClick = onRate, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Rate this worker") }
                }
            }
            if (booking.status == BookingStatus.REQUESTED || booking.status == BookingStatus.ACCEPTED) {
                item { TextButton(onClick = onCancel) { Text("Cancel booking") } }
            }
        }
    }
}

@Composable
private fun StatusTimeline(current: BookingStatus) {
    val index = TIMELINE.indexOf(current).let { if (it < 0) 0 else it }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        TIMELINE.forEachIndexed { i, status ->
            val color by animateColorAsState(
                if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(360, delayMillis = i * 60, easing = FastOutSlowInEasing), label = "timelineSegment"
            )
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(50), color = color, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.height(10.dp).fillMaxWidth()) {}
                }
                Text(status.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** extra_feature.md Section 9: "Rate worker" - stars, quick tags, comment, verified note. */
@Composable
fun RateWorkerScreen(
    workerName: String,
    stars: Int,
    onStarsChange: (Int) -> Unit,
    availableTags: List<String>,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    willBeVerified: Boolean,
    onSubmit: () -> Unit,
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Rate $workerName", style = MaterialTheme.typography.headlineMedium)
                DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
            }
            Row {
                (1..5).forEach { i ->
                    val scale by animateFloatAsState(if (i <= stars) 1.15f else 1f, animationSpec = spring(dampingRatio = 0.45f), label = "starScale")
                    IconButton(onClick = { onStarsChange(i) }) {
                        Icon(
                            if (i <= stars) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.scale(scale)
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableTags.forEach { tag ->
                    FilterChip(selected = tag in selectedTags, onClick = { onToggleTag(tag) }, label = { Text(tag) })
                }
            }
            OutlinedTextField(
                value = comment, onValueChange = onCommentChange, label = { Text("Comment (optional)") },
                modifier = Modifier.fillMaxWidth().height(110.dp)
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (willBeVerified) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (willBeVerified) "This rating will be marked verified - it's backed by a hashed, completed job."
                    else "This rating will not be verified: no evidence summary was attached to this booking.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp)
                )
            }
            Button(onClick = onSubmit, enabled = stars > 0, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text("Submit rating", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
