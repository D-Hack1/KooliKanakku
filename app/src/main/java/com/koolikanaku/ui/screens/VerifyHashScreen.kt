package com.koolikanaku.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.koolikanaku.R
import com.koolikanaku.capture.HashVerifier

/** Result of checking one picked file against the job's recorded hashes. */
sealed class FileCheckResult {
    data class Match(val match: HashVerifier.FileMatch, val hash: String) : FileCheckResult()
    data class NoMatch(val hash: String) : FileCheckResult()
    object Unreadable : FileCheckResult()
}

/**
 * Verify-hash screen: first re-checks every photo saved on this phone against its recorded
 * SHA-256, then lets the person pick any original file and see whether it is one of the job's photos.
 */
@Composable
fun VerifyHashScreen(
    savedChecks: List<HashVerifier.PhotoCheck>?,
    fileResult: FileCheckResult?,
    onPickFile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Row(modifier = Modifier.statusBarsPadding().padding(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 4.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(stringResource(R.string.verify_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.verify_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(stringResource(R.string.verify_saved_title), style = MaterialTheme.typography.titleLarge)
            }
            if (savedChecks == null) {
                item { Text(stringResource(R.string.verify_checking), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val changed = savedChecks.count { !it.intact }
                item {
                    Text(
                        if (changed == 0) stringResource(R.string.verify_all_intact, savedChecks.size)
                        else stringResource(R.string.verify_some_changed, changed, savedChecks.size),
                        color = if (changed == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                items(savedChecks) { check ->
                    Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), color = MaterialTheme.colorScheme.surface) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.verify_photo_line, check.roomNumber, check.photo.role.name), style = MaterialTheme.typography.titleSmall)
                                Text(check.photo.timestampIso, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(check.photo.sha256Hash.take(16) + "…", style = com.koolikanaku.ui.theme.KooliKanakuMonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                stringResource(if (check.intact) R.string.verify_intact else R.string.verify_changed),
                                color = if (check.intact) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }

            item { Text(stringResource(R.string.verify_file_title), style = MaterialTheme.typography.titleLarge) }
            item { Text(stringResource(R.string.verify_file_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.verify_pick_file)) }
            }
            if (fileResult != null) {
                item {
                    Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), color = MaterialTheme.colorScheme.surface) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (fileResult) {
                                is FileCheckResult.Match -> {
                                    Text(stringResource(R.string.verify_match_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(R.string.verify_match_detail, fileResult.match.roomNumber, fileResult.match.photo.role.name, fileResult.match.photo.timestampIso))
                                    Text(fileResult.hash, style = com.koolikanaku.ui.theme.KooliKanakuMonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                is FileCheckResult.NoMatch -> {
                                    Text(stringResource(R.string.verify_nomatch_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                                    Text(stringResource(R.string.verify_nomatch_detail))
                                    Text(fileResult.hash, style = com.koolikanaku.ui.theme.KooliKanakuMonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FileCheckResult.Unreadable -> Text(stringResource(R.string.verify_unreadable), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.verify_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
