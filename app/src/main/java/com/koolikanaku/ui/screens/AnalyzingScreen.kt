package com.koolikanaku.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koolikanaku.R
import com.koolikanaku.ui.components.OfflineBadge

/** One pipeline stage shown in the Analyzing checklist (plan.md Section 4). */
data class AnalysisStageUi(val label: String, val done: Boolean, val current: Boolean, val durationText: String)

/**
 * Analyzing screen: a progress checklist over the room's photos (detection, stain check,
 * merge) with an always-visible Offline badge, then a hand-off to Room Confirm once done.
 */
@Composable
fun AnalyzingScreen(
    stages: List<AnalysisStageUi>,
    isDone: Boolean,
    onReviewRoom: () -> Unit,
    errorText: String? = null,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 24.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.analyzing_title), style = MaterialTheme.typography.headlineMedium)
                OfflineBadge()
            }

            Text(
                stringResource(R.string.analyzing_comparing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                stages.forEach { stage ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            when {
                                stage.done -> Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                stage.current -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                else -> Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                                    color = androidx.compose.ui.graphics.Color.Transparent,
                                    modifier = Modifier.size(10.dp)
                                ) {}
                            }
                        }
                        Text(
                            stage.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (stage.current) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (!stage.done && !stage.current) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (stage.done) stage.durationText else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (stages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.analyzing_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (errorText != null) {
                Text(
                    stringResource(R.string.analyzing_problem, errorText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = onReviewRoom,
                enabled = isDone,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(
                    stringResource(if (isDone) R.string.analyzing_review_button else R.string.analyzing_working),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}
