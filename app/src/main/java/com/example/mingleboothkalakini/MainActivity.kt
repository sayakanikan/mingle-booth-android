package com.example.mingleboothkalakini

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import com.example.mingleboothkalakini.camera.HdmiCaptureController
import com.example.mingleboothkalakini.camera.SonyCameraController
import com.example.mingleboothkalakini.ui.*
import com.example.mingleboothkalakini.util.BoothUtils

class MainActivity : ComponentActivity() {

    private val viewModel: MingleViewModel by viewModels()
    private var usbPromptedFor: String? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("MingleUSB", "Camera permission result: granted=$isGranted")
        if (isGranted) {
            promptUsbPermissions()
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.example.mingleboothkalakini.USB_PERMISSION" -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.d("MingleUSB", "USB permission broadcast received: granted=$granted")
                    if (granted) {
                        SonyCameraController.getInstance(context).start()
                        HdmiCaptureController.getInstance(context).start()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d("MingleUSB", "USB device attached broadcast received")
                    usbPromptedFor = null
                    promptUsbPermissions()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d("MingleUSB", "USB device detached broadcast received")
                    usbPromptedFor = null
                }
            }
        }
    }

    // Ask for USB permission up-front (Sony camera or HDMI capture card)
    private fun promptUsbPermissions() {
        val sonyDev = BoothUtils.findConnectedSonyCamera(this)
        val hdmiDev = BoothUtils.findConnectedHdmiCaptureDevice(this)

        if (sonyDev != null) {
            if (!BoothUtils.hasSonyUsbPermission(this) && usbPromptedFor != sonyDev.deviceName) {
                usbPromptedFor = sonyDev.deviceName
                BoothUtils.requestSonyUsbPermission(this)
            } else if (BoothUtils.hasSonyUsbPermission(this)) {
                SonyCameraController.getInstance(this).start()
            }
        }

        if (hdmiDev != null) {
            if (!BoothUtils.hasHdmiCaptureUsbPermission(this) && usbPromptedFor != hdmiDev.deviceName) {
                usbPromptedFor = hdmiDev.deviceName
                BoothUtils.requestHdmiCaptureUsbPermission(this)
            } else if (BoothUtils.hasHdmiCaptureUsbPermission(this)) {
                HdmiCaptureController.getInstance(this).start()
            }
        }

        if (sonyDev == null && hdmiDev == null) {
            usbPromptedFor = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(usbReceiver) }
        SonyCameraController.getInstance(this).shutdown()
        HdmiCaptureController.getInstance(this).shutdown()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        usbPromptedFor = null
        promptUsbPermissions()
    }

    override fun onResume() {
        super.onResume()
        promptUsbPermissions()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_CAMERA ||
            keyCode == KeyEvent.KEYCODE_SPACE ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            keyCode == KeyEvent.KEYCODE_BUTTON_R1
        ) {
            if (viewModel.step.value == BoothStep.CAPTURE) {
                viewModel.triggerExternalShutter()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filter = IntentFilter("com.example.mingleboothkalakini.USB_PERMISSION").apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            promptUsbPermissions()
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Terracotta,
                    onPrimary = Color.White,
                    secondary = DustyRose,
                    onSecondary = Espresso,
                    tertiary = SageGreen,
                    background = WarmSand,
                    onBackground = Espresso,
                    surface = CardCream,
                    onSurface = Espresso,
                    surfaceVariant = SurfaceSand,
                    onSurfaceVariant = MutedCocoa
                )
            ) {
                MingleBoothApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MingleBoothApp(viewModel: MingleViewModel) {
    val context = LocalContext.current
    val step by viewModel.step.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val capturedPhotos by viewModel.capturedPhotos.collectAsState()
    val retakeSlotIndex by viewModel.retakeSlotIndex.collectAsState()
    val cachedFrameBitmap by viewModel.cachedFrameBitmap.collectAsState()
    val processingStage by viewModel.processingStage.collectAsState()
    val processingDetail by viewModel.processingDetail.collectAsState()
    val boothResult by viewModel.boothResult.collectAsState()
    val events by viewModel.events.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val adminBusy by viewModel.adminBusy.collectAsState()
    val adminStatusMessage by viewModel.adminStatusMessage.collectAsState()
    val batchProgress by viewModel.batchRegenerateState.collectAsState()
    val lastModifiedSlot by viewModel.lastModifiedSlot.collectAsState()
    val photosRevision by viewModel.photosRevision.collectAsState()

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    BackHandler(enabled = step != BoothStep.SCREENSAVER && step != BoothStep.PROCESSING) {
        when (step) {
            BoothStep.CAPTURE -> viewModel.cancelCapture()
            BoothStep.REVIEW -> viewModel.returnToScreensaver()
            BoothStep.RESULT -> viewModel.returnToScreensaver()
            BoothStep.ADMIN_LOGIN -> viewModel.returnToScreensaver()
            BoothStep.ADMIN_DASHBOARD -> viewModel.returnToScreensaver()
            else -> {}
        }
    }

    when (step) {
        BoothStep.SCREENSAVER -> ScreenSaverView(
            settings = settings,
            isLoading = isLoading,
            onStart = { viewModel.startBoothCapture() },
            onOpenAdmin = { viewModel.openAdmin() },
            onRefresh = { viewModel.refreshBoothSettings() }
        )

        BoothStep.CAPTURE -> CaptureView(
            settings = settings,
            retakeSlotIndex = retakeSlotIndex,
            existingPhotos = capturedPhotos,
            externalShutterTrigger = viewModel.externalShutterTrigger,
            onComplete = { photos -> viewModel.onPhotosCaptured(photos) },
            onCancel = { viewModel.cancelCapture() }
        )

        BoothStep.REVIEW -> ReviewView(
            photos = capturedPhotos,
            settings = settings,
            cachedFrameBitmap = cachedFrameBitmap,
            initialSlotIndex = lastModifiedSlot,
            photosRevision = photosRevision,
            onRetakeSlot = { slotIdx -> viewModel.retakeSingleSlot(slotIdx) },
            onConfirm = { viewModel.confirmAndProcessPhotos() },
            onRestartAll = { viewModel.restartAllCaptures() }
        )

        BoothStep.PROCESSING -> ProcessingView(
            stage = processingStage,
            detail = processingDetail
        )

        BoothStep.RESULT -> {
            val res = boothResult
            if (res != null) {
                ResultView(
                    result = res,
                    settings = settings,
                    capturedPhotos = capturedPhotos,
                    onRetake = { viewModel.restartAllCaptures() },
                    onHome = { viewModel.returnToScreensaver() },
                    onToast = { msg -> viewModel.showToast(msg) }
                )
            }
        }

        BoothStep.ADMIN_LOGIN -> AdminLoginScreen(
            isBusy = adminBusy,
            onLogin = { pass -> viewModel.loginAdmin(pass) },
            onBackToBooth = { viewModel.returnToScreensaver() }
        )

        BoothStep.ADMIN_DASHBOARD -> AdminDashboardScreen(
            settings = settings,
            events = events,
            sessions = sessions,
            isBusy = adminBusy,
            adminStatusMessage = adminStatusMessage,
            batchProgress = batchProgress,
            onCreateEvent = { t, s, a -> viewModel.createNewEvent(t, s, a) },
            onUpdateEvent = { id, t, s, a -> viewModel.updateExistingEvent(id, t, s, a) },
            onActivateEvent = { id -> viewModel.setActiveEvent(id) },
            onDeleteEvent = { id -> viewModel.removeEvent(id) },
            onActivateFrame = { id -> viewModel.setActiveFrame(id) },
            onAddTemplateFrame = { type -> viewModel.addBuiltInFrameTemplate(type) },
            onUploadCustomFrame = { name, bmp, slots -> viewModel.uploadCustomFrame(name, bmp, slots) },
            onUpdateFrameSlots = { id, slots, w, h -> viewModel.updateFrameSlots(id, slots, w, h) },
            onClearActiveFrame = { viewModel.clearActiveFrame() },
            onDeleteFrame = { id -> viewModel.deleteFrameItem(id) },
            onSaveCameraAndBranding = { facing, res, mirCam, mirRes, bg, txt, showT, showD, saveLocal ->
                viewModel.saveCameraAndBranding(facing, res, mirCam, mirRes, bg, txt, showT, showD, saveLocal)
            },
            onDeleteSession = { sess -> viewModel.deleteSessionItem(sess) },
            onRegenerateSession = { sess -> viewModel.regenerateSessionItem(sess) },
            onRegenerateAllEventPhotos = { id, title -> viewModel.regenerateAllEventSessions(id, title) },
            onCancelBatchRegeneration = { viewModel.cancelBatchRegeneration() },
            onFilterGalleryByEvent = { evId -> viewModel.loadAdminData(evId) },
            onReturnToBooth = { viewModel.returnToScreensaver() },
            onLogout = { viewModel.logoutAdmin() }
        )
    }
}
