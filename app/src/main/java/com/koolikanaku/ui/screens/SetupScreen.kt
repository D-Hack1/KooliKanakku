package com.koolikanaku.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.House
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.koolikanaku.R
import com.koolikanaku.domain.AppMode
import com.koolikanaku.ui.AppLanguage
import com.koolikanaku.ui.components.DarkModeToggle
import com.koolikanaku.ui.components.LanguageToggle

private data class PaceOption(val factor: Double, val labelRes: Int)
private val PACE_OPTIONS = listOf(
    PaceOption(0.9, R.string.setup_pace_fast),
    PaceOption(1.0, R.string.setup_pace_standard),
    PaceOption(1.15, R.string.setup_pace_thorough)
)

/**
 * Setup screen: worker/owner mode, rate, pace, optional height, and app language.
 * Everything here feeds [com.koolikanaku.domain.Job] once Abhinav's startJob() runs it.
 */
@Composable
fun SetupScreen(
    mode: AppMode,
    onModeChange: (AppMode) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    rateText: String,
    onRateChange: (String) -> Unit,
    paceFactor: Double,
    onPaceChange: (Double) -> Unit,
    heightCm: String,
    onHeightChange: (String) -> Unit,
    onStartJob: () -> Unit,
    resumeRoomCount: Int = 0,
    onResumeJob: () -> Unit = {},
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isWorker = mode == AppMode.WORKER_ESTIMATE

    Scaffold(
        modifier = modifier,
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (resumeRoomCount > 0) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onResumeJob,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text(pluralStringResource(R.plurals.setup_resume_job, resumeRoomCount, resumeRoomCount), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Button(
                        onClick = onStartJob,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(stringResource(R.string.setup_next_button), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (darkTheme) Color(0xFF15110E) else Color.White,
                            border = BorderStroke(1.dp, if (darkTheme) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.app_logo),
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier.fillMaxSize().padding(8.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.Center) {
                            AnimatedContent(
                                targetState = language,
                                transitionSpec = {
                                    (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 })
                                        .togetherWith(fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 3 })
                                },
                                label = "setup_logo_title_transition"
                            ) { lang ->
                                Text(
                                    text = if (lang == AppLanguage.ML) "കൂലിക്കണക്ക്" else "KooliKanaku",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                        DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(R.string.setup_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.setup_who_question), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeCard(
                            icon = Icons.Outlined.CleaningServices,
                            title = stringResource(R.string.setup_worker_title),
                            subtitle = stringResource(R.string.setup_worker_subtitle),
                            selected = isWorker,
                            onClick = { onModeChange(AppMode.WORKER_ESTIMATE) },
                            modifier = Modifier.weight(1f)
                        )
                        ModeCard(
                            icon = Icons.Outlined.House,
                            title = stringResource(R.string.setup_owner_title),
                            subtitle = stringResource(R.string.setup_owner_subtitle),
                            selected = !isWorker,
                            onClick = { onModeChange(AppMode.OWNER_QUOTE) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(if (isWorker) R.string.setup_rate_worker_label else R.string.setup_rate_owner_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { onRateChange(it.filter { ch -> ch.isDigit() }) },
                        leadingIcon = { Text("₹", style = MaterialTheme.typography.headlineSmall) },
                        trailingIcon = { Text(stringResource(R.string.setup_per_hour), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(if (isWorker) R.string.setup_rate_worker_hint else R.string.setup_rate_owner_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.setup_height_label), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.setup_height_optional),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = heightCm,
                        onValueChange = { onHeightChange(it.filter { ch -> ch.isDigit() }) },
                        placeholder = { Text("165") },
                        trailingIcon = { Text(stringResource(R.string.setup_height_unit), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.setup_height_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isWorker) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.setup_owner_pace_note), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.setup_owner_time_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                if (selected) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(2.dp))
                    }
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PaceCard(
    label: String,
    multiplierText: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 1)
            Text(multiplierText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
