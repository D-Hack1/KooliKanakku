package com.koolikanaku.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.koolikanaku.R
import com.koolikanaku.ui.CaptureTarget
import com.koolikanaku.ui.SizeMethod
import com.koolikanaku.ui.components.CrosshairOverlay

/**
 * Room Capture stepper: choose how the room's size is known, take the guided measure
 * shots or enter/pick a size, add wide + toughest-spot photos, then Analyze. Camera
 * capture and sensor readouts are supplied entirely through callbacks/state from the
 * caller - Ebin's capture module owns the real camera and gravity sensor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomCaptureScreen(
    roomNumber: Int,
    sizeMethod: SizeMethod,
    onSizeMethodChange: (SizeMethod) -> Unit,
    lengthShotTaken: Boolean,
    widthShotTaken: Boolean,
    measureResultText: String?,
    enterLengthFt: String,
    onEnterLengthChange: (String) -> Unit,
    enterWidthFt: String,
    onEnterWidthChange: (String) -> Unit,
    enterResultText: String?,
    presetIndex: Int?,
    presetLabels: List<Pair<String, String>>,
    onPresetChange: (Int) -> Unit,
    widePhotoCount: Int,
    toughPhotoCount: Int,
    canAddWide: Boolean,
    canAddTough: Boolean,
    readyToAnalyze: Boolean,
    statusText: String,
    activeCaptureTarget: CaptureTarget?,
    isSteady: Boolean,
    tiltText: String,
    distanceText: String,
    sideText: String,
    onOpenCamera: (CaptureTarget) -> Unit,
    onCloseCamera: () -> Unit,
    onShutter: () -> Unit,
    onAnalyzeRoom: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val isMeasure = sizeMethod == SizeMethod.MEASURE

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Row(modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.capture_back_to_setup))
                    }
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text(stringResource(R.string.capture_room_title, roomNumber), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            stringResource(R.string.capture_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            bottomBar = {
                Surface(shadowElevation = 4.dp) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (readyToAnalyze) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (readyToAnalyze) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { sheetOpen = true }, modifier = Modifier.width(120.dp).height(54.dp)) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Text(" " + stringResource(R.string.capture_photo_button))
                            }
                            Button(
                                onClick = onAnalyzeRoom,
                                enabled = readyToAnalyze,
                                modifier = Modifier.weight(1f).height(54.dp)
                            ) {
                                Text(stringResource(R.string.capture_analyze_button), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 8.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                item {
                    StepBlock(number = 1, done = when (sizeMethod) {
                        SizeMethod.MEASURE -> lengthShotTaken && widthShotTaken
                        SizeMethod.ENTER -> !enterResultText.isNullOrBlank()
                        SizeMethod.PRESET -> presetIndex != null
                    }, title = stringResource(R.string.capture_size_question)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                MethodButton(stringResource(R.string.capture_method_measure), sizeMethod == SizeMethod.MEASURE, { onSizeMethodChange(SizeMethod.MEASURE) }, Modifier.weight(1f))
                                MethodButton(stringResource(R.string.capture_method_enter), sizeMethod == SizeMethod.ENTER, { onSizeMethodChange(SizeMethod.ENTER) }, Modifier.weight(1f))
                                MethodButton(stringResource(R.string.capture_method_preset), sizeMethod == SizeMethod.PRESET, { onSizeMethodChange(SizeMethod.PRESET) }, Modifier.weight(1f))
                            }

                            when (sizeMethod) {
                                SizeMethod.MEASURE -> {
                                    Text(stringResource(R.string.capture_measure_tip), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        ShotTile(
                                            label = stringResource(R.string.capture_length_label),
                                            taken = lengthShotTaken,
                                            onClick = { onOpenCamera(CaptureTarget.LENGTH) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        ShotTile(
                                            label = stringResource(R.string.capture_width_label),
                                            taken = widthShotTaken,
                                            onClick = { onOpenCamera(CaptureTarget.WIDTH) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (measureResultText != null) {
                                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(measureResultText, style = MaterialTheme.typography.titleLarge)
                                                Text(
                                                    stringResource(R.string.capture_measure_note),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                SizeMethod.ENTER -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = enterLengthFt,
                                            onValueChange = onEnterLengthChange,
                                            label = { Text(stringResource(R.string.capture_length_ft)) },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text("×", style = MaterialTheme.typography.titleLarge)
                                        OutlinedTextField(
                                            value = enterWidthFt,
                                            onValueChange = onEnterWidthChange,
                                            label = { Text(stringResource(R.string.capture_width_ft)) },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (enterResultText != null) {
                                        Text(enterResultText, style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                                SizeMethod.PRESET -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        presetLabels.forEachIndexed { index, (label, sub) ->
                                            PresetTile(label, sub, presetIndex == index, { onPresetChange(index) }, Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    StepBlock(number = 2, done = widePhotoCount >= 1, title = stringResource(R.string.capture_wide_title)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(if (isMeasure) R.string.capture_wide_hint_recommended else R.string.capture_wide_hint_required),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                repeat(widePhotoCount) { PhotoThumb(modifier = Modifier) }
                                if (canAddWide) {
                                    AddPhotoTile(stringResource(R.string.common_add)) { onOpenCamera(CaptureTarget.WIDE) }
                                }
                            }
                        }
                    }
                }

                item {
                    StepBlock(number = 3, done = toughPhotoCount >= 1, title = stringResource(R.string.capture_toughest_title)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.capture_toughest_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                repeat(toughPhotoCount) { PhotoThumb(modifier = Modifier) }
                                if (canAddTough) {
                                    AddPhotoTile(stringResource(R.string.capture_close_up)) { onOpenCamera(CaptureTarget.TOUGHEST) }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (sheetOpen) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.capture_sheet_which_photo), style = MaterialTheme.typography.headlineSmall)
                    SheetOption(stringResource(R.string.capture_wide_title), Icons.Outlined.PhotoCamera) {
                        sheetOpen = false
                        onOpenCamera(CaptureTarget.WIDE)
                    }
                    SheetOption(stringResource(R.string.capture_sheet_close_up_full), Icons.Outlined.CameraAlt) {
                        sheetOpen = false
                        onOpenCamera(CaptureTarget.TOUGHEST)
                    }
                }
            }
        }

        if (activeCaptureTarget != null) {
            CameraOverlayPlaceholder(
                target = activeCaptureTarget,
                isSteady = isSteady,
                tiltText = tiltText,
                distanceText = distanceText,
                sideText = sideText,
                onClose = onCloseCamera,
                onShutter = onShutter
            )
        }
    }
}

@Composable
private fun StepBlock(number: Int, done: Boolean, title: String, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = CircleShape,
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            border = BorderStroke(if (done) 0.dp else 1.dp, MaterialTheme.colorScheme.outline),
            contentColor = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (done) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                else Text(number.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun MethodButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(44.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ShotTile(label: String, taken: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (taken) 1.dp else 1.5.dp, if (taken) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.secondary)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(96.dp).padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (taken) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(label, style = MaterialTheme.typography.titleSmall)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun PresetTile(label: String, sub: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(64.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhotoThumb(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(84.dp, 112.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun AddPhotoTile(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(84.dp, 112.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun SheetOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Stand-in for the live CameraX preview while that module is built elsewhere: it supplies
 * the same crosshair/readout/shutter UI, just over a plain background instead of a frame feed.
 */
@Composable
private fun CameraOverlayPlaceholder(
    target: CaptureTarget,
    isSteady: Boolean,
    tiltText: String,
    distanceText: String,
    sideText: String,
    onClose: () -> Unit,
    onShutter: () -> Unit
) {
    val isMeasureShot = target == CaptureTarget.LENGTH || target == CaptureTarget.WIDTH
    val titleRes = when (target) {
        CaptureTarget.LENGTH -> R.string.capture_cam_length_title
        CaptureTarget.WIDTH -> R.string.capture_cam_width_title
        CaptureTarget.WIDE -> R.string.capture_cam_wide_title
        CaptureTarget.TOUGHEST -> R.string.capture_cam_tough_title
    }
    val promptRes = when (target) {
        CaptureTarget.LENGTH -> R.string.capture_prompt_length
        CaptureTarget.WIDTH -> R.string.capture_prompt_width
        CaptureTarget.WIDE -> R.string.capture_prompt_wide
        CaptureTarget.TOUGHEST -> R.string.capture_prompt_tough
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF171210))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0x99171210)).padding(start = 4.dp, top = 40.dp, bottom = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_cancel), tint = Color(0xFFF5EBE0))
                }
                Text(stringResource(titleRes), color = Color(0xFFF5EBE0), style = MaterialTheme.typography.titleSmall)
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .background(Color(0xB8171210), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(stringResource(promptRes), color = Color(0xFFF5EBE0), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.capture_no_scan_yet), color = Color(0xFFD9CBBB), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        CrosshairOverlay(
            showReadout = isMeasureShot,
            isSteady = isSteady,
            tiltText = tiltText,
            distanceText = distanceText,
            sideText = sideText,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(Color(0xE6171210)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shutterEnabled = !isMeasureShot || isSteady
            Surface(
                modifier = Modifier
                    .size(78.dp)
                    .clickable(enabled = shutterEnabled, onClick = onShutter),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(4.dp, Color(0xFFFFF9F2).copy(alpha = if (shutterEnabled) 1f else 0.4f))
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(9.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFF9F2).copy(alpha = if (shutterEnabled) 1f else 0.4f),
                        modifier = Modifier.fillMaxSize()
                    ) {}
                }
            }
        }
    }
}
