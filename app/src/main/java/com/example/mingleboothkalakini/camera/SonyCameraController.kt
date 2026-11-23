package com.example.mingleboothkalakini.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.usb.UsbManager
import android.media.ExifInterface
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
import java.io.ByteArrayInputStream
import java.io.IOException

sealed class SonyCameraState {
    object NoCamera : SonyCameraState()
    object NeedsPermission : SonyCameraState()
    object WrongMode : SonyCameraState()
    object Connecting : SonyCameraState()
    data class Ready(val model: String) : SonyCameraState()
    data class Error(val message: String) : SonyCameraState()
}

/**
 * High-level manager for the Sony camera. Manages the USB lifecycle, streams live-
 * view frames, and takes full-resolution photos with external flash sync.
 *
 * Designed to stay connected across screen transitions (such as retakes or reviews).
 */
class SonyCameraController private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<SonyCameraState>(SonyCameraState.NoCamera)
    val state: StateFlow<SonyCameraState> = _state

    private val _liveFrame = MutableStateFlow<Bitmap?>(null)
    val liveFrame: StateFlow<Bitmap?> = _liveFrame

    private val _capturedPhotoEvent = MutableSharedFlow<Bitmap>(extraBufferCapacity = 2)
    val capturedPhotoEvent: SharedFlow<Bitmap> = _capturedPhotoEvent.asSharedFlow()

    @Volatile private var autoCaptureEnabled: Boolean = false
    @Volatile private var currentBaselineMeta: SonyCameraSession.ObjectMeta? = null
    @Volatile private var mirrorCapturedPhotos: Boolean = false

    private val _isLiveViewEnabled = MutableStateFlow(true)

    private val ioMutex = Mutex() // live view and capture share one USB pipe
    @Volatile private var session: SonyCameraSession? = null
    private var job: Job? = null
    private var lastPermissionRequest = 0L

    /** Starts the continuous connection loop. Once connected, stays connected. */
    fun start() {
        Log.d(TAG, "SonyCameraController.start() called")
        _isLiveViewEnabled.value = true
        if (job?.isActive == true) return
        job = scope.launch { runLoop() }
    }

    /** Enables or pauses live preview polling without disconnecting the camera. */
    fun setLiveViewEnabled(enabled: Boolean) {
        Log.d(TAG, "setLiveViewEnabled: $enabled")
        _isLiveViewEnabled.value = enabled
        if (!enabled) {
            _liveFrame.value = null
        }
    }

    /** Pauses live preview (does not tear down USB connection, so retakes remain fast). */
    fun stop() {
        setLiveViewEnabled(false)
    }

    /** Completely shuts down the controller and closes the USB session (e.g. on app exit). */
    fun shutdown() {
        Log.d(TAG, "SonyCameraController.shutdown() called")
        _isLiveViewEnabled.value = false
        job?.cancel()
        job = null
        _liveFrame.value = null
        scope.launch {
            ioMutex.withLock {
                session?.close()
                session = null
            }
        }
    }

    /** Enables automatic detection of physical camera trigger presses during live view. */
    fun enableAutoCapture(mirror: Boolean) {
        Log.d(TAG, "enableAutoCapture: true (mirror=$mirror)")
        mirrorCapturedPhotos = mirror
        scope.launch {
            ioMutex.withLock {
                currentBaselineMeta = session?.sampleBaselineMeta()
                Log.d(TAG, "Initial baseline for auto-capture: $currentBaselineMeta")
            }
            autoCaptureEnabled = true
        }
    }

    /** Disables auto-capture detection (e.g. when exiting Capture screen). */
    fun disableAutoCapture() {
        Log.d(TAG, "disableAutoCapture")
        autoCaptureEnabled = false
    }

    /**
     * Takes a full-resolution photo with the camera (firing the external flash).
     * @param mirror flips the result horizontally (booth "mirror result" setting)
     * @throws IOException with a user-presentable message on failure
     */
    suspend fun capture(mirror: Boolean): Bitmap = withContext(Dispatchers.IO) {
        Log.d(TAG, "capture() called (mirror=$mirror)")
        val s = session ?: throw IOException("Camera is not connected")
        val jpeg = ioMutex.withLock {
            val data = s.capturePhoto()
            currentBaselineMeta = s.sampleBaselineMeta()
            data
        }
        Log.d(TAG, "Captured photo downloaded, size=${jpeg.size} bytes. Decoding...")
        decodeJpeg(jpeg, mirror)
    }

    // ---------------------------------------------------------------- connection loop

    private suspend fun runLoop() {
        Log.d(TAG, "runLoop started")
        while (currentCoroutineContext().isActive) {
            val device = BoothUtils.findConnectedSonyCamera(appContext)
            when {
                device == null -> {
                    _state.value = SonyCameraState.NoCamera
                    _liveFrame.value = null
                    delay(1000)
                    continue
                }
                !usbManager.hasPermission(device) -> {
                    _state.value = SonyCameraState.NeedsPermission
                    val now = System.currentTimeMillis()
                    if (now - lastPermissionRequest > PERMISSION_REPROMPT_MS) {
                        lastPermissionRequest = now
                        Log.d(TAG, "Requesting USB permission for ${device.deviceName}")
                        BoothUtils.requestSonyUsbPermission(appContext)
                    }
                    delay(1000)
                    continue
                }
                PtpUsbTransport.locate(device) == null -> {
                    Log.w(TAG, "Device found but no PTP interface (wrong mode?)")
                    _state.value = SonyCameraState.WrongMode
                    delay(1500)
                    continue
                }
            }

            _state.value = SonyCameraState.Connecting
            Log.d(TAG, "Attempting connection to ${device.deviceName} (VID=0x${device.vendorId.toString(16)}, PID=0x${device.productId.toString(16)})")
            try {
                deviceLock.withLock {
                    val transport = PtpUsbTransport.open(usbManager, device)
                    val s = SonyCameraSession(transport)
                    try {
                        s.connect()
                        session = s
                        Log.i(TAG, "Sony session connected! Model: ${s.modelName}")
                        _state.value = SonyCameraState.Ready(s.modelName)
                        liveViewLoop(s)
                    } finally {
                        withContext(NonCancellable) {
                            ioMutex.withLock {
                                session = null
                                s.close()
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "runLoop cancelled")
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Connection failed: ${e.message}", e)
                _state.value = SonyCameraState.Error(e.message ?: e.javaClass.simpleName)
            }
            delay(RECONNECT_DELAY_MS)
        }
    }

    private suspend fun liveViewLoop(s: SonyCameraSession) {
        var frameCount = 0
        var consecutiveErrors = 0
        Log.d(TAG, "liveViewLoop started")
        while (currentCoroutineContext().isActive) {
            // Check if user pressed physical camera trigger / wireless remote on camera
            if (autoCaptureEnabled) {
                val newMeta = try {
                    ioMutex.withLock { s.checkNewPhotoAvailable(currentBaselineMeta) }
                } catch (_: Exception) {
                    null
                }

                if (newMeta != null) {
                    Log.i(TAG, "Physical camera trigger detected! Downloading: $newMeta")
                    try {
                        val jpeg = ioMutex.withLock { s.downloadPhoto() }
                        currentBaselineMeta = ioMutex.withLock { s.sampleBaselineMeta() } ?: newMeta
                        val bmp = decodeJpeg(jpeg, mirrorCapturedPhotos)
                        _capturedPhotoEvent.emit(bmp)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed downloading photo after camera trigger: ${e.message}", e)
                    }
                    delay(300)
                    continue
                }
            }

            // If USB live view is disabled (e.g. HDMI card provides video feed), sleep briefly
            if (!_isLiveViewEnabled.value) {
                delay(100)
                continue
            }

            val jpeg = try {
                ioMutex.withLock { s.fetchLiveViewJpeg() }
            } catch (e: Exception) {
                consecutiveErrors++
                Log.w(TAG, "fetchLiveViewJpeg error: ${e.message} (streak=$consecutiveErrors)")
                if (consecutiveErrors > 3) throw e
                delay(100)
                null
            }

            if (jpeg == null) {
                delay(40)
                continue
            }

            consecutiveErrors = 0
            val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            if (bmp != null) {
                _liveFrame.value = bmp
                frameCount++
                if (frameCount == 1 || frameCount % 120 == 0) {
                    Log.d(TAG, "Live preview frame #$frameCount (${bmp.width}x${bmp.height})")
                }
            }
            delay(33) // ~30 fps cap for USB bandwidth stability
        }
    }

    // ---------------------------------------------------------------- decoding

    private fun decodeJpeg(bytes: ByteArray, mirror: Boolean): Bitmap {
        var bmp = decodeWithOomFallback(bytes)
            ?: throw IOException("The camera returned an unreadable image")

        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        }
        if (mirror) m.postScale(-1f, 1f)

        if (!m.isIdentity) {
            val transformed = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (transformed !== bmp) bmp.recycle()
            bmp = transformed
        }
        return bmp
    }

    /** Full resolution first; halves the size only if the device runs out of memory. */
    private fun decodeWithOomFallback(bytes: ByteArray): Bitmap? {
        var sample = 1
        while (sample <= 4) {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            } catch (_: OutOfMemoryError) {
                sample *= 2
            }
        }
        return null
    }

    companion object {
        private const val TAG = "SonyPTP"

        @Volatile private var instance: SonyCameraController? = null

        fun getInstance(context: Context): SonyCameraController {
            return instance ?: synchronized(this) {
                instance ?: SonyCameraController(context.applicationContext).also { instance = it }
            }
        }

        /** Only one controller may talk to the camera at a time. */
        private val deviceLock = Mutex()

        private const val PERMISSION_REPROMPT_MS = 10_000L
        private const val RECONNECT_DELAY_MS = 1500L
    }
}
