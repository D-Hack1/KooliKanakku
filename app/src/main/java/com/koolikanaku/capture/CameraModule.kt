package com.koolikanaku.capture

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.koolikanaku.domain.EvidencePhoto
import com.koolikanaku.domain.PhotoRole
import java.io.File
import java.util.concurrent.Executors

/**
 * Fallback Camera launcher using the system camera app (ActivityResultContracts.TakePicture).
 *
 * CRITICAL RULE: Uses TakePicture() with a full FileProvider destination URI.
 * NEVER uses TakePicturePreview, which returns an unusable low-res thumbnail bitmap.
 */
@Composable
fun rememberCameraFallbackLauncher(
    role: PhotoRole,
    holdHeightM: Float? = null,
    depressionDeg: Float? = null,
    onPhotoCaptured: (EvidencePhoto) -> Unit,
    onError: (String) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    var currentFile by remember { mutableStateOf<File?>(null) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = currentFile
        if (success && file != null && file.exists() && file.length() > 0) {
            try {
                val evidence = PhotoStore.processCapturedFile(
                    context = context,
                    file = file,
                    role = role,
                    depressionDeg = depressionDeg,
                    holdHeightM = holdHeightM
                )
                onPhotoCaptured(evidence)
            } catch (e: Exception) {
                onError("Failed to process captured image: ${e.message}")
            }
        } else {
            onError("Capture was cancelled or file write failed")
        }
    }

    return {
        val (file, uri) = PhotoStore.createCaptureTarget(context)
        currentFile = file
        currentUri = uri
        launcher.launch(uri)
    }
}

/**
 * CameraX Live Viewfinder Screen with full resolution capture and measurement crosshair support.
 */
@Composable
fun CameraCaptureScreen(
    role: PhotoRole,
    holdHeightM: Float? = null,
    showCrosshair: Boolean = (role == PhotoRole.MEASURE_LENGTH || role == PhotoRole.MEASURE_WIDTH),
    onPhotoCaptured: (EvidencePhoto) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val sensor = remember { OrientationSensor(context) }
    val orientationState by sensor.orientationState.collectAsState()

    DisposableEffect(Unit) {
        sensor.startListening()
        onDispose {
            sensor.stopListening()
        }
    }

    var isCapturing by remember { mutableStateOf(false) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview, boxed to the same 1:1 square the saved photo is cropped to (PhotoStore.
        // cropToSquareInPlace), so what's framed here is what ends up in the evidence photo - no
        // surprise crop after the fact. PreviewView's default FILL_CENTER scale center-crops into
        // this square exactly the way the post-capture crop does.
        AndroidView(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).align(Alignment.Center),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        onError("Camera initialization failed: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Crosshair Overlay for Measure Shots
        if (showCrosshair) {
            MeasurementCrosshairOverlay(
                depressionDeg = orientationState.depressionDeg,
                isSteady = orientationState.isSteady,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top Status Bar (Angle Readout & Steady Status)
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Tilt: %.1f°".format(orientationState.depressionDeg),
                    color = Color.White,
                    fontSize = 14.sp
                )
                if (showCrosshair) {
                    val steadyColor = if (orientationState.isSteady) Color(0xFF4CAF50) else Color(0xFFFF5252)
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(steadyColor, CircleShape)
                    )
                    Text(
                        text = if (orientationState.isSteady) "STEADY" else "HOLD STILL",
                        color = steadyColor,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Bottom Shutter Controls
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(bottom = 32.dp, top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val canCapture = !isCapturing && (!showCrosshair || orientationState.isSteady)

            Button(
                onClick = {
                    if (!canCapture) return@Button
                    isCapturing = true

                    val (targetFile, _) = PhotoStore.createCaptureTarget(context)
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(targetFile).build()

                    imageCapture.takePicture(
                        outputOptions,
                        cameraExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                try {
                                    val evidence = PhotoStore.processCapturedFile(
                                        context = context,
                                        file = targetFile,
                                        role = role,
                                        depressionDeg = if (showCrosshair) orientationState.depressionDeg else null,
                                        holdHeightM = if (showCrosshair) holdHeightM else null
                                    )
                                    ContextCompat.getMainExecutor(context).execute {
                                        isCapturing = false
                                        onPhotoCaptured(evidence)
                                    }
                                } catch (e: Exception) {
                                    ContextCompat.getMainExecutor(context).execute {
                                        isCapturing = false
                                        onError("Processing failed: ${e.message}")
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                ContextCompat.getMainExecutor(context).execute {
                                    isCapturing = false
                                    onError("Capture failed: ${exception.message}")
                                }
                            }
                        }
                    )
                },
                enabled = canCapture,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canCapture) Color.White else Color.Gray,
                    disabledContainerColor = Color.DarkGray
                ),
                modifier = Modifier
                    .size(76.dp)
                    .border(4.dp, Color.LightGray, CircleShape)
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.Black,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

/**
 * Lightweight built-in crosshair overlay for measurement alignment.
 */
@Composable
private fun MeasurementCrosshairOverlay(
    depressionDeg: Float,
    isSteady: Boolean,
    modifier: Modifier = Modifier
) {
    val crosshairColor = if (isSteady) Color(0xFF4CAF50) else Color(0xFFFFA000)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val armLength = 40.dp.toPx()
            val strokeWidth = 2.dp.toPx()

            // Horizontal cross line
            drawLine(
                color = crosshairColor,
                start = Offset(cx - armLength, cy),
                end = Offset(cx + armLength, cy),
                strokeWidth = strokeWidth
            )
            // Vertical cross line
            drawLine(
                color = crosshairColor,
                start = Offset(cx, cy - armLength),
                end = Offset(cx, cy + armLength),
                strokeWidth = strokeWidth
            )
            // Center indicator circle
            drawCircle(
                color = crosshairColor,
                radius = 6.dp.toPx(),
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
        }

        // Directional advisory
        val warningText = when {
            depressionDeg < 10f -> "Aim lower (aim at floor baseboard)"
            depressionDeg > 70f -> "Aim higher (too steep for accuracy)"
            else -> null
        }

        if (warningText != null) {
            Surface(
                color = Color.Red.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 180.dp)
            ) {
                Text(
                    text = warningText,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
