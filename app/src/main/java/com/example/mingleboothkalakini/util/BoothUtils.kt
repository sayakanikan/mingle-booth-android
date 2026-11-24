package com.example.mingleboothkalakini.util

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.FileProvider
import com.example.mingleboothkalakini.data.BoothSettings
import com.example.mingleboothkalakini.data.CameraDeviceOption
import com.example.mingleboothkalakini.data.RemoteRepository
import com.example.mingleboothkalakini.data.Slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

object BoothUtils {

    private val audioScope = CoroutineScope(Dispatchers.Default)

    fun playCountdownBeep() {
        playToneAsync(freq = 1046.0, durationMs = 80, volume = 0.5f)
    }

    fun playGoBeep() {
        audioScope.launch {
            playToneBlocking(1318.0, 60, 0.5f)
            delay(20)
            playToneBlocking(1568.0, 100, 0.5f)
        }
    }

    fun playShutter() {
        audioScope.launch {
            try {
                val sampleRate = 44100
                val numSamples = (sampleRate * 0.08).toInt()
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val env = exp(-i / (sampleRate * 0.015))
                    val noise = (Random.nextDouble() * 2.0 - 1.0) * env * 0.7
                    samples[i] = (noise * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                playPcmBlocking(samples, sampleRate)
            } catch (_: Exception) {
            }
        }
    }

    fun playSuccess() {
        audioScope.launch {
            val notes = listOf(523.0, 659.0, 784.0, 1046.0)
            for (freq in notes) {
                playToneBlocking(freq, 130, 0.35f)
                delay(30)
            }
        }
    }

    private fun playToneAsync(freq: Double, durationMs: Int, volume: Float) {
        audioScope.launch {
            playToneBlocking(freq, durationMs, volume)
        }
    }

    private fun playToneBlocking(freq: Double, durationMs: Int, volume: Float) {
        try {
            val sampleRate = 44100
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val samples = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                val env = (1.0 - progress).coerceAtLeast(0.0)
                val v = sin(2.0 * PI * i * freq / sampleRate) * volume * env
                samples[i] = (v * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcmBlocking(samples, sampleRate)
        } catch (_: Exception) {
        }
    }

    private fun playPcmBlocking(samples: ShortArray, sampleRate: Int) {
        val byteCount = samples.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(byteCount, AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size)
        track.play()
        Thread.sleep((samples.size * 1000L / sampleRate) + 30L)
        track.release()
    }

    fun parseColorSafe(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        return try {
            Color.parseColor(hex.trim())
        } catch (_: Exception) {
            fallback
        }
    }

    fun formatAspectRatio(w: Int, h: Int): String {
        if (w <= 0 || h <= 0) return ""
        val ratio = w.toFloat() / h.toFloat()
        val label = when {
            kotlin.math.abs(ratio - 1f) < 0.04f -> "Square (1:1)"
            kotlin.math.abs(ratio - 4f / 3f) < 0.04f -> "Landscape (4:3)"
            kotlin.math.abs(ratio - 3f / 4f) < 0.04f -> "Portrait (3:4)"
            kotlin.math.abs(ratio - 16f / 9f) < 0.04f -> "Widescreen (16:9)"
            kotlin.math.abs(ratio - 9f / 16f) < 0.04f -> "Story (9:16)"
            kotlin.math.abs(ratio - 3f / 2f) < 0.04f -> "Classic (3:2)"
            else -> "Custom Crop"
        }
        return "$label · ${w}×${h}px"
    }

    /** Raw list of every USB device Android currently sees (for troubleshooting). */
    fun describeAllUsbDevices(context: Context): String {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return "USB host not available"
        val devices = usbManager.deviceList.values
        if (devices.isEmpty()) return "Android sees NO USB devices (check cable / OTG / camera power & PC Remote mode)"
        return "Android sees: " + devices.joinToString(" | ") {
            "%04X:%04X %s".format(it.vendorId, it.productId, it.productName ?: it.deviceName)
        }
    }

    fun getConnectedUsbCameraSummary(context: Context): String? {
        return try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
            val videoDevices = mutableListOf<String>()
            for (device in usbManager.deviceList.values) {
                val prod = (device.productName ?: "").trim()
                val mfg = (device.manufacturerName ?: "").trim()
                val rawName = listOf(mfg, prod).filter { it.isNotBlank() }.joinToString(" ").ifBlank { device.deviceName }
                val lower = rawName.lowercase()

                var isVideoOrSony = device.vendorId == 0x054C || // Sony Corp Vendor ID
                    lower.contains("sony") ||
                    lower.contains("ilce") ||
                    lower.contains("a6000") ||
                    lower.contains("a6400") ||
                    lower.contains("a6700") ||
                    lower.contains("cam link") ||
                    lower.contains("elgato") ||
                    lower.contains("capture") ||
                    lower.contains("hdmi") ||
                    lower.contains("usb video") ||
                    device.deviceClass == UsbConstants.USB_CLASS_VIDEO ||
                    device.deviceClass == UsbConstants.USB_CLASS_MISC

                if (!isVideoOrSony) {
                    for (i in 0 until device.interfaceCount) {
                        if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_VIDEO) {
                            isVideoOrSony = true
                            break
                        }
                    }
                }

                if (isVideoOrSony) {
                    val tag = when {
                        device.vendorId == 0x054C || lower.contains("sony") || lower.contains("ilce") ->
                            "📸 $rawName [Sony Mirrorless]"
                        lower.contains("capture") || lower.contains("cam link") || lower.contains("elgato") || lower.contains("hdmi") ->
                            "📡 $rawName [HDMI Capture Card]"
                        else -> "🎥 $rawName [USB Video]"
                    }
                    videoDevices.add(tag)
                }
            }
            videoDevices.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun getAvailableCameraOptions(context: Context): List<CameraDeviceOption> {
        val usbHint = getConnectedUsbCameraSummary(context)
        val externalLabel = if (usbHint != null) {
            "$usbHint (External USB Camera)"
        } else {
            "📸 External Camera (Auto-Detect: Sony PC Remote / HDMI UVC)"
        }

        return listOf(
            CameraDeviceOption(
                id = "FRONT",
                label = "🤳 Built-in Front Camera",
                isExternal = false
            ),
            CameraDeviceOption(
                id = "BACK",
                label = "📷 Built-in Back Camera",
                isExternal = false
            ),
            CameraDeviceOption(
                id = "EXTERNAL",
                label = externalLabel,
                isExternal = true
            )
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun resolveCameraSelector(
        cameraProvider: ProcessCameraProvider,
        cameraSetting: String
    ): CameraSelector {
        val norm = cameraSetting.trim().uppercase()

        if (norm.startsWith("ID:")) {
            val targetId = cameraSetting.substringAfter(":").trim()
            val idSelector = CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    val matched = cameraInfos.filter { info ->
                        try {
                            Camera2CameraInfo.from(info).cameraId == targetId
                        } catch (_: Exception) {
                            false
                        }
                    }
                    matched.ifEmpty { cameraInfos }
                }
                .build()
            return idSelector
        }

        if (norm == "EXTERNAL") {
            val extSelector = CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    val extMatches = cameraInfos.filter { info ->
                        try {
                            val c2 = Camera2CameraInfo.from(info)
                            val facing = c2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                            facing == CameraCharacteristics.LENS_FACING_EXTERNAL ||
                                (facing != CameraCharacteristics.LENS_FACING_FRONT && facing != CameraCharacteristics.LENS_FACING_BACK) ||
                                (c2.cameraId.toIntOrNull() ?: 0) >= 2
                        } catch (_: Exception) {
                            false
                        }
                    }
                    when {
                        extMatches.isNotEmpty() -> extMatches
                        cameraInfos.size > 2 -> listOf(cameraInfos.last())
                        else -> cameraInfos
                    }
                }
                .build()
            return extSelector
        }

        if (norm == "BACK") {
            return if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
        }

        return if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    suspend fun composeStrip(
        photos: List<Bitmap>,
        settings: BoothSettings,
        cachedFrameBitmap: Bitmap? = null,
        maxDimension: Int? = null
    ): Bitmap = withContext(Dispatchers.Default) {
        val activeFrame = settings.activeFrame
        val frameW = (activeFrame?.width ?: 1200).coerceAtLeast(400)
        val frameH = (activeFrame?.height ?: 3600).coerceAtLeast(600)
        val slots = (activeFrame?.slots ?: default3Slots()).sortedBy { it.slotIndex }

        val maxDim = max(frameW, frameH).toFloat()
        val scale = if (maxDimension != null) {
            min(1f, maxDimension.toFloat() / maxDim)
        } else {
            // Full print-grade photostrip resolution following 2R (1200x3600), 4R (2400x3600),
            // or square (2400x2400) templates while preserving natively larger frames up to 4200px.
            val ratio = frameW.toFloat() / frameH.toFloat()
            val targetMinMaxDim = if (ratio in 0.85f..1.15f) 2400f else 3600f
            val desiredScale = max(1f, targetMinMaxDim / maxDim)
            min(desiredScale, 4200f / maxDim)
        }
        val outW = (frameW * scale).roundToInt()
        val outH = (frameH * scale).roundToInt()

        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val bgColor = parseColorSafe(settings.stripBgColor, Color.parseColor("#1a1a28"))
        val textColor = parseColorSafe(settings.stripTextColor, Color.WHITE)

        canvas.drawColor(bgColor)

        for (i in slots.indices) {
            val photo = photos.getOrNull(i) ?: continue
            val s = slots[i]
            val dstRect = RectF(
                s.x * scale,
                s.y * scale,
                (s.x + s.w) * scale,
                (s.y + s.h) * scale
            )

            canvas.save()
            canvas.clipRect(dstRect)

            val pAspect = photo.width.toFloat() / photo.height.toFloat()
            val sAspect = dstRect.width() / dstRect.height()
            val srcRect = if (pAspect > sAspect) {
                val sw = (photo.height * sAspect).roundToInt()
                val sx = (photo.width - sw) / 2
                Rect(sx, 0, sx + sw, photo.height)
            } else {
                val sh = (photo.width / sAspect).roundToInt()
                val sy = (photo.height - sh) / 2
                Rect(0, sy, photo.width, sy + sh)
            }

            canvas.drawBitmap(photo, srcRect, dstRect, paint)

            val cx = dstRect.centerX()
            val cy = dstRect.centerY()
            val r = max(dstRect.width(), dstRect.height()) * 0.75f
            if (r > 0f) {
                val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        cx, cy, r,
                        intArrayOf(Color.TRANSPARENT, Color.argb(56, 0, 0, 0)),
                        floatArrayOf(0.4f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(dstRect, vignettePaint)
            }

            canvas.restore()
        }

        val frameBmp = cachedFrameBitmap ?: activeFrame?.dataUrl?.takeIf { it.isNotBlank() }?.let {
            RemoteRepository.downloadBitmap(it)
        }
        if (frameBmp != null) {
            canvas.drawBitmap(frameBmp, null, RectF(0f, 0f, outW.toFloat(), outH.toFloat()), paint)
        }

        if (settings.stripShowTitle || settings.stripShowDate) {
            val barH = max(44f, outH * if (settings.stripShowTitle && settings.stripShowDate) 0.045f else 0.03f)
            val textY = outH - barH

            val barPaint = Paint().apply {
                color = bgColor
                alpha = 185
            }
            canvas.drawRect(0f, textY, outW.toFloat(), outH.toFloat(), barPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = outW * 0.038f
            }
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(190, 255, 255, 255)
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = outW * 0.024f
            }
            val dateStr = SimpleDateFormat("d MMMM yyyy", Locale.UK).format(Date())

            if (settings.stripShowTitle && settings.stripShowDate) {
                canvas.drawText(settings.eventTitle, outW / 2f, textY + barH * 0.42f, titlePaint)
                canvas.drawText(dateStr, outW / 2f, textY + barH * 0.78f, datePaint)
            } else if (settings.stripShowTitle) {
                canvas.drawText(settings.eventTitle, outW / 2f, textY + barH * 0.62f, titlePaint)
            } else {
                canvas.drawText(dateStr, outW / 2f, textY + barH * 0.62f, datePaint)
            }
        }

        result
    }

    fun default3Slots(): List<Slot> = listOf(
        Slot(0, 100, 160, 1000, 860),
        Slot(1, 100, 1120, 1000, 860),
        Slot(2, 100, 2080, 1000, 860)
    )

    /**
     * Decodes a PNG/image Uri at its original native pixel dimensions without Android density scaling.
     */
    fun decodeBitmapFromUri(context: Context, uri: android.net.Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Automatically detects transparent photo placement cutout regions in a PNG frame overlay.
     * Uses 4-connected BFS flood-fill on pixels with alpha < 10, filters noise (< 40px) and
     * full-canvas background cutouts, and sorts slots top-to-bottom then left-to-right.
     */
    fun detectSlots(bitmap: Bitmap): List<Slot> {
        val alphaThreshold = 10
        val minSlotPx = 40
        val maxDim = 1000

        val origW = bitmap.width
        val origH = bitmap.height
        if (origW <= 0 || origH <= 0) return emptyList()

        val scale = min(1f, maxDim.toFloat() / max(origW, origH).toFloat())
        val w = (origW * scale).roundToInt().coerceAtLeast(1)
        val h = (origH * scale).roundToInt().coerceAtLeast(1)

        val workBmp = if (w != origW || h != origH) {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }

        val pixels = IntArray(w * h)
        workBmp.getPixels(pixels, 0, w, 0, 0, w, h)
        if (workBmp !== bitmap) {
            workBmp.recycle()
        }

        val transp = BooleanArray(w * h) { i ->
            ((pixels[i] ushr 24) and 0xFF) < alphaThreshold
        }
        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)

        val detected = mutableListOf<Slot>()
        val minScaledPx = max(12, (minSlotPx * scale).roundToInt())

        for (y in 0 until h) {
            for (x in 0 until w) {
                val startIdx = y * w + x
                if (!transp[startIdx] || visited[startIdx]) continue

                var head = 0
                var tail = 0
                queue[tail++] = startIdx
                visited[startIdx] = true

                var minX = x
                var maxX = x
                var minY = y
                var maxY = y

                while (head < tail) {
                    val curr = queue[head++]
                    val cy = curr / w
                    val cx = curr % w

                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy

                    if (cx > 0) {
                        val n = curr - 1
                        if (!visited[n] && transp[n]) {
                            visited[n] = true
                            queue[tail++] = n
                        }
                    }
                    if (cx < w - 1) {
                        val n = curr + 1
                        if (!visited[n] && transp[n]) {
                            visited[n] = true
                            queue[tail++] = n
                        }
                    }
                    if (cy > 0) {
                        val n = curr - w
                        if (!visited[n] && transp[n]) {
                            visited[n] = true
                            queue[tail++] = n
                        }
                    }
                    if (cy < h - 1) {
                        val n = curr + w
                        if (!visited[n] && transp[n]) {
                            visited[n] = true
                            queue[tail++] = n
                        }
                    }
                }

                val slotW = maxX - minX + 1
                val slotH = maxY - minY + 1

                if (slotW < minScaledPx || slotH < minScaledPx) continue
                if (slotW >= w * 0.96f && slotH >= h * 0.96f) continue

                val nativeX = (minX / scale).roundToInt()
                val nativeY = (minY / scale).roundToInt()
                val nativeW = min(origW - nativeX, (slotW / scale).roundToInt())
                val nativeH = min(origH - nativeY, (slotH / scale).roundToInt())

                if (nativeW >= minSlotPx && nativeH >= minSlotPx) {
                    detected.add(Slot(0, nativeX, nativeY, nativeW, nativeH))
                }
            }
        }

        val rowDiffThreshold = (minSlotPx * 0.5f).roundToInt()
        detected.sortWith { a, b ->
            val rowDiff = a.y - b.y
            if (kotlin.math.abs(rowDiff) > rowDiffThreshold) rowDiff else a.x - b.x
        }

        return detected.mapIndexed { index, slot -> slot.copy(slotIndex = index) }
    }

    fun generateDefaultFrameTemplate(
        type: String,
        title: String
    ): Triple<String, Bitmap, List<Slot>> {
        val isGrid = type == "2x2-dark"
        val w = if (isGrid) 1200 else 600
        val h = if (isGrid) 1200 else 1800

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val slots: List<Slot>
        val frameName: String
        val bgColor: Int
        val borderColor: Int
        val titleColor: Int
        val subColor: Int

        when (type) {
            "3-strip-white" -> {
                frameName = "Wedding White 3-Strip"
                bgColor = Color.parseColor("#faf8f5")
                borderColor = Color.parseColor("#e0a96d")
                titleColor = Color.parseColor("#1c1917")
                subColor = Color.parseColor("#a89f91")
                slots = listOf(
                    Slot(0, 50, 80, 500, 430),
                    Slot(1, 50, 560, 500, 430),
                    Slot(2, 50, 1040, 500, 430)
                )
            }
            "4-strip-dark" -> {
                frameName = "Neon Blue 4-Strip"
                bgColor = Color.parseColor("#10101a")
                borderColor = Color.parseColor("#38bdf8")
                titleColor = Color.WHITE
                subColor = Color.parseColor("#38bdf8")
                slots = listOf(
                    Slot(0, 50, 60, 500, 340),
                    Slot(1, 50, 440, 500, 340),
                    Slot(2, 50, 820, 500, 340),
                    Slot(3, 50, 1200, 500, 340)
                )
            }
            "2x2-dark" -> {
                frameName = "Retro 2×2 Grid"
                bgColor = Color.parseColor("#12121e")
                borderColor = Color.parseColor("#c084fc")
                titleColor = Color.WHITE
                subColor = Color.parseColor("#c084fc")
                slots = listOf(
                    Slot(0, 60, 60, 520, 480),
                    Slot(1, 620, 60, 520, 480),
                    Slot(2, 60, 580, 520, 480),
                    Slot(3, 620, 580, 520, 480)
                )
            }
            else -> {
                frameName = "Classic Dark 3-Strip"
                bgColor = Color.parseColor("#141422")
                borderColor = Color.parseColor("#c084fc")
                titleColor = Color.WHITE
                subColor = Color.parseColor("#c084fc")
                slots = listOf(
                    Slot(0, 50, 80, 500, 430),
                    Slot(1, 50, 560, 500, 430),
                    Slot(2, 50, 1040, 500, 430)
                )
            }
        }

        canvas.drawColor(bgColor)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.color = borderColor
        canvas.drawRect(18f, 18f, w - 18f, h - 18f, paint)

        val clearPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val slotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = borderColor
            alpha = 110
        }

        for (s in slots) {
            val rect = RectF(s.x.toFloat(), s.y.toFloat(), (s.x + s.w).toFloat(), (s.y + s.h).toFloat())
            canvas.drawRect(rect, clearPaint)
            canvas.drawRect(rect, slotBorderPaint)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = titleColor
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (isGrid) 34f else 26f
        }
        canvas.drawText(title.uppercase(Locale.getDefault()), w / 2f, if (isGrid) 1130f else 1630f, textPaint)

        if (!isGrid) {
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textAlign = Paint.Align.CENTER
                textSize = 15f
            }
            canvas.drawText("CAPTURE YOUR MEMORIES", w / 2f, 1665f, subPaint)
        }

        return Triple(frameName, bmp, slots)
    }

    suspend fun buildAnimatedGif(
        photos: List<Bitmap>,
        width: Int = 1280,
        height: Int = 720,
        delayMs: Int = 650
    ): ByteArray? = withContext(Dispatchers.Default) {
        if (photos.isEmpty()) return@withContext null
        try {
            val first = photos.first()
            val isLandscape = first.width >= first.height
            val (targetW, targetH) = if (width > 0 && height > 0) {
                // Respect target dimensions (default 1280x720 HD Hi-Res Composite), adapting to photo orientation
                val w = if (isLandscape) maxOf(width, height) else minOf(width, height)
                val h = if (isLandscape) minOf(width, height) else maxOf(width, height)
                val maxAllowed = 1280
                if (w > maxAllowed || h > maxAllowed) {
                    val scale = maxAllowed.toFloat() / maxOf(w, h)
                    ((w * scale).roundToInt() and 1.inv()) to ((h * scale).roundToInt() and 1.inv())
                } else {
                    (w and 1.inv()) to (h and 1.inv())
                }
            } else {
                // Standardize GIF output to 1280x720 (or 720x1280 portrait)
                if (isLandscape) 1280 to 720 else 720 to 1280
            }
            val scaledFrames = photos.map { photo ->
                centerCropBitmap(photo, targetW, targetH)
            }
            SimpleGifEncoder.encode(scaledFrames, delayMs)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun centerCropBitmap(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (src.width == targetW && src.height == targetH) return src
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val pAspect = src.width.toFloat() / src.height.toFloat()
        val tAspect = targetW.toFloat() / targetH.toFloat()
        val srcRect = if (pAspect > tAspect) {
            val sw = (src.height * tAspect).roundToInt()
            val sx = (src.width - sw) / 2
            Rect(sx, 0, sx + sw, src.height)
        } else {
            val sh = (src.width / tAspect).roundToInt()
            val sy = (src.height - sh) / 2
            Rect(0, sy, src.width, sy + sh)
        }
        canvas.drawBitmap(src, srcRect, Rect(0, 0, targetW, targetH), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    suspend fun generateQrBitmap(url: String, size: Int = 512): Bitmap = withContext(Dispatchers.Default) {
        val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=${size}x${size}&margin=10&data=${java.net.URLEncoder.encode(url, "UTF-8")}"
        val downloaded = RemoteRepository.downloadBitmap(qrUrl)
        if (downloaded != null) return@withContext downloaded

        val fallback = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(fallback)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        c.drawText("Scan at:", size / 2f, size / 2f - 20f, p)
        c.drawText(url, size / 2f, size / 2f + 20f, p)
        fallback
    }

    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        filename: String,
        subFolder: String? = null,
        quality: Int = 95
    ): Boolean {
        return try {
            val relativeDir = buildString {
                append(Environment.DIRECTORY_PICTURES)
                append("/MingleBooth")
                if (!subFolder.isNullOrBlank()) {
                    append("/${subFolder.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")}")
                }
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativeDir)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun saveBytesToGallery(
        context: Context,
        bytes: ByteArray,
        filename: String,
        mimeType: String = "image/gif",
        subFolder: String? = null
    ): Boolean {
        return try {
            val relativeDir = buildString {
                append(Environment.DIRECTORY_PICTURES)
                append("/MingleBooth")
                if (!subFolder.isNullOrBlank()) {
                    append("/${subFolder.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")}")
                }
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativeDir)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Saves a complete backup of the session assets (photostrip, animated GIF, and all original poses)
     * directly into the device's Pictures gallery (MediaStore) under Pictures/MingleBooth/{eventTitle}/.
     */
    fun saveSessionToLocalGallery(
        context: Context,
        sessionId: String,
        eventTitle: String,
        stripBitmap: Bitmap,
        gifBytes: ByteArray?,
        originalPhotos: List<Bitmap>
    ) {
        val safeTitle = eventTitle.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Event" }

        // 1. Save Photostrip
        val stripFileName = "Mingle_${safeTitle}_${sessionId}_strip.jpg"
        saveBitmapToGallery(context, stripBitmap, stripFileName, subFolder = safeTitle, quality = 95)

        // 2. Save Animated GIF
        if (gifBytes != null && gifBytes.isNotEmpty()) {
            val gifFileName = "Mingle_${safeTitle}_${sessionId}_animation.gif"
            saveBytesToGallery(context, gifBytes, gifFileName, mimeType = "image/gif", subFolder = safeTitle)
        }

        // 3. Save All Original Photo Poses
        originalPhotos.forEachIndexed { idx, poseBitmap ->
            val poseFileName = "Mingle_${safeTitle}_${sessionId}_pose_${idx + 1}.jpg"
            saveBitmapToGallery(context, poseBitmap, poseFileName, subFolder = safeTitle, quality = 92)
        }
    }

    fun shareBitmap(context: Context, bitmap: Bitmap, title: String, viewerUrl: String) {
        try {
            val cacheDir = File(context.cacheDir, "shared_strips").apply { mkdirs() }
            val file = File(cacheDir, "mingle_strip_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "$title — View & download: $viewerUrl")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Photo Strip"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Finds any connected Sony camera (a6000, a6400, a6700, etc.) in PC Remote mode (VID 0x054C).
     */
    fun findConnectedSonyCamera(context: Context): UsbDevice? {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        val devices = usbManager.deviceList.values
        return devices.firstOrNull { dev ->
            if (dev.vendorId == 0x054C) return@firstOrNull true
            val mfg = runCatching { dev.manufacturerName?.lowercase() }.getOrNull() ?: ""
            val prod = runCatching { dev.productName?.lowercase() }.getOrNull() ?: ""
            mfg.contains("sony") || prod.contains("sony") || prod.contains("ilce")
        }
    }

    /**
     * Checks if Android has USB permission for the connected Sony camera, or requests it.
     */
    fun hasSonyUsbPermission(context: Context): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        val device = findConnectedSonyCamera(context) ?: return false
        return usbManager.hasPermission(device)
    }

    fun requestSonyUsbPermission(context: Context) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
        val device = findConnectedSonyCamera(context) ?: return
        if (!usbManager.hasPermission(device)) {
            val intent = Intent("com.example.mingleboothkalakini.USB_PERMISSION").apply {
                setPackage(context.packageName)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pi = android.app.PendingIntent.getBroadcast(context, 0, intent, flags)
            usbManager.requestPermission(device, pi)
        }
    }

    /**
     * Checks if a USB device is an HDMI capture card or UVC video device.
     */
    fun isHdmiCaptureDevice(dev: UsbDevice): Boolean {
        // Exclude Sony camera in PC remote mode (VID 0x054C = 1356)
        if (dev.vendorId == 0x054C) return false

        // Known HDMI capture vendors (MacroSilicon MS2109/MS2130, Zasprec, Elgato Cam Link)
        if (dev.vendorId == 0x345F || dev.vendorId == 0x534D || dev.vendorId == 0x0FD9) return true

        // UVC device class (239/2)
        if (dev.deviceClass == 239 && dev.deviceSubclass == 2) return true

        // Video class interface (14)
        for (i in 0 until dev.interfaceCount) {
            val iface = dev.getInterface(i)
            if (iface.interfaceClass == 14) return true
        }
        return false
    }

    /**
     * Finds any connected HDMI Capture card (e.g. MacroSilicon MS2109, Zasprec, Elgato)
     */
    fun findConnectedHdmiCaptureDevice(context: Context): UsbDevice? {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        return usbManager.deviceList.values.firstOrNull { isHdmiCaptureDevice(it) }
    }

    fun hasHdmiCaptureUsbPermission(context: Context): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        val device = findConnectedHdmiCaptureDevice(context) ?: return false
        return usbManager.hasPermission(device)
    }

    fun requestHdmiCaptureUsbPermission(context: Context) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
        val device = findConnectedHdmiCaptureDevice(context) ?: return
        if (!usbManager.hasPermission(device)) {
            val intent = Intent("com.example.mingleboothkalakini.USB_PERMISSION").apply {
                setPackage(context.packageName)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pi = android.app.PendingIntent.getBroadcast(context, 0, intent, flags)
            usbManager.requestPermission(device, pi)
        }
    }

    /**
     * Triggers the Sony camera's physical shutter and flash over USB OTG using PTP protocol.
     * Compatible with Sony a6000, a6400, a6700 with USB set to "PC Remote".
     */
    suspend fun triggerSonyUsbShutter(context: Context): Boolean = withContext(Dispatchers.IO) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return@withContext false
        val device = findConnectedSonyCamera(context) ?: return@withContext false

        if (!usbManager.hasPermission(device)) {
            withContext(Dispatchers.Main) {
                requestSonyUsbPermission(context)
            }
            return@withContext false
        }

        var connection: UsbDeviceConnection? = null
        try {
            connection = usbManager.openDevice(device) ?: return@withContext false

            // Find Still Image (Class 6) or Vendor (Class 255) PTP interface with Bulk endpoints
            var targetInterface: UsbInterface? = null
            var epIn: UsbEndpoint? = null
            var epOut: UsbEndpoint? = null

            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                var foundIn: UsbEndpoint? = null
                var foundOut: UsbEndpoint? = null
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) foundIn = ep
                        else if (ep.direction == UsbConstants.USB_DIR_OUT) foundOut = ep
                    }
                }
                if (foundIn != null && foundOut != null) {
                    // Prioritize Still Imaging (Class 6) or Vendor interface
                    targetInterface = iface
                    epIn = foundIn
                    epOut = foundOut
                    if (iface.interfaceClass == 6 || iface.interfaceClass == 0xFF) {
                        break
                    }
                }
            }

            if (targetInterface == null || epIn == null || epOut == null) {
                connection.close()
                return@withContext false
            }

            connection.claimInterface(targetInterface, true)

            var txId = 1

            // Reads whatever packet arrives on epIn (data or response)
            fun readPacket(timeout: Int = 1000): ByteArray? {
                val buf = ByteArray(4096)
                val read = connection.bulkTransfer(epIn, buf, buf.size, timeout)
                return if (read >= 12) buf.copyOf(read) else null
            }

            // Sends a standard PTP Command block
            fun sendPtpCommand(opCode: Int, vararg params: Int): Boolean {
                val len = 12 + params.size * 4
                val buf = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(len)
                    putShort(0x0001.toShort()) // Container Type: Command
                    putShort(opCode.toShort())
                    putInt(txId++)
                    params.forEach { putInt(it) }
                }.array()
                val written = connection.bulkTransfer(epOut, buf, buf.size, 1000)
                if (written < len) return false

                // Drain incoming packet(s) until response block arrives (type 0x0003) or max 3 reads
                for (attempt in 0 until 3) {
                    val pkt = readPacket(800) ?: break
                    val type = ByteBuffer.wrap(pkt, 4, 2).order(ByteOrder.LITTLE_ENDIAN).short
                    if (type == 0x0003.toShort()) {
                        // Response block
                        return true
                    }
                }
                return true
            }

            // Sends Sony SDIO_ControlDevice (0x9207) with Command + Data phases
            fun sendSonyControl(propCode: Int, value: Short): Boolean {
                val currentTx = txId++

                // Phase 1: Command Container (Opcode 0x9207, Param1 = ControlCode e.g. 0xD2C1/0xD2C2)
                val cmd = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(16)
                    putShort(0x0001.toShort()) // Command Block
                    putShort(0x9207.toShort()) // SDIO_ControlDevice
                    putInt(currentTx)
                    putInt(propCode)
                }.array()
                connection.bulkTransfer(epOut, cmd, cmd.size, 1000)

                // Phase 2: Data Container (Opcode 0x9207, Data payload uint16 value: 2=press, 1=release)
                val data = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(14)
                    putShort(0x0002.toShort()) // Data Block
                    putShort(0x9207.toShort())
                    putInt(currentTx)
                    putShort(value)
                }.array()
                connection.bulkTransfer(epOut, data, data.size, 1000)

                // Phase 3: Read Response
                val resp = readPacket(1000)
                return resp != null
            }

            // 1. Reset any stale session from a previous run (otherwise the camera keeps
            //    showing "Check the connected device"), then PTP OpenSession (0x1002)
            sendPtpCommand(0x1003)
            delay(100)
            txId = 1
            sendPtpCommand(0x1002, 1)
            try {

            // 2. Sony SDIO_Connect Phase 1 (0x9201, params: 1, 0, 0)
            sendPtpCommand(0x9201, 1, 0, 0)
            delay(50)

            // 3. Sony SDIO_Connect Phase 2 (0x9201, params: 2, 0, 0)
            sendPtpCommand(0x9201, 2, 0, 0)
            delay(50)

            // 4. Sony SDIO_GetExtDeviceInfo (0x9202, param: 0xC8 = 200 protocol version)
            // This is mandatory for a6000 & a6400 to unlock remote property control!
            sendPtpCommand(0x9202, 0x000000C8)
            delay(50)

            // 5. Sony SDIO_Connect Phase 3 (0x9201, params: 3, 0, 0) - Finalize handshake
            sendPtpCommand(0x9201, 3, 0, 0)
            delay(80)

            // 6. Half-Press Shutter S1 (Autofocus & Flash Ready) -> Property 0xD2C1, Value 2 (Press)
            sendSonyControl(0xD2C1, 2.toShort())
            delay(200) // Wait for AF lock & flash charge

            // 7. Full-Press Shutter S2 (Shutter Release & Flash Fire!) -> Property 0xD2C2, Value 2 (Press)
            sendSonyControl(0xD2C2, 2.toShort())
            delay(250) // Hold shutter down

            // 8. Release Full-Press S2 -> Value 1 (Release)
            sendSonyControl(0xD2C2, 1.toShort())
            delay(80)

            // 9. Release Half-Press S1 -> Value 1 (Release)
            sendSonyControl(0xD2C1, 1.toShort())
            } finally {
                // Always close the PTP session so the camera never gets stuck waiting on the host
                runCatching { sendPtpCommand(0x1003) }
                connection.releaseInterface(targetInterface)
                connection.close()
            }
            true
        } catch (_: Exception) {
            try { connection?.close() } catch (_: Exception) {}
            false
        }
    }
}

/**
 * High-fidelity 256-color GIF89a animated encoder.
 * Uses adaptive perceptual Median-Cut color quantization, RGB565 nearest-match lookup table (LUT),
 * serpentine clamped Floyd-Steinberg error diffusion dithering, and spec-compliant GIF89a LZW encoding.
 */
object SimpleGifEncoder {
    fun encode(frames: List<Bitmap>, delayMs: Int): ByteArray {
        val w = frames.first().width
        val h = frames.first().height
        val out = ByteArrayOutputStream()

        // 1. Generate dedicated 256-color palettes for each individual frame (Local Color Tables)
        val palettes = frames.map { generatePalette(it, 256) }

        // 2. GIF Header
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, w)
        writeShort(out, h)
        out.write(0xF7) // Global color table present (256 colors)
        out.write(0)    // Background color
        out.write(0)    // Pixel aspect ratio

        // 3. Global Color Table (use frame 0 palette as fallback)
        val gct = palettes.first()
        for (i in 0 until 256) {
            val c = gct[i]
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }

        // 4. Netscape Application Extension for infinite looping
        out.write(0x21)
        out.write(0xFF)
        out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3)
        out.write(1)
        writeShort(out, 0) // Loop forever
        out.write(0)

        // 5. Encode each frame with its own dedicated 256-color Local Color Table
        for (idx in frames.indices) {
            val frame = frames[idx]
            val palette = palettes[idx]
            // Pause on the final pose for 2x delay before looping
            val frameDelayCs = if (idx == frames.lastIndex) (delayMs * 2) / 10 else delayMs / 10

            // Graphic Control Extension
            out.write(0x21)
            out.write(0xF9)
            out.write(4)
            out.write(0) // No transparency, dispose none
            writeShort(out, frameDelayCs)
            out.write(0)
            out.write(0)

            // Image Descriptor with Local Color Table (0x87: LCT flag=1, size=256)
            out.write(0x2C)
            writeShort(out, 0)
            writeShort(out, 0)
            writeShort(out, w)
            writeShort(out, h)
            out.write(0x87) // Local color table follows immediately

            // Local Color Table (256 * 3 bytes)
            for (i in 0 until 256) {
                val c = palette[i]
                out.write((c shr 16) and 0xFF)
                out.write((c shr 8) and 0xFF)
                out.write(c and 0xFF)
            }

            // Quantize pixels using exact 24-bit perceptual distance and direct hash cache (no noisy dither speckles)
            val indexed = quantizeFrame(frame, palette)
            lzwEncode(out, indexed)
        }

        out.write(0x3B) // Trailer
        return out.toByteArray()
    }

    private class ColorBox(val colors: IntArray, val start: Int, val end: Int) : Comparable<ColorBox> {
        var minR = 255; var maxR = 0
        var minG = 255; var maxG = 0
        var minB = 255; var maxB = 0

        init {
            for (i in start until end) {
                val c = colors[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r < minR) minR = r
                if (r > maxR) maxR = r
                if (g < minG) minG = g
                if (g > maxG) maxG = g
                if (b < minB) minB = b
                if (b > maxB) maxB = b
            }
        }

        val count: Int get() = end - start
        val rSpanWeighted: Int get() = ((maxR - minR) * 299) / 1000
        val gSpanWeighted: Int get() = ((maxG - minG) * 587) / 1000
        val bSpanWeighted: Int get() = ((maxB - minB) * 114) / 1000
        val weightedSpan: Int get() = maxOf(rSpanWeighted, gSpanWeighted, bSpanWeighted)

        // Population-weighted variance priority:
        // Multiplying count by span squared ensures dense areas like skin tones get dedicated colors!
        val priority: Long get() = count.toLong() * (weightedSpan.toLong() * weightedSpan + 1)

        override fun compareTo(other: ColorBox): Int = other.priority.compareTo(this.priority)

        fun averageColor(): Int {
            if (count == 0) return 0
            var tr = 0L
            var tg = 0L
            var tb = 0L
            for (i in start until end) {
                val c = colors[i]
                tr += (c shr 16) and 0xFF
                tg += (c shr 8) and 0xFF
                tb += c and 0xFF
            }
            val ar = (tr / count).toInt().coerceIn(0, 255)
            val ag = (tg / count).toInt().coerceIn(0, 255)
            val ab = (tb / count).toInt().coerceIn(0, 255)
            return (ar shl 16) or (ag shl 8) or ab
        }
    }

    private fun generatePalette(frame: Bitmap, maxColors: Int = 256): IntArray {
        val w = frame.width
        val h = frame.height
        // Fast color sampling using a thumbnail to minimize heap allocation and maximize speed
        val thumbW = 240
        val thumbH = ((240f * h) / w).roundToInt().coerceAtLeast(1)
        val thumb = Bitmap.createScaledBitmap(frame, thumbW, thumbH, false)
        val totalPixels = thumbW * thumbH
        val pixels = IntArray(totalPixels)
        thumb.getPixels(pixels, 0, thumbW, 0, 0, thumbW, thumbH)
        thumb.recycle()

        val samples = ArrayList<Int>(totalPixels)
        for (i in 0 until totalPixels) {
            samples.add(pixels[i] and 0x00FFFFFF)
        }

        if (samples.isEmpty()) {
            return IntArray(maxColors)
        }

        val colors = samples.toIntArray()
        val queue = java.util.PriorityQueue<ColorBox>()
        queue.add(ColorBox(colors, 0, colors.size))

        while (queue.size < maxColors) {
            val box = queue.poll() ?: break
            if (box.count <= 1 || box.weightedSpan == 0) {
                queue.add(box)
                break
            }

            val channel = when {
                box.gSpanWeighted >= box.rSpanWeighted && box.gSpanWeighted >= box.bSpanWeighted -> 1
                box.rSpanWeighted >= box.bSpanWeighted -> 0
                else -> 2
            }

            sortColorsByChannel(colors, box.start, box.end - 1, channel)
            val mid = (box.start + box.end) / 2
            queue.add(ColorBox(colors, box.start, mid))
            queue.add(ColorBox(colors, mid, box.end))
        }

        val palette = IntArray(maxColors)
        var idx = 0
        while (queue.isNotEmpty() && idx < maxColors) {
            val b = queue.poll() ?: break
            palette[idx++] = b.averageColor()
        }
        val lastColor = if (idx > 0) palette[idx - 1] else 0
        while (idx < maxColors) {
            palette[idx++] = lastColor
        }
        return palette
    }

    private fun sortColorsByChannel(a: IntArray, low: Int, high: Int, channel: Int) {
        if (low >= high) return
        val pivotVal = getChannel(a[(low + high) ushr 1], channel)
        var i = low
        var j = high
        while (i <= j) {
            while (getChannel(a[i], channel) < pivotVal) i++
            while (getChannel(a[j], channel) > pivotVal) j--
            if (i <= j) {
                val tmp = a[i]
                a[i] = a[j]
                a[j] = tmp
                i++
                j--
            }
        }
        if (low < j) sortColorsByChannel(a, low, j, channel)
        if (i < high) sortColorsByChannel(a, i, high, channel)
    }

    private fun getChannel(c: Int, channel: Int): Int {
        return when (channel) {
            0 -> (c shr 16) and 0xFF
            1 -> (c shr 8) and 0xFF
            else -> c and 0xFF
        }
    }

    private fun quantizeFrame(frame: Bitmap, palette: IntArray): ByteArray {
        val w = frame.width
        val h = frame.height
        val total = w * h
        val argb = IntArray(total)
        frame.getPixels(argb, 0, w, 0, 0, w, h)
        val indexed = ByteArray(total)

        val palR = IntArray(256) { (palette[it] shr 16) and 0xFF }
        val palG = IntArray(256) { (palette[it] shr 8) and 0xFF }
        val palB = IntArray(256) { palette[it] and 0xFF }

        val cacheKeys = IntArray(16384) { -1 }
        val cacheValues = ByteArray(16384)

        for (i in 0 until total) {
            val c = argb[i] and 0x00FFFFFF
            val hash = (c xor (c ushr 10) xor (c ushr 18)) and 16383
            if (cacheKeys[hash] == c) {
                indexed[i] = cacheValues[hash]
            } else {
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                var bestIdx = 0
                var bestDist = Int.MAX_VALUE
                for (p in 0 until 256) {
                    val dr = r - palR[p]
                    val dg = g - palG[p]
                    val db = b - palB[p]
                    val dist = (2 * dr * dr) + (4 * dg * dg) + (3 * db * db)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestIdx = p
                        if (dist == 0) break
                    }
                }
                val bestByte = bestIdx.toByte()
                cacheKeys[hash] = c
                cacheValues[hash] = bestByte
                indexed[i] = bestByte
            }
        }
        return indexed
    }

    private fun lzwEncode(out: ByteArrayOutputStream, pixels: ByteArray) {
        val initCodeSize = 8
        out.write(initCodeSize)

        val clearCode = 1 shl initCodeSize
        val endCode = clearCode + 1
        var codeSize = initCodeSize + 1
        var maxCode = 1 shl codeSize
        var nextCode = endCode + 1

        val hashSize = 9973
        val hashPrefix = IntArray(hashSize) { -1 }
        val hashSuffix = IntArray(hashSize) { -1 }
        val hashNext = IntArray(hashSize) { -1 }

        fun clearTable() {
            hashPrefix.fill(-1)
            hashSuffix.fill(-1)
            hashNext.fill(-1)
            codeSize = initCodeSize + 1
            maxCode = 1 shl codeSize
            nextCode = endCode + 1
        }

        fun findInTable(prefix: Int, suffix: Int): Int {
            var h = ((prefix shl 8) xor suffix) and 0x7FFFFFFF % hashSize
            while (hashPrefix[h] != -1) {
                if (hashPrefix[h] == prefix && hashSuffix[h] == suffix) {
                    return hashNext[h]
                }
                h = (h + 1) % hashSize
            }
            return -1
        }

        fun insertInTable(prefix: Int, suffix: Int, code: Int) {
            var h = ((prefix shl 8) xor suffix) and 0x7FFFFFFF % hashSize
            while (hashPrefix[h] != -1) {
                h = (h + 1) % hashSize
            }
            hashPrefix[h] = prefix
            hashSuffix[h] = suffix
            hashNext[h] = code
        }

        val subBlock = ByteArrayOutputStream()
        var bitBuf = 0
        var bitCnt = 0

        fun emitCode(code: Int) {
            bitBuf = bitBuf or (code shl bitCnt)
            bitCnt += codeSize
            while (bitCnt >= 8) {
                subBlock.write(bitBuf and 0xFF)
                bitBuf = bitBuf ushr 8
                bitCnt -= 8
                if (subBlock.size() == 254) {
                    out.write(subBlock.size())
                    subBlock.writeTo(out)
                    subBlock.reset()
                }
            }
        }

        emitCode(clearCode)
        clearTable()

        if (pixels.isNotEmpty()) {
            var prefix = pixels[0].toInt() and 0xFF
            for (i in 1 until pixels.size) {
                val suffix = pixels[i].toInt() and 0xFF
                val code = findInTable(prefix, suffix)
                if (code != -1) {
                    prefix = code
                } else {
                    emitCode(prefix)
                    if (nextCode < 4096) {
                        insertInTable(prefix, suffix, nextCode)
                        if (nextCode >= maxCode && codeSize < 12) {
                            codeSize++
                            maxCode = 1 shl codeSize
                        }
                        nextCode++
                    } else {
                        emitCode(clearCode)
                        clearTable()
                    }
                    prefix = suffix
                }
            }
            emitCode(prefix)
        }

        emitCode(endCode)

        if (bitCnt > 0) {
            subBlock.write(bitBuf and 0xFF)
        }
        if (subBlock.size() > 0) {
            out.write(subBlock.size())
            subBlock.writeTo(out)
        }
        out.write(0) // Block terminator
    }

    private fun writeShort(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }
}
