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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.ui.AppLanguage
import com.koolikanaku.ui.components.DarkModeToggle
import com.koolikanaku.ui.components.LanguageToggle
import com.koolikanaku.ui.t

private val POPULAR_LANGUAGES = listOf(
    "Malayalam" to "മലയാളം",
    "English" to "English",
    "Tamil" to "தமிழ்",
    "Hindi" to "हिन्दी",
    "Kannada" to "ಕನ್ನಡ",
    "Bengali" to "বাংলা"
)

private fun translateService(service: String, language: AppLanguage): String {
    if (language != AppLanguage.ML) return service
    return when (service.trim().lowercase()) {
        "sweeping" -> "അടിക്കൽ"
        "mopping" -> "തുടയ്ക്കൽ"
        "kitchen cleaning" -> "അടുക്കള വൃത്തിയാക്കൽ"
        "bathroom cleaning" -> "കുളിമുറി വൃത്തിയാക്കൽ"
        "deep clean" -> "ഡീപ് ക്ലീനിംഗ്"
        "dusting" -> "പൊടി തുടയ്ക്കൽ"
        else -> service
    }
}

/** The "Active booking" card (both Worker and Owner home) only ever shows a booking while it's in
 * one of these non-terminal states - collapsed to one "in progress" badge the whole span, instead
 * of cycling through requested/accepted/arrived/in_progress wording, so it reads as one continuous
 * state right from the moment the owner hires someone up until "Finish the booking" completes it. */
internal fun activeBookingStatusLabel(status: BookingStatus, language: AppLanguage): String =
    if (status in setOf(BookingStatus.REQUESTED, BookingStatus.ACCEPTED, BookingStatus.ARRIVED, BookingStatus.IN_PROGRESS)) {
        t(language, "in progress", "പുരോഗതിയിൽ")
    } else {
        bookingStatusLabel(status, language)
    }

internal fun bookingStatusLabel(status: BookingStatus, language: AppLanguage = AppLanguage.EN): String {
    if (language != AppLanguage.ML) return status.name.lowercase().replace('_', ' ')
    return when (status) {
        BookingStatus.REQUESTED -> "അഭ്യർത്ഥിച്ചു"
        BookingStatus.ACCEPTED -> "സ്വീകരിച്ചു"
        BookingStatus.ARRIVED -> "എത്തിച്ചേർന്നു"
        BookingStatus.IN_PROGRESS -> "പുരോഗതിയിൽ"
        BookingStatus.COMPLETED -> "പൂർത്തിയായി"
        BookingStatus.RATED -> "റേറ്റിംഗ് നൽകി"
        BookingStatus.DECLINED -> "നിരസിച്ചു"
        BookingStatus.CANCELLED -> "റദ്ദാക്കി"
        BookingStatus.EXPIRED -> "കാലഹരണപ്പെട്ടു"
        else -> status.name.lowercase().replace('_', ' ')
    }
}

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
    language: AppLanguage = AppLanguage.EN,
    onLanguageChange: (AppLanguage) -> Unit = {},
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentSelectedLangs = remember(languagesText) {
        languagesText.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun toggleLanguage(langName: String) {
        val exists = currentSelectedLangs.any { it.equals(langName, ignoreCase = true) }
        val updated = if (exists) {
            currentSelectedLangs.filterNot { it.equals(langName, ignoreCase = true) }
        } else {
            currentSelectedLangs + langName
        }
        onLanguagesChange(updated.joinToString(", "))
    }

    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = padding.calculateTopPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            t(language, "Your worker profile", "നിങ്ങളുടെ തൊഴിലാളി പ്രൊഫൈൽ"),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                            DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                        }
                    }
                    Text(
                        t(language, "Owners see this when they browse nearby workers.", "സമീപത്തുള്ള ഉടമകൾ തൊഴിലാളികളെ തിരയുമ്പോൾ ഇത് കാണാം."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        t(language, "Select languages known", "അറിയാവുന്ന ഭാഷകൾ തിരഞ്ഞെടുക്കുക"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        POPULAR_LANGUAGES.forEach { (langEn, langNative) ->
                            val isSelected = currentSelectedLangs.any { it.equals(langEn, ignoreCase = true) }
                            FilterChip(
                                selected = isSelected,
                                onClick = { toggleLanguage(langEn) },
                                label = { Text(if (language == AppLanguage.ML) langNative else langEn) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = languagesText,
                        onValueChange = onLanguagesChange,
                        label = { Text(t(language, "Languages (comma separated)", "ഭാഷകൾ (കോമ നൽകി നൽകുക)")) },
                        placeholder = { Text(t(language, "Malayalam, English, Tamil", "മലയാളം, ഇംഗ്ലീഷ്, തമിഴ്")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        t(language, "Services offered", "നൽകുന്ന സേവനങ്ങൾ"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableServices.forEach { service ->
                            FilterChip(
                                selected = service in selectedServices,
                                onClick = { onToggleService(service) },
                                label = { Text(translateService(service, language)) }
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = rateMin,
                        onValueChange = onRateMinChange,
                        label = { Text(t(language, "Rate from (₹/hr)", "കുറഞ്ഞ നിരക്ക് (₹/മണി)")) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rateMax,
                        onValueChange = onRateMaxChange,
                        label = { Text(t(language, "Rate to (₹/hr)", "കൂടിയ നിരക്ക് (₹/മണി)")) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = bio,
                    onValueChange = onBioChange,
                    label = { Text(t(language, "Short bio (optional)", "ചെറിയ വിവരണം (ഓപ്ഷണൽ)")) },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            }

            item {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(
                        t(language, "Save profile", "പ്രൊഫൈൽ സേവ് ചെയ്യുക"),
                        style = MaterialTheme.typography.titleMedium
                    )
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
    busy: Boolean = false,
    language: AppLanguage = AppLanguage.EN,
    onLanguageChange: (AppLanguage) -> Unit = {},
    onViewMyRatings: () -> Unit = {},
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
<<<<<<< HEAD
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Title and the two always-compact toggles share a row - these never grow with
                    // text length in any language, so this row can never overflow.
=======
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
>>>>>>> c2c201e7cb09a2d8339c57167c4dc9110b899f6b
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
<<<<<<< HEAD
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                t(language, "Hi, $workerName", "നമസ്കാരം, $workerName"),
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                t(language, "Worker home", "തൊഴിലാളി ഹോം"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
=======
                        Text(
                            t(language, "Hi, $workerName", "നമസ്കാരം, $workerName"),
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
>>>>>>> c2c201e7cb09a2d8339c57167c4dc9110b899f6b
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                            DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                        }
                    }
<<<<<<< HEAD
                    // Text links get their own row, full width to themselves - Malayalam labels
                    // ("പ്രൊഫൈൽ മാറ്റുക") run noticeably longer than their English counterparts and
                    // used to squeeze/clip the title above when they shared a row with everything else.
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onViewMyRatings) {
                            Text(t(language, "My ratings", "റേറ്റിംഗുകൾ"))
                        }
                        TextButton(onClick = onEditProfile) {
                            Text(t(language, "Edit profile", "പ്രൊഫൈൽ മാറ്റുക"))
                        }
=======
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            t(language, "Worker home", "തൊഴിലാളി ഹോം"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onViewMyRatings) {
                                Text(t(language, "My ratings", "റേറ്റിംഗുകൾ"))
                            }
                            TextButton(onClick = onEditProfile) {
                                Text(t(language, "Edit profile", "പ്രൊഫൈൽ മാറ്റുക"))
                            }
                        }
>>>>>>> c2c201e7cb09a2d8339c57167c4dc9110b899f6b
                    }
                }
            }

            item {
                val bannerColor by animateColorAsState(
                    if (openForWork) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    label = "bannerColor"
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
                            Text(
                                if (openForWork) t(language, "Open for work", "ജോലിക്ക് തയ്യാറാണ്")
                                else t(language, "Not visible to owners", "ഉടമകൾക്ക് കാണാനാവില്ല"),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (openForWork) {
                                    t(
                                        language,
                                        "You are visible to owners near ${areaLabel ?: "you"}. Turns off automatically after 4 hours.",
                                        "${areaLabel ?: "നിങ്ങളുടെ"} സമീപത്തുള്ള ഉടമകൾക്ക് നിങ്ങളെ കാണാം. 4 മണിക്കൂറിന് ശേഷം തനിയെ ഓഫാകും."
                                    )
                                } else {
                                    t(
                                        language,
                                        "Turn this on so owners nearby can find and book you.",
                                        "സമീപത്തുള്ള ഉടമകൾക്ക് നിങ്ങളെ കണ്ടെത്താനും വിളിക്കാനും ഇത് ഓണാക്കുക."
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = openForWork, onCheckedChange = { onToggleOpen() })
                    }
                }
            }

            if (active != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            t(language, "Active booking", "നിലവിലുള്ള ബുക്കിംഗ്"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        BookingCard(
                            booking = active,
                            meIsWorker = true,
                            language = language,
                            onClick = { onOpenBooking(active.id) }
                        )
                    }
                }
            }

            if (incoming.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            t(language, "Incoming requests", "വന്ന അഭ്യർത്ഥനകൾ"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (incoming.size > 2) {
                            TextButton(onClick = onSeeAllRequests) {
                                Text(t(language, "See all (${incoming.size})", "എല്ലാം (${incoming.size})"))
                            }
                        }
                    }
                }
                itemsIndexed(incoming.take(2)) { index, b ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(220, delayMillis = index * 30, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(220, delayMillis = index * 30, easing = FastOutSlowInEasing)) { it / 5 }
                    ) {
                        RequestCard(
                            booking = b,
                            language = language,
                            busy = busy,
                            onAccept = { onAccept(b.id) },
                            onDecline = { onDecline(b.id) }
                        )
                    }
                }
            } else {
                item {
                    Text(
                        t(
                            language,
                            "No requests yet. They'll show up here as soon as an owner books you.",
                            "ഇതുവരെ പുതിയ അഭ്യർത്ഥനകളില്ല. ഉടമകൾ ബുക്ക് ചെയ്യുമ്പോൾ ഇവിടെ കാണാം."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = onOpenEstimator,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
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

/** extra_feature.md Section 9: "Request inbox" - the full list, reachable from Worker home. */
@Composable
fun RequestInboxScreen(
    requests: List<Booking>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onBack: () -> Unit,
    busy: Boolean = false,
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onBack) {
                            Text(t(language, "< Back", "< തിരികെ"))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                            DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                        }
                    }
                    Text(
                        t(language, "Request inbox", "അഭ്യർത്ഥനകൾ"),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
            if (requests.isEmpty()) {
                item {
                    Text(
                        t(language, "No pending requests.", "തീർപ്പാക്കാത്ത അഭ്യർത്ഥനകളില്ല."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(requests) { index, b ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(200, delayMillis = index * 25, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(200, delayMillis = index * 25, easing = FastOutSlowInEasing)) { it / 5 }
                ) {
                    RequestCard(
                        booking = b,
                        language = language,
                        busy = busy,
                        onAccept = { onAccept(b.id) },
                        onDecline = { onDecline(b.id) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun RequestCard(
    booking: Booking,
    language: AppLanguage = AppLanguage.EN,
    busy: Boolean = false,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(booking.ownerName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${booking.areaLabel} · ${booking.sizeHint} · ${booking.requestedForIso}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onAccept, enabled = !busy, modifier = Modifier.weight(1f).height(44.dp)) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text(t(language, "Accept", "സ്വീകരിക്കുക"))
                }
                OutlinedButton(onClick = onDecline, enabled = !busy, modifier = Modifier.weight(1f).height(44.dp)) {
                    Text(t(language, "Decline", "നിരസിക്കുക"))
                }
            }
        }
    }
}

@Composable
internal fun BookingCard(
    booking: Booking,
    meIsWorker: Boolean,
    language: AppLanguage = AppLanguage.EN,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.98f else 1f,
        label = "bookingCardScale"
    )
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
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (meIsWorker) booking.ownerName else booking.workerName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    booking.areaLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    activeBookingStatusLabel(booking.status, language),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
