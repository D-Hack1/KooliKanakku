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
import androidx.compose.material3.CircularProgressIndicator
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
import com.koolikanaku.ui.AppLanguage
import com.koolikanaku.ui.components.DarkModeToggle
import com.koolikanaku.ui.components.LanguageToggle
import com.koolikanaku.ui.t

private val TIMELINE = listOf(
    BookingStatus.REQUESTED, BookingStatus.ACCEPTED, BookingStatus.ARRIVED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED
)

private fun translateTag(tag: String, language: AppLanguage): String {
    if (language != AppLanguage.ML) return tag
    return when (tag.trim().lowercase()) {
        "punctual" -> "കൃത്യനിഷ്ഠ"
        "fast" -> "വേഗതയുള്ളത്"
        "thorough" -> "സൂക്ഷ്മതയുള്ളത്"
        "polite" -> "നല്ല പെരുമാറ്റം"
        "hardworking" -> "കഠിനാധ്വാനം"
        else -> tag
    }
}

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
    /** True while an action (accept/decline/cancel/arrive/start/complete) is in flight, so buttons
     * show a spinner and disable instead of looking frozen while the request is on the wire. */
    busy: Boolean = false,
    language: AppLanguage = AppLanguage.EN,
    onLanguageChange: (AppLanguage) -> Unit = {},
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { padding ->
        if (booking == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text(t(language, "< Back", "< തിരികെ")) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                        DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                    }
                }
                Text(t(language, "This booking is no longer available.", "ഈ ബുക്കിംഗ് ഇപ്പോൾ ലഭ്യമല്ല."))
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = padding.calculateTopPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
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
                    Text(if (meIsWorker) booking.ownerName else booking.workerName, style = MaterialTheme.typography.headlineMedium)
                    Text("${booking.areaLabel} · ${booking.sizeHint}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { StatusTimeline(current = booking.status, language = language) }

            if (booking.status == BookingStatus.DECLINED || booking.status == BookingStatus.CANCELLED || booking.status == BookingStatus.EXPIRED) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            t(
                                language,
                                "This booking was ${booking.status.name.lowercase()}" + (booking.cancelReason?.let { ": $it" } ?: "."),
                                "ഈ ബുക്കിംഗ് ${bookingStatusLabel(booking.status, language)} ആയിരുന്നു" + (booking.cancelReason?.let { ": $it" } ?: ".")
                            ),
                            modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (booking.status == BookingStatus.ACCEPTED || booking.status == BookingStatus.ARRIVED || booking.status == BookingStatus.IN_PROGRESS) {
                item {
                    Button(onClick = onCall, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text(
                            t(
                                language,
                                "Call ${if (meIsWorker) "owner" else "worker"} - share the address by voice",
                                "${if (meIsWorker) "ഉടമയെ" else "തൊഴിലാളിയെ"} വിളിക്കുക - ഫോണിലൂടെ വിലാസം നൽകാം"
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            if (booking.status == BookingStatus.ACCEPTED) {
                item {
                    OutlinedButton(onClick = onArrived, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                        else Text(t(language, "I've arrived / worker arrived", "ഞാൻ എത്തി / തൊഴിലാളി എത്തിച്ചേർന്നു"))
                    }
                }
            }
            if (booking.status == BookingStatus.ARRIVED || booking.status == BookingStatus.IN_PROGRESS) {
                item {
                    Button(onClick = onStartEstimate, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(
                            if (meIsWorker) t(language, "Start the estimate", "എസ്റ്റിമേറ്റ് തുടങ്ങുക")
                            else t(language, "Start the estimate (quotation)", "ക്വട്ടേഷൻ ആരംഭിക്കുക"),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            if (booking.status == BookingStatus.COMPLETED && !meIsWorker) {
                item {
                    Button(onClick = onRate, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text(t(language, "Rate this worker", "തൊഴിലാളിക്ക് റേറ്റിംഗ് നൽകുക"))
                    }
                }
            }
            if (booking.status == BookingStatus.REQUESTED || booking.status == BookingStatus.ACCEPTED) {
                item {
                    TextButton(onClick = onCancel, enabled = !busy) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                        else Text(t(language, "Cancel booking", "ബുക്കിംഗ് റദ്ദാക്കുക"))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTimeline(current: BookingStatus, language: AppLanguage = AppLanguage.EN) {
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
                Text(
                    bookingStatusLabel(status, language),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
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
    busy: Boolean = false,
    language: AppLanguage = AppLanguage.EN,
    onLanguageChange: (AppLanguage) -> Unit = {},
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t(language, "Rate $workerName", "$workerName-ന് റേറ്റിംഗ് നൽകുക"),
                    style = MaterialTheme.typography.headlineMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                    DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                }
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
                    FilterChip(
                        selected = tag in selectedTags,
                        onClick = { onToggleTag(tag) },
                        label = { Text(translateTag(tag, language)) }
                    )
                }
            }
            OutlinedTextField(
                value = comment,
                onValueChange = onCommentChange,
                label = { Text(t(language, "Comment (optional)", "അഭിപ്രായം (ഓപ്ഷണൽ)")) },
                modifier = Modifier.fillMaxWidth().height(110.dp)
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (willBeVerified) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (willBeVerified) {
                        t(
                            language,
                            "This rating will be marked verified - it's backed by a hashed, completed job.",
                            "ഈ റേറ്റിംഗ് സ്ഥിരീകരിച്ചതായി അടയാളപ്പെടുത്തും - പൂർത്തിയായ ജോലിയുടെ തെളിവ് ഇതിലുണ്ട്."
                        )
                    } else {
                        t(
                            language,
                            "This rating will not be verified: no evidence summary was attached to this booking.",
                            "ഈ റേറ്റിംഗ് സ്ഥിരീകരിക്കപ്പെടില്ല: ഈ ബുക്കിംഗിന് തെളിവുകൾ ലഭ്യമല്ല."
                        )
                    },
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp)
                )
            }
            Button(onClick = onSubmit, enabled = stars > 0 && !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                if (busy) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(t(language, "Submit rating", "റേറ്റിംഗ് സമർപ്പിക്കുക"), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
