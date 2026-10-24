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
import com.koolikanaku.ui.AppLanguage
import com.koolikanaku.ui.components.DarkModeToggle
import com.koolikanaku.ui.components.LanguageToggle
import com.koolikanaku.ui.t

/** extra_feature.md Section 9: "Nearby workers (owner)" - sorted by trust/distance, with an empty state. */
@Composable
fun NearbyWorkersScreen(
    areaLabel: String?,
    workers: List<WorkerCard>,
    onWorkerClick: (String) -> Unit,
    onOpenEstimator: () -> Unit,
    onSignOut: () -> Unit,
    /** Mirrors Worker Home's own "Active booking" card - an in-progress job needs to be just as
     * reachable from the owner's side as the worker's, not only findable via booking history. */
    active: Booking? = null,
    onOpenBooking: (String) -> Unit = {},
    language: AppLanguage = AppLanguage.EN,
    onLanguageChange: (AppLanguage) -> Unit = {},
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Title and the two always-compact toggles share a row - these never grow with
                    // text length in any language, so this row can never overflow.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                t(language, "Find a worker", "തൊഴിലാളിയെ കണ്ടെത്തുക"),
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                t(
                                    language,
                                    "Open for work near ${areaLabel ?: "you"}",
                                    "${areaLabel ?: "നിങ്ങളുടെ"} സമീപം ലഭ്യമായവർ"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                            DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                        }
                    }
                    // Its own row, full width to itself - same reasoning as Worker Home's header.
                    TextButton(onClick = onViewHiredWorkers) {
                        Text(t(language, "People I've hired", "ഞാൻ ജോലിക്കെടുത്തവർ"))
                    }
                }
            }
            if (active != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(t(language, "Active booking", "നിലവിലുള്ള ബുക്കിംഗ്"), style = MaterialTheme.typography.titleMedium)
                        BookingCard(booking = active, meIsWorker = false, language = language, onClick = { onOpenBooking(active.id) })
                    }
                }
            }
            if (workers.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                t(language, "No one's open for work right now", "ഇപ്പോൾ ആരും ലഭ്യമല്ല"),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                t(
                                    language,
                                    "Check back soon, or ask a worker you know to turn on \"Open for work\".",
                                    "കുറച്ച് കഴിഞ്ഞ് വീണ്ടും നോക്കൂ, അല്ലെങ്കിൽ പരിചയമുള്ള തൊഴിലാളിയോട് ആപ്പിൽ ലഭ്യമാവാൻ പറയൂ."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            itemsIndexed(workers) { index, w ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(220, delayMillis = index * 25, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(220, delayMillis = index * 25, easing = FastOutSlowInEasing)) { it / 6 }
                ) {
                    WorkerCardRow(worker = w, language = language, onClick = { onWorkerClick(w.uid) })
                }
            }
            item {
                OutlinedButton(onClick = onOpenEstimator, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text(t(language, "Open offline estimator", "ഓഫ്‌ലൈൻ എസ്റ്റിമേറ്റർ തുറക്കുക"))
                }
            }
            item {
                TextButton(onClick = onSignOut) {
                    Text(t(language, "Sign out", "പുറത്തുകടക്കുക"))
                }
            }
        }
    }
}

@Composable
private fun WorkerCardRow(
    worker: WorkerCard,
    language: AppLanguage = AppLanguage.EN,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "workerCardScale")
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(worker.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    worker.distanceBandLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(16.dp))
                Text(
                    if (worker.ratingCount > 0) {
                        "${"%.1f".format(worker.ratingAvg)} · ${worker.ratingCount} ${t(language, "ratings", "റേറ്റിംഗുകൾ")} · ${worker.verifiedCount} ${t(language, "verified", "സ്ഥിരീകരിച്ചത്")}"
                    } else {
                        t(language, "No ratings yet", "റേറ്റിംഗുകൾ ഇല്ല")
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "${worker.languages.joinToString()} · ₹${worker.rateMinInr.toInt()}-₹${worker.rateMaxInr.toInt()}/${t(language, "hr", "മണി")}",
                style = MaterialTheme.typography.bodyMedium
            )
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
    language: AppLanguage = AppLanguage.EN,
    onLanguageChange: (AppLanguage) -> Unit = {},
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
                    TextButton(onClick = onBack) { Text(t(language, "< Back", "< തിരികെ")) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                        DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                    }
                }
                Text(t(language, "This worker is no longer available.", "ഈ തൊഴിലാളി ഇപ്പോൾ ലഭ്യമല്ല."))
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = padding.calculateTopPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text(t(language, "< Back", "< തിരികെ")) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                        DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                    }
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
                            if (worker.ratingCount > 0) {
                                "${"%.1f".format(worker.ratingAvg)} · ${worker.ratingCount} ${t(language, "ratings", "റേറ്റിംഗുകൾ")} · ${worker.verifiedCount} ${t(language, "with verified photos", "സ്ഥിരീകരിച്ച ഫോട്ടോകളോടെ")}"
                            } else {
                                t(language, "No ratings yet", "റേറ്റിംഗുകൾ ഇല്ല")
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            t(
                                language,
                                "A verified rating comes from a completed, hashed job - not just a tap.",
                                "പൂർത്തിയായ ജോലിയുടെ തെളിവ് ഫോട്ടോകളുള്ള വിശ്വസനീയ റേറ്റിംഗ് ആണിത്."
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${t(language, "Languages", "ഭാഷകൾ")}: ${worker.languages.joinToString()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${t(language, "Services", "സേവനങ്ങൾ")}: ${worker.services.joinToString()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${t(language, "Rate", "നിരക്ക്")}: ₹${worker.rateMinInr.toInt()}-₹${worker.rateMaxInr.toInt()}/${t(language, "hr", "മണി")}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (worker.bio.isNotBlank()) {
                        Text(worker.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (recentRatings.isNotEmpty()) {
                item {
                    Text(
                        t(language, "Recent comments", "സമീപകാല അഭിപ്രായങ്ങൾ"),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                itemsIndexed(recentRatings.take(5)) { index, r ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(200, delayMillis = index * 25, easing = FastOutSlowInEasing))
                    ) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    "${"★".repeat(r.stars)}${if (r.verified) "  " + t(language, "verified", "സ്ഥിരീകരിച്ചത്") else ""}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
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
                        Text(
                        t(language, "Request booking (now)", "ഇപ്പോൾ ബുക്ക് ചെയ്യുക"),
                        style = MaterialTheme.typography.titleMedium
                    )
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
    language: AppLanguage = AppLanguage.EN,
    onLanguageChange: (AppLanguage) -> Unit = {},
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
                        TextButton(onClick = onBack) { Text(t(language, "< Back", "< തിരികെ")) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                            DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                        }
                    }
                    Text(t(language, "People I've hired", "ഞാൻ ജോലിക്കെടുത്തവർ"), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        t(language, "Your own booking history with each worker.", "ഓരോ തൊഴിലാളിയുമായുള്ള നിങ്ങളുടെ സ്വന്തം ബുക്കിംഗ് ചരിത്രം."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (workers.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            t(language, "You haven't booked anyone yet.", "നിങ്ങൾ ഇതുവരെ ആരെയും ബുക്ക് ചെയ്തിട്ടില്ല."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(22.dp)
                        )
                    }
                }
            }
            itemsIndexed(workers) { index, w ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(200, delayMillis = index * 25, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(200, delayMillis = index * 25, easing = FastOutSlowInEasing)) { it / 6 }
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
                                    if (w.bookingCount == 1) t(language, "1 booking · last ${w.lastDateText}", "1 ബുക്കിംഗ് · അവസാനം ${w.lastDateText}")
                                    else t(language, "${w.bookingCount} bookings · last ${w.lastDateText}", "${w.bookingCount} ബുക്കിംഗുകൾ · അവസാനം ${w.lastDateText}"),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(activeBookingStatusLabel(w.lastStatus, language), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
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
    language: AppLanguage = AppLanguage.EN,
    onLanguageChange: (AppLanguage) -> Unit = {},
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
                        TextButton(onClick = onBack) { Text(t(language, "< Back", "< തിരികെ")) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                            DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                        }
                    }
                    Text(t(language, "Your history with $workerName", "$workerName-മായുള്ള നിങ്ങളുടെ ചരിത്രം"), style = MaterialTheme.typography.headlineMedium)
                }
            }
            itemsIndexed(bookings) { index, b ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(200, delayMillis = index * 25, easing = FastOutSlowInEasing))
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
                                    Text(activeBookingStatusLabel(b.status, language), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Text("${b.areaLabel} · ${b.sizeHint}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            b.evidence?.let { e ->
                                Text("₹${e.totalPriceLowInr.toInt()}-₹${e.totalPriceHighInr.toInt()} · ${e.roomCount} ${t(language, "room(s)", "മുറി(കൾ)")}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
