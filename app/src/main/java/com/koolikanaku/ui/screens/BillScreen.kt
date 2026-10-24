package com.koolikanaku.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koolikanaku.R
import com.koolikanaku.ui.AppLanguage
import com.koolikanaku.ui.components.DarkModeToggle
import com.koolikanaku.ui.components.LanguageToggle
import com.koolikanaku.ui.components.PhotoImage
import com.koolikanaku.ui.components.openPhotoExternally
import androidx.compose.ui.platform.LocalContext

data class BillMathRowUi(val label: String, val value: String, val emphasized: Boolean = false)
data class BillTaskUi(val label: String, val qty: Int, val minutesText: String, val sourceLabel: String)
data class BillRoomUi(
    val name: String,
    val sizeText: String,
    val payText: String,
    val timeText: String,
    val tasks: List<BillTaskUi>,
    val severityLabel: String,
    val severityMultText: String,
    val severityEdited: Boolean,
    val aiSaidText: String,
    val clutterLabel: String,
    val clutterMultText: String,
    val mathRows: List<BillMathRowUi>
)
data class BillPhotoUi(val label: String, val hashShort: String, val fullHash: String, val timeText: String, val uri: android.net.Uri? = null)
data class BillPhotoGroupUi(val title: String, val photos: List<BillPhotoUi>)
/** The worst close-up of a room next to its optional after photo (plan.md Section 7, P2). */
data class BeforeAfterUi(
    val roomId: String,
    val roomName: String,
    val beforeUri: android.net.Uri?,
    val afterUri: android.net.Uri?,
    val afterHashShort: String?
)

/**
 * Bill screen: totals, per-room breakdown with the math shown on request, evidence photos
 * with hash badges, and Export PDF / Share (plan.md Section 6 and 7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScreen(
    title: String,
    subtitleLine: String,
    totalTimeText: String,
    payLabel: String,
    totalPayText: String,
    rangeNote: String,
    rooms: List<BillRoomUi>,
    photoGroups: List<BillPhotoGroupUi>,
    disclaimer: String,
    beforeAfter: List<BeforeAfterUi> = emptyList(),
    onAddAfterPhoto: (String) -> Unit = {},
    onVerifyPhotos: () -> Unit = {},
    /** Set when this job came from a Find-a-worker booking (extra_feature.md Section 8). */
    bookingLabel: String? = null,
    bookingFinished: Boolean = false,
    onFinishBooking: () -> Unit = {},
    exportSheetVisible: Boolean,
    fileNameText: String,
    pdfMetaText: String,
    onExportPdf: () -> Unit,
    onShare: () -> Unit,
    onDismissExportSheet: () -> Unit,
    onBack: () -> Unit,
    language: AppLanguage = AppLanguage.EN,
    onLanguageChange: (AppLanguage) -> Unit = {},
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expandedMath by remember { mutableStateOf(setOf<Int>()) }
    var selectedPhoto by remember { mutableStateOf<BillPhotoUi?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier.statusBarsPadding().fillMaxWidth().padding(start = 4.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitleLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                    DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onExportPdf, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                        Text(" " + stringResource(R.string.bill_export_pdf), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.bill_time_label), style = MaterialTheme.typography.titleSmall)
                                Text(totalTimeText, style = MaterialTheme.typography.displayLarge)
                                Text(stringResource(R.string.bill_hours_unit), style = MaterialTheme.typography.titleSmall)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(payLabel, style = MaterialTheme.typography.titleSmall)
                                Text(totalPayText, style = MaterialTheme.typography.displayLarge)
                            }
                        }
                        HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f))
                        Text(rangeNote, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            itemsIndexed(rooms) { index: Int, room: BillRoomUi ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(room.name, style = MaterialTheme.typography.titleLarge)
                                Text(room.sizeText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(room.payText, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                Text(room.timeText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            room.tasks.forEach { task ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(task.label, style = MaterialTheme.typography.bodyLarge)
                                        Text(task.sourceLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        task.qty.toString(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                    Text(
                                        task.minutesText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${stringResource(R.string.bill_dirt_label)}: ${room.severityLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (room.severityEdited) {
                                        Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                            Text(room.aiSaidText, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Text(room.severityMultText, fontWeight = FontWeight.SemiBold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${stringResource(R.string.bill_clutter_label)}: ${room.clutterLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(room.clutterMultText, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        val expanded = index in expandedMath
                        OutlinedButton(
                            onClick = { expandedMath = if (expanded) expandedMath - index else expandedMath + index },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.bill_show_math), modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.ExpandMore, contentDescription = null)
                        }
                        if (expanded) {
                            Column(modifier = Modifier.padding(top = 2.dp)) {
                                room.mathRows.forEach { row ->
                                    Row(modifier = Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(row.label, color = if (row.emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(row.value, fontWeight = if (row.emphasized) FontWeight.Bold else FontWeight.Normal)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }

            if (beforeAfter.isNotEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column {
                                Text(stringResource(R.string.bill_after_title), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.bill_after_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            beforeAfter.forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(item.roomName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
                                                PhotoImage(uri = item.beforeUri, modifier = Modifier.fillMaxSize())
                                            }
                                            Text(stringResource(R.string.bill_before_label), style = MaterialTheme.typography.labelMedium)
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
                                                if (item.afterUri != null) PhotoImage(uri = item.afterUri, modifier = Modifier.fillMaxSize())
                                                else Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.bill_after_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                            }
                                            Text(stringResource(R.string.bill_after_label), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                    OutlinedButton(onClick = { onAddAfterPhoto(item.roomId) }, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(if (item.afterUri == null) R.string.bill_after_add else R.string.bill_after_retake))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Text(stringResource(R.string.bill_evidence_title), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.bill_evidence_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        photoGroups.forEach { group ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(group.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.height((((group.photos.size + 2) / 3) * 132).dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(group.photos) { photo ->
                                        Column(
                                            modifier = Modifier.clickable { selectedPhoto = photo },
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                            ) {
                                                PhotoImage(uri = photo.uri, modifier = Modifier.fillMaxSize())
                                            }
                                            Text(photo.label, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = onVerifyPhotos, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.bill_verify_button))
                }
            }

            if (bookingLabel != null) {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Booked job: $bookingLabel", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Finishing sends only the bill total and photo hashes back to the booking - never the photos themselves. That's what lets the rating be marked verified.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(onClick = onFinishBooking, enabled = !bookingFinished, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Text(if (bookingFinished) "Booking finished" else "Finish the booking")
                            }
                        }
                    }
                }
            }

            item {
                Text(disclaimer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    selectedPhoto?.let { photo ->
        val sheetContext = LocalContext.current
        ModalBottomSheet(onDismissRequest = { selectedPhoto = null }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(358f / 240f)
                        .let { m -> if (photo.uri != null) m.clickable { openPhotoExternally(sheetContext, photo.uri) } else m }
                ) {
                    PhotoImage(uri = photo.uri, modifier = Modifier.fillMaxSize(), cornerRadiusDp = 14)
                }
                Text(photo.label, style = MaterialTheme.typography.headlineSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.bill_taken_label), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(photo.timeText)
                }
                if (photo.uri != null) {
                    Button(onClick = { openPhotoExternally(sheetContext, photo.uri) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.bill_open_photo))
                    }
                }
                OutlinedButton(onClick = { selectedPhoto = null }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_done))
                }
            }
        }
    }

    if (exportSheetVisible) {
        ModalBottomSheet(onDismissRequest = onDismissExportSheet, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.background, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(14.dp))
                    }
                    Column {
                        Text(stringResource(R.string.bill_pdf_ready), style = MaterialTheme.typography.headlineSmall)
                        Text(fileNameText, style = com.koolikanaku.ui.theme.KooliKanakuMonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(pdfMetaText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    stringResource(R.string.bill_pdf_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                )
                Button(onClick = onShare, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Text(" " + stringResource(R.string.bill_share_dots))
                }
                OutlinedButton(onClick = onDismissExportSheet, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_done))
                }
            }
        }
    }
}
