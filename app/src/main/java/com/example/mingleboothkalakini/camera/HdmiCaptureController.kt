package com.example.mingleboothkalakini.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.example.mingleboothkalakini.util.BoothUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

sealed class HdmiCaptureState {
    object NoDevice : HdmiCaptureState()
    object NeedsPermission : HdmiCaptureState()
    object Connecting : HdmiCaptureState()
    data class Ready(val name: String) : HdmiCaptureState()
    data class Error(val message: String) : HdmiCaptureState()
}

/**
 * Controller for HDMI Capture cards (e.g. Sony a6000/a6400 via MacroSilicon MS2109 or generic UVC cards).
 * Operates purely through Android's USB Host API, bypassing non-existent Camera2 external HALs on tablets.
 */
class HdmiCaptureController private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<HdmiCaptureState>(HdmiCaptureState.NoDevice)
    val state: StateFlow<HdmiCaptureState> = _state

    private val _liveFrame = MutableStateFlow<Bitmap?>(null)
    val liveFrame: StateFlow<Bitmap?> = _liveFrame

    private val _capturedPhotoEvent = MutableSharedFlow<Bitmap>(extraBufferCapacity = 2)
    val capturedPhotoEvent: SharedFlow<Bitmap> = _capturedPhotoEvent.asSharedFlow()

    private val _isLiveViewEnabled = MutableStateFlow(true)
    private val ioMutex = Mutex()

    @Volatile
    private var session: HdmiCaptureSession? = null
    private var loopJob: Job? = null
    private var lastPermissionPrompt = 0L

    fun start() {
        Log.d(TAG, "HdmiCaptureController.start() called")
        _isLiveViewEnabled.value = true
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { runLoop() }
    }

    fun setLiveViewEnabled(enabled: Boolean) {
        Log.d(TAG, "setLiveViewEnabled: $enabled")
        _isLiveViewEnabled.value = enabled
        if (!enabled) {
            _liveFrame.value = null
        }
    }

    fun stop() {
        setLiveViewEnabled(false)
    }

    fun shutdown() {
        Log.d(TAG, "shutdown() called")
        _isLiveViewEnabled.value = false
        loopJob?.cancel()
        loopJob = null
        _liveFrame.value = null
        scope.launch {
            ioMutex.withLock {
                session?.close()
                session = null
            }
        }
    }

    /**
     * Captures the current high-resolution frame from the HDMI live stream.
     */
    suspend fun takePhoto(mirror: Boolean): Bitmap = withContext(Dispatchers.Default) {
        // Wait for an active frame if one isn't currently buffered
        var frame = _liveFrame.value
        val startTime = System.currentTimeMillis()
        while (frame == null && System.currentTimeMillis() - startTime < 2500) {
            delay(50)
            frame = _liveFrame.value
        }

        val rawBmp = frame ?: throw IOException("No live frame available from HDMI capture card")

        if (mirror) {
            val matrix = Matrix().apply { postScale(-1f, 1f) }
            Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
        } else {
            rawBmp.copy(rawBmp.config ?: Bitmap.Config.ARGB_8888, false)
        }
    }

    private suspend fun runLoop() {
        while (scope.isActive) {
            val device = BoothUtils.findConnectedHdmiCaptureDevice(appContext)

            if (device == null) {
                _state.value = HdmiCaptureState.NoDevice
                _liveFrame.value = null
                delay(1000)
                continue
            }

            if (!usbManager.hasPermission(device)) {
                _state.value = HdmiCaptureState.NeedsPermission
                _liveFrame.value = null
                val now = System.currentTimeMillis()
                if (now - lastPermissionPrompt > 3000) {
                    lastPermissionPrompt = now
                    withContext(Dispatchers.Main) {
                        BoothUtils.requestHdmiCaptureUsbPermission(appContext)
                    }
                }
                delay(1000)
                continue
            }

            _state.value = HdmiCaptureState.Connecting

            try {
                connectAndStream(device)
            } catch (e: Exception) {
                Log.e(TAG, "HDMI capture session error: ${e.message}", e)
                _state.value = HdmiCaptureState.Error(e.localizedMessage ?: "Connection error")
                _liveFrame.value = null
                delay(1500)
            }
        }
    }

    private suspend fun connectAndStream(device: UsbDevice) = withContext(Dispatchers.IO) {
        val conn = usbManager.openDevice(device) ?: throw IOException("Failed to open USB device connection")
        val devName = device.productName?.takeIf { it.isNotBlank() } ?: "HDMI Video Capture"

        ioMutex.withLock {
            session?.close()
            session = HdmiCaptureSession(conn, device)
        }

        _state.value = HdmiCaptureState.Ready(devName)
        Log.i(TAG, "HDMI capture stream active: $devName")

        try {
            session?.startStreaming(1920, 1080) { frameBitmap ->
                if (_isLiveViewEnabled.value) {
                    _liveFrame.value = frameBitmap
                }
            }
        } finally {
            ioMutex.withLock {
                session?.close()
                session = null
            }
        }
    }

    companion object {
        private const val TAG = "HdmiCaptureController"

        @Volatile
        private var instance: HdmiCaptureController? = null

        fun getInstance(context: Context): HdmiCaptureController =
            instance ?: synchronized(this) {
                instance ?: HdmiCaptureController(context.applicationContext).also { instance = it }
            }
    }
}
