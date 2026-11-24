package com.example.mingleboothkalakini.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.mingleboothkalakini.R
import com.example.mingleboothkalakini.camera.HdmiCaptureController
import com.example.mingleboothkalakini.camera.HdmiCaptureState
import com.example.mingleboothkalakini.camera.SonyCameraController
import com.example.mingleboothkalakini.camera.SonyCameraState
import com.example.mingleboothkalakini.data.BoothResult
import com.example.mingleboothkalakini.data.BoothSettings
import com.example.mingleboothkalakini.data.Slot
import com.example.mingleboothkalakini.util.BoothUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

// Mingle Booth Retro-Editorial Palette (AGENTS.md Warm Retro Tokens)
val WarmSand = Color(0xFFF7F3EB)
val BgCream = Color(0xFFFDF9F2)
val SurfaceSand = Color(0xFFF7ECE1)
val SoftPeach = Color(0xFFFCE4D8)
val CardCream = Color(0xFFFFFDF8)
val BorderWarm = Color(0xFFE6DFD3)
val Terracotta = Color(0xFFBD5338)       // Earthy Retro Terracotta
val SunsetCoral = Color(0xFFFF6B3D)      // Warm Coral-Orange highlight
val DeepVermilion = Color(0xFFC82800)    // Deep Retro Red shade
val Espresso = Color(0xFF221B17)         // Deep Neutral Dark Espresso
val MutedCocoa = Color(0xFF7D564A)       // Warm Vintage Sepia / Cocoa
val DustyRose = Color(0xFFF2846B)        // Soft Retro Coral-Rose
val SageGreen = Color(0xFF4F6D5A)        // Vintage Sage Green
val WarmGold = Color(0xFFE07A1F)         // Retro Amber Gold

// Legacy aliases
val BgDark = BgCream
val CardDark = CardCream
val PurpleAccent = Terracotta
val GoldAccent = WarmGold
val PinkAccent = DustyRose
val SkyAccent = SunsetCoral
val EmeraldAccent = SageGreen

// Multi-stop Color Gradation Brushes
val RetroCanvasBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFDFBF7),
        Color(0xFFFAF0E4),
        Color(0xFFFCE5DA),
        Color(0xFFF7E8DA)
    ),
    start = Offset(0f, 0f),
    end = Offset(1600f, 1600f)
)

val RetroButtonBrush = Brush.horizontalGradient(
    colors = listOf(
        DeepVermilion,
        Terracotta,
        SunsetCoral
    )
)

val RetroTitleBrush = Brush.linearGradient(
    colors = listOf(
        Espresso,
        DeepVermilion,
        Terracotta
    ),
    start = Offset(0f, 0f),
    end = Offset(900f, 200f)
)

val RetroCardBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFFFF8F2)
    )
)

@Composable
fun ScreenSaverView(
    settings: BoothSettings,
    isLoading: Boolean,
    onStart: () -> Unit,
    onOpenAdmin: () -> Unit,
    onRefresh: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "retroFloat")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )
    val buttonScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buttonScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val hasActiveEvent = settings.hasActiveEvent
    val hasActiveSlots = (settings.activeFrame?.slots?.size ?: 0) > 0

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroCanvasBrush)
            .clickable(enabled = !isLoading) { onStart() }
    ) {
        val isCompact = maxWidth < 600.dp

        // Ambient Color Gradation Glow Orbs in Background
        Box(
            modifier = Modifier
                .size(if (isCompact) 320.dp else 460.dp)
                .align(Alignment.TopStart)
                .offset(x = (-100).dp, y = (-80).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Terracotta.copy(alpha = glowAlpha), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(if (isCompact) 360.dp else 520.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(SunsetCoral.copy(alpha = glowAlpha), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 16.dp else 24.dp)
        ) {
            // Top Right Admin & Refresh Controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onRefresh,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = CardCream.copy(alpha = 0.85f),
                        contentColor = Espresso
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Terracotta)
                }

                if (isCompact) {
                    FilledTonalIconButton(
                        onClick = onOpenAdmin,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = CardCream.copy(alpha = 0.85f),
                            contentColor = Espresso
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Studio Admin", tint = Terracotta)
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenAdmin,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardCream.copy(alpha = 0.85f),
                            contentColor = Espresso
                        ),
                        border = BorderStroke(1.dp, BorderWarm),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Terracotta, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "STUDIO ADMIN",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Center Hero Retro-Editorial Branding (with vertical scroll safety on compact displays)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Retro Logo Centerpiece with Soft Radial Aura
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationY = floatOffset }
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 160.dp else 220.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        SunsetCoral.copy(alpha = 0.18f),
                                        SoftPeach.copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                    if (!settings.brandLogoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = settings.brandLogoUrl,
                            contentDescription = "Brand Logo",
                            modifier = Modifier
                                .heightIn(max = if (isCompact) 120.dp else 175.dp)
                                .widthIn(max = if (isCompact) 210.dp else 290.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.logo_mingle_booth),
                            contentDescription = "Mingle Booth Retro Logo",
                            modifier = Modifier
                                .widthIn(max = if (isCompact) 220.dp else 300.dp)
                                .height(if (isCompact) 130.dp else 185.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Gradient-Brushed Display Serif Event Title
                Text(
                    text = settings.eventTitle.ifBlank { "Mingle Booth" },
                    style = TextStyle(
                        brush = RetroTitleBrush,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
                        fontSize = if (isCompact) 28.sp else 44.sp,
                        lineHeight = if (isCompact) 34.sp else 50.sp,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(Modifier.height(8.dp))

                // Tracked Sans-Serif Subtitle Pill
                Surface(
                    color = SoftPeach.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Terracotta.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = settings.eventSubtitle.ifBlank { "BY KALA KINI" }.uppercase(),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = if (isCompact) 10.sp else 12.sp,
                        letterSpacing = if (isCompact) 2.sp else 3.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DeepVermilion,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(
                            horizontal = if (isCompact) 14.dp else 18.dp,
                            vertical = if (isCompact) 4.dp else 5.dp
                        )
                    )
                }

                Spacer(Modifier.height(if (isCompact) 20.dp else 30.dp))

                if (isLoading) {
                    CircularProgressIndicator(color = Terracotta, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Warming up the retro camera…",
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        color = MutedCocoa,
                        fontSize = 14.sp
                    )
                } else {
                    // Gradient-filled Retro Pill Button
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = buttonScale
                                scaleY = buttonScale
                            }
                            .shadow(10.dp, RoundedCornerShape(50), spotColor = Terracotta)
                            .clip(RoundedCornerShape(50))
                            .background(RetroButtonBrush)
                            .border(2.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .padding(
                                horizontal = if (isCompact) 22.dp else 36.dp,
                                vertical = if (isCompact) 14.dp else 17.dp
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                            Text(
                                text = "TAP TO START PHOTOBOOTH",
                                fontFamily = FontFamily.SansSerif,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                letterSpacing = if (isCompact) 1.sp else 2.sp,
                                fontSize = if (isCompact) 13.sp else 15.sp
                            )
                            Text("✦", color = Color.White, fontSize = 16.sp)
                        }
                    }
                }

                if (!isLoading && (!hasActiveEvent || !hasActiveSlots)) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = WarmGold.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, WarmGold.copy(alpha = 0.45f)),
                        modifier = Modifier.clickable { onOpenAdmin() }
                    ) {
                        Text(
                            text = if (!hasActiveEvent) {
                                "⚠️ No active event selected · Tap to configure in Admin"
                            } else {
                                "⚠️ No active frame with photo slots · Tap to configure in Admin"
                            },
                            color = Espresso,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Bottom active frame monospace film badge
            if (!isLoading && settings.activeFrame != null) {
                Surface(
                    color = CardCream.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, BorderWarm),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = if (isCompact) {
                            "[ ${settings.activeFrame.name.uppercase()} • ${settings.activeFrame.slots.size} EXP ]"
                        } else {
                            "[ FRAME: ${settings.activeFrame.name.uppercase()} • ${settings.activeFrame.slots.size} EXP ]"
                        },
                        color = MutedCocoa,
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (isCompact) 10.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CaptureView(
    settings: BoothSettings,
    retakeSlotIndex: Int?,
    existingPhotos: List<Bitmap>,
    externalShutterTrigger: SharedFlow<Unit>? = null,
    onComplete: (List<Bitmap>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val isExternalUsb = settings.cameraFacing.equals("EXTERNAL", ignoreCase = true) ||
        settings.cameraFacing.equals("SONY_PC_REMOTE", ignoreCase = true)

    LaunchedEffect(isExternalUsb) {
        // For USB devices (Sony PC Remote or HDMI Capture), standard Android CAMERA runtime permission
        // is not strictly needed (it uses UsbManager), but we still check it if using CameraX/built-in camera.
        if (!isExternalUsb && !hasCameraPermission) {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // For USB devices, standard Android CAMERA runtime permission is not strictly needed
    // (it uses UsbManager), but we still check it if using CameraX/built-in fallback.
    if (!isExternalUsb && !hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RetroCanvasBrush)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardCream),
                border = BorderStroke(1.5.dp, BorderWarm),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Terracotta, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Camera Access Required",
                        style = TextStyle(
                            brush = RetroTitleBrush,
                            fontFamily = FontFamily.Serif,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Please grant camera permission to start your retro photobooth session.",
                        color = MutedCocoa,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onCancel,
                            border = BorderStroke(1.dp, BorderWarm),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Espresso)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White)
                        ) {
                            Text("Grant Permission", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        return
    }

    val sortedSlots = remember(settings.activeFrame) {
        (settings.activeFrame?.slots ?: BoothUtils.default3Slots()).sortedBy { it.slotIndex }
    }
    val isRetake = retakeSlotIndex != null
    val totalShots = sortedSlots.size

    var currentShot by remember(retakeSlotIndex) { mutableIntStateOf(retakeSlotIndex ?: 0) }
    var intermissionText by remember { mutableStateOf<String?>(null) }
    var flashActive by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }

    val thumbBitmaps = remember(retakeSlotIndex, existingPhotos) {
        mutableStateListOf<Bitmap?>().apply {
            for (i in 0 until totalShots) {
                val existing = existingPhotos.getOrNull(i)
                add(if (!isRetake || i != retakeSlotIndex) existing else null)
            }
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // External camera controllers (Sony PC Remote and HDMI UVC Capture)
    val sonyController = remember(isExternalUsb) {
        if (isExternalUsb) SonyCameraController.getInstance(context) else null
    }
    val sonyState by if (sonyController != null) {
        sonyController.state.collectAsState()
    } else {
        remember { mutableStateOf<SonyCameraState>(SonyCameraState.NoCamera) }
    }
    val isSonyReady = sonyState is SonyCameraState.Ready

    val hdmiController = remember(isExternalUsb) {
        if (isExternalUsb) HdmiCaptureController.getInstance(context) else null
    }
    val hdmiState by if (hdmiController != null) {
        hdmiController.state.collectAsState()
    } else {
        remember { mutableStateOf<HdmiCaptureState>(HdmiCaptureState.NoDevice) }
    }

    val isHdmiActive = isExternalUsb && (hdmiState !is HdmiCaptureState.NoDevice)

    DisposableEffect(isExternalUsb, isHdmiActive) {
        if (isExternalUsb) {
            sonyController?.start()
            sonyController?.setLiveViewEnabled(!isHdmiActive)
            sonyController?.enableAutoCapture(mirror = settings.mirrorResult)
            hdmiController?.start()
            hdmiController?.setLiveViewEnabled(isHdmiActive)
        }
        onDispose {
            sonyController?.disableAutoCapture()
            sonyController?.setLiveViewEnabled(false)
            hdmiController?.setLiveViewEnabled(false)
        }
    }

    // Bind CameraX only if NOT using external USB camera
    LaunchedEffect(isExternalUsb, settings.cameraFacing, settings.cameraResolution) {
        if (isExternalUsb) {
            imageCapture = null
            cameraReady = true
            return@LaunchedEffect
        }
        val cameraProvider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { cont.resume(future.get()) },
                ContextCompat.getMainExecutor(context)
            )
        }

        val (resW, resH) = settings.cameraResolution.split("x").let {
            val w = it.getOrNull(0)?.toIntOrNull() ?: 1920
            val h = it.getOrNull(1)?.toIntOrNull() ?: 1080
            w to h
        }

        val resSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(Size(resW, resH), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resSelector)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resSelector)
            .build()

        val selector = BoothUtils.resolveCameraSelector(cameraProvider, settings.cameraFacing)

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            imageCapture = capture
            cameraReady = true
        } catch (e: Exception) {
            // Strictly do NOT fall back to front camera if an external camera was explicitly requested!
            val isExplicitExternal = settings.cameraFacing.equals("EXTERNAL", ignoreCase = true) ||
                settings.cameraFacing.equals("SONY_PC_REMOTE", ignoreCase = true)
            if (!isExplicitExternal) {
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture)
                    imageCapture = capture
                    cameraReady = true
                } catch (fallbackErr: Exception) {
                    fallbackErr.printStackTrace()
                }
            } else {
                cameraReady = false
            }
        }
    }

    var isCapturingNow by remember { mutableStateOf(false) }
    var captureErrorMsg by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Common saver and flow-advancer for photos (both manual button/countdown and camera trigger)
    suspend fun saveAndAdvancePhoto(rawBmp: Bitmap) {
        thumbBitmaps[currentShot] = rawBmp
        BoothUtils.playShutter()
        flashActive = true
        delay(150)
        flashActive = false

        // Advance to next shot or complete
        val nextShot = currentShot + 1
        if (isRetake || nextShot >= totalShots) {
            BoothUtils.playSuccess()
            delay(300)
            // Filter out any nulls and pass all shots to ReviewView
            val finalList = (0 until totalShots).mapNotNull { idx ->
                thumbBitmaps.getOrNull(idx) ?: existingPhotos.getOrNull(idx)
            }
            onComplete(finalList)
        } else {
            currentShot = nextShot
            intermissionText = "Pose $currentShot Saved! ✦"
            delay(600)
            intermissionText = null
        }
    }

    // Common handler for photos captured via physical camera trigger or external event
    fun onPhotoReceived(rawBmp: Bitmap) {
        if (isCapturingNow) return
        coroutineScope.launch {
            isCapturingNow = true
            intermissionText = null
            captureErrorMsg = null
            try {
                saveAndAdvancePhoto(rawBmp)
            } finally {
                isCapturingNow = false
            }
        }
    }

    // Realtime listener for physical camera shutter trigger events
    LaunchedEffect(sonyController) {
        sonyController?.capturedPhotoEvent?.collect { bmp ->
            onPhotoReceived(bmp)
        }
    }

    LaunchedEffect(hdmiController) {
        hdmiController?.capturedPhotoEvent?.collect { bmp ->
            onPhotoReceived(bmp)
        }
    }

    // External Shutter Trigger (e.g. Bluetooth remote, keyboard, or UI countdown/pill tap)
    fun triggerSnap() {
        if (isCapturingNow) return
        if (isExternalUsb && !isHdmiActive && !isSonyReady) {
            intermissionText = "Please wait: Sony camera connecting…"
            coroutineScope.launch {
                delay(2000)
                intermissionText = null
            }
            return
        }

        coroutineScope.launch {
            isCapturingNow = true
            intermissionText = null
            captureErrorMsg = null

            val rawBmp: Bitmap? = if (isExternalUsb) {
                if (isHdmiActive) {
                    try {
                        if (isSonyReady && sonyController != null) {
                            try {
                                intermissionText = "Releasing Sony shutter & downloading photo…"
                                sonyController.capture(mirror = settings.mirrorResult)
                            } catch (e: Exception) {
                                android.util.Log.w("BoothScreens", "USB shutter capture fallback to HDMI frame: ${e.message}")
                                hdmiController?.takePhoto(mirror = settings.mirrorResult)
                            }
                        } else {
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching { BoothUtils.triggerSonyUsbShutter(context) }
                            }
                            hdmiController?.takePhoto(mirror = settings.mirrorResult)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        captureErrorMsg = e.message ?: "HDMI Capture failed"
                        null
                    }
                } else if (sonyController != null) {
                    try {
                        intermissionText = "Releasing Sony shutter & downloading full-size photo…"
                        sonyController.capture(mirror = settings.mirrorResult)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        captureErrorMsg = e.message ?: "Sony USB Capture failed"
                        null
                    }
                } else {
                    null
                }
            } else {
                // Best-effort physical Sony Shutter trigger (if connected over OTG)
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { BoothUtils.triggerSonyUsbShutter(context) }
                }

                // Immediately capture the frame from CameraX / HDMI stream
                capturePhotoBitmap(context, imageCapture, previewView, settings.mirrorResult, settings.cameraFacing)
            }

            if (rawBmp != null) {
                saveAndAdvancePhoto(rawBmp)
                isCapturingNow = false
            } else {
                intermissionText = captureErrorMsg ?: "Capture failed. Please check Sony connection & retry."
                delay(3000)
                intermissionText = null
                isCapturingNow = false
            }
        }
    }

    LaunchedEffect(externalShutterTrigger) {
        externalShutterTrigger?.collect {
            triggerSnap()
        }
    }

    val activeSlot: Slot? = sortedSlots.getOrNull(currentShot)
    val slotAspect = if (activeSlot != null && activeSlot.h > 0) {
        (activeSlot.w.toFloat() / activeSlot.h.toFloat()).coerceIn(0.4f, 2.2f)
    } else {
        4f / 3f
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val isLandscape = maxWidth > maxHeight
        val isCompact = maxWidth < 600.dp

        // 1. Full-Width Live Camera Preview Viewport (External USB [HDMI or Sony] OR CameraX)
        if (isExternalUsb) {
            if (isHdmiActive && hdmiController != null) {
                HdmiLiveFeed(
                    controller = hdmiController,
                    mirror = settings.mirrorCamera,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (sonyController != null) {
                SonyLiveFeed(
                    controller = sonyController,
                    mirror = settings.mirrorCamera,
                    modifier = Modifier.fillMaxSize()
                )

                // When Sony is not ready, display clean status guidance card overlay
                if (!isSonyReady) {
                    SonyStatusCard(
                        state = sonyState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = if (settings.mirrorCamera) -1f else 1f
                    }
            )
        }

        // 2. White Shutter Flash Effect
        if (flashActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }

        // 3. Slot Aspect-Ratio Framing Guide Overlay
        ActiveSlotFramingOverlay(
            aspectRatio = slotAspect,
            modifier = Modifier.fillMaxSize()
        )

        // 4. Top-Left Floating Controls: Exit / Cancel & Film Format Pill
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(if (isCompact) 10.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = CardCream.copy(alpha = 0.90f),
                shape = RoundedCornerShape(50),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, BorderWarm),
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .clickable { onCancel() }
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (isCompact) 12.dp else 16.dp,
                        vertical = if (isCompact) 10.dp else 12.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Espresso, modifier = Modifier.size(16.dp))
                    Text(
                        if (isRetake) (if (isCompact) "Cancel" else "Cancel Retake") else "Exit",
                        color = Espresso,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                color = CardCream.copy(alpha = 0.90f),
                shape = RoundedCornerShape(50),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, BorderWarm)
            ) {
                Text(
                    text = if (activeSlot != null) {
                        if (isCompact) BoothUtils.formatAspectRatio(activeSlot.w, activeSlot.h)
                        else "FILM • ${BoothUtils.formatAspectRatio(activeSlot.w, activeSlot.h)}"
                    } else "LIVE PREVIEW",
                    color = Espresso,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (isCompact) 10.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(
                        horizontal = if (isCompact) 10.dp else 14.dp,
                        vertical = if (isCompact) 6.dp else 8.dp
                    )
                )
            }
        }

        // 5. Small Overlay Preview for Captured Photos / Slots (Top-Right)
        SmallSlotTrackerOverlay(
            isLandscape = isLandscape,
            isCompact = isCompact,
            currentShot = currentShot,
            totalShots = totalShots,
            isRetake = isRetake,
            thumbs = thumbBitmaps,
            slotAspect = slotAspect,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(if (isCompact) 10.dp else 16.dp)
        )

        // 6. Tactile Camera Shutter Prompt Pill (Bottom Center)
        val showSonyShutterPrompt = (isExternalUsb && !isHdmiActive) || (isHdmiActive && isSonyReady)
        Surface(
            color = CardCream.copy(alpha = 0.92f),
            shape = RoundedCornerShape(50),
            shadowElevation = 8.dp,
            border = BorderStroke(1.5.dp, BorderWarm),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isCompact) 16.dp else 24.dp)
                .defaultMinSize(minHeight = 44.dp)
                .clickable(enabled = !isCapturingNow && (!isExternalUsb || isHdmiActive)) {
                    triggerSnap()
                }
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (isCompact) 16.dp else 24.dp,
                    vertical = if (isCompact) 11.dp else 14.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isCapturingNow) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Terracotta
                    )
                    Text(
                        text = "SAVING PHOTO…",
                        color = Espresso,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        fontSize = if (isCompact) 11.sp else 13.sp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (showSonyShutterPrompt) (if (isSonyReady) SageGreen else Terracotta) else (if (cameraReady) SageGreen else Terracotta))
                    )
                    val promptText = if (showSonyShutterPrompt) {
                        if (isCompact) {
                            if (isRetake) "PRESS SHUTTER • RETAKE 0${currentShot + 1}"
                            else "PRESS SHUTTER • POSE 0${currentShot + 1}/0$totalShots"
                        } else {
                            if (isRetake) "PRESS CAMERA SHUTTER TO RETAKE POSE 0${currentShot + 1}"
                            else "PRESS CAMERA SHUTTER • POSE 0${currentShot + 1} OF 0$totalShots"
                        }
                    } else {
                        if (isCompact) {
                            if (isRetake) "TAP / SHUTTER • RETAKE 0${currentShot + 1}"
                            else "TAP / SHUTTER • POSE 0${currentShot + 1}/0$totalShots"
                        } else {
                            if (isRetake) "TRIGGER SHUTTER / TAP TO RETAKE POSE 0${currentShot + 1}"
                            else "TRIGGER SHUTTER / TAP • POSE 0${currentShot + 1} OF 0$totalShots"
                        }
                    }
                    Text(
                        text = promptText,
                        color = Espresso,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = if (isCompact) 11.sp else 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 7. Brief Intermission Toast
        if (intermissionText != null) {
            Surface(
                color = CardCream.copy(alpha = 0.95f),
                shape = RoundedCornerShape(50),
                shadowElevation = 8.dp,
                border = BorderStroke(1.5.dp, Terracotta),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (isCompact) 56.dp else 70.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✦", color = Terracotta, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        intermissionText ?: "",
                        color = Espresso,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isCompact) 12.sp else 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallSlotTrackerOverlay(
    isLandscape: Boolean,
    isCompact: Boolean = false,
    currentShot: Int,
    totalShots: Int,
    isRetake: Boolean,
    thumbs: List<Bitmap?>,
    slotAspect: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CardCream.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 6.dp,
        border = BorderStroke(1.5.dp, BorderWarm),
        modifier = modifier
    ) {
        if (isLandscape) {
            Column(
                modifier = Modifier.padding(if (isCompact) 6.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp)
            ) {
                Text(
                    text = if (isRetake) "[ RETAKE ]" else "[ 0${currentShot + 1} / 0$totalShots ]",
                    color = Terracotta,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (isCompact) 9.sp else 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                thumbs.forEachIndexed { idx, bmp ->
                    MiniSlotBox(idx = idx, isCurrent = idx == currentShot, bitmap = bmp, slotAspect = slotAspect, isCompact = isCompact)
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (isCompact) 8.dp else 12.dp,
                    vertical = if (isCompact) 6.dp else 8.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp)
            ) {
                Text(
                    text = if (isRetake) "[ RETAKE ]" else "[ 0${currentShot + 1} / 0$totalShots ]",
                    color = Terracotta,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (isCompact) 9.sp else 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                thumbs.forEachIndexed { idx, bmp ->
                    MiniSlotBox(idx = idx, isCurrent = idx == currentShot, bitmap = bmp, slotAspect = slotAspect, isCompact = isCompact)
                }
            }
        }
    }
}

@Composable
private fun MiniSlotBox(
    idx: Int,
    isCurrent: Boolean,
    bitmap: Bitmap?,
    slotAspect: Float,
    isCompact: Boolean = false
) {
    val boxWidth = if (isCompact) 38.dp else 52.dp
    val baseHeight = (if (isCompact) 38f else 52f) / slotAspect.coerceIn(0.6f, 1.8f)
    val boxHeight = baseHeight.coerceIn(if (isCompact) 28f else 38f, if (isCompact) 50f else 66f).dp

    val borderColor = when {
        isCurrent -> Terracotta
        bitmap != null -> SunsetCoral
        else -> BorderWarm
    }

    Box(
        modifier = Modifier
            .width(boxWidth)
            .height(boxHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceSand)
            .border(if (isCurrent) 2.5.dp else 1.dp, borderColor, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Shot ${idx + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "0${idx + 1}",
                color = if (isCurrent) Terracotta else MutedCocoa,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Terracotta)
            )
        }
    }
}

@Composable
private fun ActiveSlotFramingOverlay(
    aspectRatio: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val containerAspect = maxWidth / maxHeight
        val targetAspect = aspectRatio.coerceIn(0.4f, 2.5f)

        val frameModifier = if (containerAspect > targetAspect) {
            Modifier
                .fillMaxHeight(0.90f)
                .aspectRatio(targetAspect)
        } else {
            Modifier
                .fillMaxWidth(0.90f)
                .aspectRatio(targetAspect)
        }

        Box(
            modifier = frameModifier
                .border(1.5.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
        ) {
            Text(
                "⌜",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 28.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 6.dp, top = 2.dp)
            )
            Text(
                "⌝",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 28.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 6.dp, top = 2.dp)
            )
            Text(
                "⌞",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 28.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 2.dp)
            )
            Text(
                "⌟",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 28.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 2.dp)
            )
        }
    }
}

private suspend fun capturePhotoBitmap(
    context: android.content.Context,
    imageCapture: ImageCapture?,
    previewView: PreviewView,
    mirrorResult: Boolean,
    cameraFacing: String
): Bitmap? {
    val viewBmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        previewView.bitmap
    }
    if (viewBmp != null) {
        return if (mirrorResult) {
            val m = Matrix().apply { preScale(-1f, 1f) }
            Bitmap.createBitmap(viewBmp, 0, 0, viewBmp.width, viewBmp.height, m, true)
        } else {
            viewBmp
        }
    }

    val capture = imageCapture ?: return null
    return suspendCancellableCoroutine { cont ->
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val m = Matrix().apply {
                            postRotate(image.imageInfo.rotationDegrees.toFloat())
                            val isFront = cameraFacing.uppercase() != "BACK"
                            if ((isFront && mirrorResult) || (!isFront && mirrorResult)) {
                                postScale(-1f, 1f)
                            }
                        }
                        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
                        image.close()
                        cont.resume(rotated)
                    } catch (e: Exception) {
                        image.close()
                        cont.resume(null)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resume(null)
                }
            }
        )
    }
}

@Composable
fun ReviewView(
    photos: List<Bitmap>,
    settings: BoothSettings,
    cachedFrameBitmap: Bitmap?,
    initialSlotIndex: Int = 0,
    photosRevision: Int = 0,
    onRetakeSlot: (Int) -> Unit,
    onConfirm: () -> Unit,
    onRestartAll: () -> Unit
) {
    var selectedIdx by remember(photosRevision, initialSlotIndex) {
        mutableIntStateOf(initialSlotIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)))
    }
    var stripPreview by remember(photosRevision) { mutableStateOf<Bitmap?>(null) }
    var fullPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fullPreviewTitle by remember { mutableStateOf("") }

    LaunchedEffect(photos, photosRevision, settings, cachedFrameBitmap) {
        if (photos.isNotEmpty()) {
            stripPreview = BoothUtils.composeStrip(
                photos = photos,
                settings = settings,
                cachedFrameBitmap = cachedFrameBitmap,
                maxDimension = 1200
            )
        }
    }

    val validIdx = selectedIdx.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    val currentPhoto = photos.getOrNull(validIdx)
    var phoneReviewTab by remember { mutableIntStateOf(0) } // 0: Poses, 1: Analog Strip

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroCanvasBrush)
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        val isLandscape = maxWidth > maxHeight
        val isCompact = maxWidth < 600.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Editorial Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Review Your Poses",
                    style = TextStyle(
                        brush = RetroTitleBrush,
                        fontFamily = FontFamily.Serif,
                        fontSize = if (isCompact) 22.sp else 28.sp,
                        fontWeight = FontWeight.Black
                    )
                )

                // Phone-only segmented tab control (Poses vs Analog Strip)
                if (isCompact && !isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .clip(RoundedCornerShape(50))
                            .background(SurfaceSand)
                            .border(1.dp, BorderWarm, RoundedCornerShape(50))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { phoneReviewTab = 0 },
                            shape = RoundedCornerShape(50),
                            color = if (phoneReviewTab == 0) Terracotta else Color.Transparent
                        ) {
                            Text(
                                "📸 Photo Poses",
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (phoneReviewTab == 0) Color.White else Espresso
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { phoneReviewTab = 1 },
                            shape = RoundedCornerShape(50),
                            color = if (phoneReviewTab == 1) Terracotta else Color.Transparent
                        ) {
                            Text(
                                "🎞️ Analog Strip",
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (phoneReviewTab == 1) Color.White else Espresso
                            )
                        }
                    }
                }
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SelectedPhotoInspector(
                        photos = photos,
                        validIdx = validIdx,
                        currentPhoto = currentPhoto,
                        isCompact = isCompact,
                        onSelectIdx = { selectedIdx = it },
                        onPhotoClick = { bmp ->
                            fullPreviewBitmap = bmp
                            fullPreviewTitle = "[ POSE 0${validIdx + 1} OF 0${photos.size} • FULL VIEW ]"
                        },
                        onRetakeSlot = onRetakeSlot,
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                    )

                    StripPreviewCard(
                        stripBitmap = stripPreview,
                        onStripClick = { bmp ->
                            fullPreviewBitmap = bmp
                            fullPreviewTitle = "[ ANALOG STRIP • FULL PREVIEW ]"
                        },
                        modifier = Modifier
                            .weight(0.7f)
                            .fillMaxHeight()
                    )
                }
            } else if (isCompact) {
                // Phone portrait: full-size segmented display
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (phoneReviewTab == 0) {
                        SelectedPhotoInspector(
                            photos = photos,
                            validIdx = validIdx,
                            currentPhoto = currentPhoto,
                            isCompact = true,
                            onSelectIdx = { selectedIdx = it },
                            onPhotoClick = { bmp ->
                                fullPreviewBitmap = bmp
                                fullPreviewTitle = "[ POSE 0${validIdx + 1} OF 0${photos.size} • FULL VIEW ]"
                            },
                            onRetakeSlot = onRetakeSlot,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        StripPreviewCard(
                            stripBitmap = stripPreview,
                            onStripClick = { bmp ->
                                fullPreviewBitmap = bmp
                                fullPreviewTitle = "[ ANALOG STRIP • FULL PREVIEW ]"
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                // Tablet portrait: side-by-side / stacked dual preview
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SelectedPhotoInspector(
                        photos = photos,
                        validIdx = validIdx,
                        currentPhoto = currentPhoto,
                        isCompact = false,
                        onSelectIdx = { selectedIdx = it },
                        onPhotoClick = { bmp ->
                            fullPreviewBitmap = bmp
                            fullPreviewTitle = "[ POSE 0${validIdx + 1} OF 0${photos.size} • FULL VIEW ]"
                        },
                        onRetakeSlot = onRetakeSlot,
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxWidth()
                    )

                    StripPreviewCard(
                        stripBitmap = stripPreview,
                        onStripClick = { bmp ->
                            fullPreviewBitmap = bmp
                            fullPreviewTitle = "[ ANALOG STRIP • FULL PREVIEW ]"
                        },
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxWidth()
                    )
                }
            }

            // Action Buttons with Gradient Primary CTA
            Row(
                modifier = if (isCompact) Modifier.fillMaxWidth() else Modifier,
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = (if (isCompact) Modifier.weight(1.3f) else Modifier)
                        .shadow(6.dp, RoundedCornerShape(50), spotColor = Terracotta)
                        .clip(RoundedCornerShape(50))
                        .background(RetroButtonBrush)
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable { onConfirm() }
                        .padding(horizontal = if (isCompact) 16.dp else 28.dp, vertical = if (isCompact) 13.dp else 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isCompact) "CONFIRM PHOTOS ✦" else "TAKE THE PHOTOS!",
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        fontSize = if (isCompact) 12.sp else 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedButton(
                    onClick = onRestartAll,
                    modifier = (if (isCompact) Modifier.weight(0.9f) else Modifier).defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.5.dp, BorderWarm),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardCream.copy(alpha = 0.8f),
                        contentColor = Espresso
                    ),
                    contentPadding = PaddingValues(horizontal = if (isCompact) 10.dp else 20.dp, vertical = if (isCompact) 13.dp else 14.dp)
                ) {
                    Text(
                        "Retake All ↺",
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isCompact) 12.sp else 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Full Page Modal Preview for Photo or Strip
        if (fullPreviewBitmap != null) {
            FullScreenPreviewDialog(
                bitmap = fullPreviewBitmap!!,
                title = fullPreviewTitle,
                onDismiss = { fullPreviewBitmap = null }
            )
        }
    }
}

@Composable
private fun SelectedPhotoInspector(
    photos: List<Bitmap>,
    validIdx: Int,
    currentPhoto: Bitmap?,
    isCompact: Boolean = false,
    onSelectIdx: (Int) -> Unit,
    onPhotoClick: (Bitmap) -> Unit,
    onRetakeSlot: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CardCream,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 3.dp,
        border = BorderStroke(1.5.dp, BorderWarm),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RetroCardBrush)
                .padding(if (isCompact) 10.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[ FRAME 0${validIdx + 1} OF 0${photos.size} ]",
                    color = Espresso,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (isCompact) 11.sp else 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                FilledTonalButton(
                    onClick = { onRetakeSlot(validIdx) },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SoftPeach,
                        contentColor = DeepVermilion
                    ),
                    contentPadding = PaddingValues(horizontal = if (isCompact) 10.dp else 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isCompact) "Retake ↺" else "Retake Pose ${validIdx + 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceSand)
                    .border(1.dp, BorderWarm, RoundedCornerShape(16.dp))
                    .clickable(enabled = currentPhoto != null) {
                        currentPhoto?.let { onPhotoClick(it) }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (currentPhoto != null) {
                    Image(
                        bitmap = currentPhoto.asImageBitmap(),
                        contentDescription = "Selected Shot",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        color = Espresso.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Full Page View", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(photos) { idx, bmp ->
                    val isSelected = idx == validIdx
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Terracotta else BorderWarm,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectIdx(idx) }
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Thumb ${idx + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StripPreviewCard(
    stripBitmap: Bitmap?,
    onStripClick: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ANALOG STRIP PREVIEW",
            color = MutedCocoa,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            color = CardCream,
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 4.dp,
            border = BorderStroke(1.5.dp, BorderWarm),
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = stripBitmap != null) {
                    stripBitmap?.let { onStripClick(it) }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RetroCardBrush)
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (stripBitmap != null) {
                    Image(
                        bitmap = stripBitmap.asImageBitmap(),
                        contentDescription = "Photostrip Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        color = Espresso.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Text("Full Page View", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    CircularProgressIndicator(color = Terracotta, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun FullScreenPreviewDialog(
    bitmap: Bitmap,
    title: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable { onDismiss() }
                .systemBarsPadding()
                .padding(16.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = CardCream.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, BorderWarm)
                ) {
                    Text(
                        text = title,
                        color = Espresso,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }

                Surface(
                    color = Terracotta,
                    shape = RoundedCornerShape(50),
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable { onDismiss() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Close", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Image Content
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp, bottom = 44.dp)
                    .align(Alignment.Center)
            )

            // Bottom Tap anywhere hint
            Surface(
                color = CardCream.copy(alpha = 0.88f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            ) {
                Text(
                    text = "Tap anywhere to close ✦",
                    color = MutedCocoa,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun ProcessingView(
    stage: Int,
    detail: String
) {
    val steps = listOf(
        "🖼️ Developing your retro photo strip",
        "🎞️ Stitching animated celebration GIF",
        "☁️ Archiving to cloud gallery",
        "📱 Generating instant QR download link"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroCanvasBrush)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val isCompact = maxWidth < 600.dp
        Card(
            colors = CardDefaults.cardColors(containerColor = CardCream),
            shape = RoundedCornerShape(26.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            border = BorderStroke(1.5.dp, BorderWarm),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .background(RetroCardBrush)
                    .padding(if (isCompact) 20.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_mingle_booth),
                    contentDescription = "Mingle Booth",
                    modifier = Modifier
                        .height(if (isCompact) 65.dp else 100.dp)
                        .widthIn(max = if (isCompact) 140.dp else 180.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(
                    color = Terracotta,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(if (isCompact) 30.dp else 36.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Developing in the Darkroom…",
                    style = TextStyle(
                        brush = RetroTitleBrush,
                        fontFamily = FontFamily.Serif,
                        fontSize = if (isCompact) 20.sp else 24.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail.ifBlank { "Hold tight while we craft your retro keepsake ✦" },
                    color = MutedCocoa,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = if (isCompact) 12.sp else 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(if (isCompact) 16.dp else 22.dp))

                Column(verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 10.dp)) {
                    steps.forEachIndexed { idx, label ->
                        val isDone = idx < stage
                        val isCurrent = idx == stage
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = when {
                                isDone -> SageGreen.copy(alpha = 0.12f)
                                isCurrent -> SoftPeach
                                else -> SurfaceSand.copy(alpha = 0.55f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isDone -> SageGreen.copy(alpha = 0.45f)
                                    isCurrent -> Terracotta.copy(alpha = 0.55f)
                                    else -> BorderWarm
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = if (isCompact) 12.dp else 14.dp,
                                    vertical = if (isCompact) 10.dp else 12.dp
                                ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    color = if (isDone || isCurrent) Espresso else MutedCocoa,
                                    fontSize = if (isCompact) 11.sp else 13.sp,
                                    fontWeight = if (isDone || isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                if (isDone) {
                                    Text(
                                        "DONE ✓",
                                        color = SageGreen,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = if (isCompact) 10.sp else 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                } else if (isCurrent) {
                                    Text(
                                        "ACTIVE…",
                                        color = Terracotta,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = if (isCompact) 10.sp else 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultView(
    result: BoothResult,
    settings: BoothSettings,
    capturedPhotos: List<Bitmap>,
    onRetake: () -> Unit,
    onHome: () -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current

    var animIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(capturedPhotos) {
        if (capturedPhotos.isNotEmpty()) {
            while (true) {
                delay(700)
                animIdx = (animIdx + 1) % capturedPhotos.size
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroCanvasBrush)
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        val isLandscape = maxWidth > maxHeight
        val isCompact = maxWidth < 600.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Your Retro Keepsake is Ready!",
                    style = TextStyle(
                        brush = RetroTitleBrush,
                        fontFamily = FontFamily.Serif,
                        fontSize = if (isCompact) 20.sp else 28.sp,
                        fontWeight = FontWeight.Black
                    )
                )
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Column 1: Photo Strip
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "[ PHOTO STRIP ]",
                            color = MutedCocoa,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = CardCream,
                            shape = RoundedCornerShape(22.dp),
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.5.dp, BorderWarm),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Image(
                                bitmap = result.stripBitmap.asImageBitmap(),
                                contentDescription = "Final Strip",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(RetroCardBrush)
                                    .padding(10.dp)
                            )
                        }
                    }

                    // Column 2: Center Hero QR Code Card
                    QrHeroCard(
                        result = result,
                        isCompact = isCompact,
                        onSaveGallery = {
                            val ok = BoothUtils.saveBitmapToGallery(
                                context,
                                result.stripBitmap,
                                "MingleBooth_${result.sessionId}.jpg"
                            )
                            onToast(if (ok) "Saved strip to Pictures/MingleBooth!" else "Failed to save to gallery")
                        },
                        onShare = {
                            BoothUtils.shareBitmap(context, result.stripBitmap, settings.eventTitle, result.viewerUrl)
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Column 3: Animated GIF Preview
                    if (capturedPhotos.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "[ MOTION GIF ]",
                                color = MutedCocoa,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                color = CardCream,
                                shape = RoundedCornerShape(22.dp),
                                shadowElevation = 4.dp,
                                border = BorderStroke(1.5.dp, SunsetCoral.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .aspectRatio(4f / 3f)
                                    .fillMaxWidth()
                            ) {
                                Image(
                                    bitmap = capturedPhotos[animIdx % capturedPhotos.size].asImageBitmap(),
                                    contentDescription = "Animated GIF Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            }
                        }
                    }
                }
            } else {
                // Portrait scrollable layout
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    QrHeroCard(
                        result = result,
                        isCompact = isCompact,
                        onSaveGallery = {
                            val ok = BoothUtils.saveBitmapToGallery(
                                context,
                                result.stripBitmap,
                                "MingleBooth_${result.sessionId}.jpg"
                            )
                            onToast(if (ok) "Saved strip to Pictures/MingleBooth!" else "Failed to save to gallery")
                        },
                        onShare = {
                            BoothUtils.shareBitmap(context, result.stripBitmap, settings.eventTitle, result.viewerUrl)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isCompact) 210.dp else 260.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = CardCream,
                            shape = RoundedCornerShape(20.dp),
                            shadowElevation = 3.dp,
                            border = BorderStroke(1.dp, BorderWarm),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Image(
                                bitmap = result.stripBitmap.asImageBitmap(),
                                contentDescription = "Photo Strip",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }

                        if (capturedPhotos.isNotEmpty()) {
                            Surface(
                                color = CardCream,
                                shape = RoundedCornerShape(20.dp),
                                shadowElevation = 3.dp,
                                border = BorderStroke(1.5.dp, SunsetCoral.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Image(
                                    bitmap = capturedPhotos[animIdx % capturedPhotos.size].asImageBitmap(),
                                    contentDescription = "Animated GIF",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Footer Buttons
            Row(
                modifier = if (isCompact) Modifier.fillMaxWidth() else Modifier,
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = (if (isCompact) Modifier.weight(1.3f) else Modifier)
                        .shadow(6.dp, RoundedCornerShape(50), spotColor = Terracotta)
                        .clip(RoundedCornerShape(50))
                        .background(RetroButtonBrush)
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable { onRetake() }
                        .padding(horizontal = if (isCompact) 14.dp else 26.dp, vertical = if (isCompact) 13.dp else 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isCompact) "MORE PHOTOS 📸" else "TAKE MORE PHOTOS 📸",
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        fontSize = if (isCompact) 12.sp else 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedButton(
                    onClick = onHome,
                    modifier = (if (isCompact) Modifier.weight(0.9f) else Modifier).defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.5.dp, BorderWarm),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardCream.copy(alpha = 0.85f),
                        contentColor = Espresso
                    ),
                    contentPadding = PaddingValues(horizontal = if (isCompact) 10.dp else 22.dp, vertical = if (isCompact) 13.dp else 14.dp)
                ) {
                    Text(
                        if (isCompact) "Done ✦" else "Done • Next Guest",
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isCompact) 12.sp else 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun QrHeroCard(
    result: BoothResult,
    isCompact: Boolean = false,
    onSaveGallery: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardCream),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = BorderStroke(
            1.5.dp,
            Brush.linearGradient(listOf(Terracotta.copy(alpha = 0.55f), SunsetCoral.copy(alpha = 0.35f)))
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetroCardBrush)
                .padding(if (isCompact) 14.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_mingle_booth),
                contentDescription = null,
                modifier = Modifier
                    .height(if (isCompact) 38.dp else 48.dp)
                    .widthIn(max = if (isCompact) 90.dp else 110.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "Scan for Instant Keepsake",
                style = TextStyle(
                    brush = RetroTitleBrush,
                    fontFamily = FontFamily.Serif,
                    fontSize = if (isCompact) 16.sp else 18.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(8.dp))

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, BorderWarm),
                modifier = Modifier.size(if (isCompact) 140.dp else 170.dp)
            ) {
                Image(
                    bitmap = result.qrBitmap.asImageBitmap(),
                    contentDescription = "QR Code for ${result.viewerUrl}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isCompact) 8.dp else 10.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = result.viewerUrl,
                color = Terracotta,
                fontFamily = FontFamily.Monospace,
                fontSize = if (isCompact) 9.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = onSaveGallery,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SoftPeach,
                        contentColor = Espresso
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Terracotta, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SoftPeach,
                        contentColor = Espresso
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Terracotta, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
