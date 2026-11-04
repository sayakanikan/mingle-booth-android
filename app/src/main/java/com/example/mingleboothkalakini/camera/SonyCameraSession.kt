package com.example.mingleboothkalakini.camera

import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sony Alpha "PC Remote" protocol (PTP + Sony SDIO vendor extension) as used by the a6700.
 *
 * The sequence follows the open-source libgphoto2 implementation (camlibs/ptp2), which lists the
 * a6700 as "Sony:A6700 (PC Control)" 054c:0e78 with live-view support. Sony's own Camera Remote
 * SDK has no Android build, so this is a clean-room Kotlin implementation of the same wire protocol.
 *
 * All methods block – call them from a background thread and never concurrently
 * ([SonyCameraController] serialises access).
 */
class SonyCameraSession(private val transport: PtpUsbTransport) {

    var modelName: String = "Sony camera"
        private set

    /** Connects (PTP session + Sony SDIO handshake). Throws [IOException] on failure. */
    fun connect() {
        try {
            openSessionAndHandshake(PROTOCOL_3_00)
        } catch (e: Exception) {
            Log.w("SonyPTP", "Handshake with PROTOCOL_3_00 failed (${e.message}), retrying with PROTOCOL_2_00")
            runCatching { transport.transaction(Ptp.OC_CLOSE_SESSION) }
            Thread.sleep(200)
            try {
                openSessionAndHandshake(PROTOCOL_2_00)
            } catch (e2: Exception) {
                throw IOException("Sony connection failed: ${e.message ?: e2.message}", e)
            }
        }
        Thread.sleep(SETTLE_AFTER_CONNECT_MS)
    }

    private fun openSessionAndHandshake(protocolVersion: Int) {
        transport.resetTransactionId()

        // 1. Try OpenSession first. Retry if camera reports device busy.
        var open = transport.transaction(Ptp.OC_OPEN_SESSION, intArrayOf(1))
        for (retry in 0 until 5) {
            if (open.code == Ptp.RC_DEVICE_BUSY) {
                Thread.sleep(200)
                open = transport.transaction(Ptp.OC_OPEN_SESSION, intArrayOf(1))
            } else {
                break
            }
        }
        if (open.code == Ptp.RC_SESSION_ALREADY_OPEN) {
            runCatching { transport.transaction(Ptp.OC_CLOSE_SESSION) }
            Thread.sleep(150)
            transport.resetTransactionId()
            open = transport.transaction(Ptp.OC_OPEN_SESSION, intArrayOf(1))
        }

        if (open.code != Ptp.RC_OK && open.code != Ptp.RC_SESSION_ALREADY_OPEN) {
            throw PtpException(open.code, "Camera refused the PTP session (0x%04X)".format(open.code))
        }

        runCatching {
            val info = transport.execute(Ptp.OC_GET_DEVICE_INFO).data
            parseModel(info)?.let { modelName = it }
        }

        // 2. Sony SDIO connect phases 1 and 2
        transport.execute(OC_SDIO_CONNECT, intArrayOf(1, 0, 0))
        transport.execute(OC_SDIO_CONNECT, intArrayOf(2, 0, 0))

        // 3. Extended Device Info (wait up to 2.5s for camera to prepare table)
        var extInfo = ByteArray(0)
        for (attempt in 0 until 25) {
            val params = if (protocolVersion >= PROTOCOL_3_00) intArrayOf(protocolVersion, 1) else intArrayOf(protocolVersion)
            val res = transport.transaction(OC_SDIO_GET_EXT_DEVICE_INFO, params)
            if (res.code == Ptp.RC_OK && res.data.isNotEmpty()) {
                extInfo = res.data
                break
            }
            Thread.sleep(100)
        }
        if (extInfo.isEmpty()) throw IOException("Camera did not report its extended device info")

        // 4. Final SDIO connect phase
        transport.execute(OC_SDIO_CONNECT, intArrayOf(3, 0, 0))
    }

    /**
     * Fetches one live-view JPEG frame, or null if transient error or frame not ready yet.
     */
    fun fetchLiveViewJpeg(): ByteArray? {
        val r = transport.transaction(Ptp.OC_GET_OBJECT, intArrayOf(HANDLE_LIVE_VIEW))
        return when (r.code) {
            Ptp.RC_OK -> extractJpeg(r.data)
            Ptp.RC_ACCESS_DENIED, Ptp.RC_INVALID_OBJECT_HANDLE, Ptp.RC_DEVICE_BUSY -> null
            else -> null
        }
    }

    /**
     * Metadata parsed from PTP ObjectInfo dataset (ISO 15740).
     */
    data class ObjectMeta(
        val size: Long,
        val sequenceNumber: Long,
        val filename: String?,
        val captureDate: String?
    )

    /**
     * Returns the metadata of the image currently residing in HANDLE_IMAGE, or null if empty/inaccessible.
     */
    fun sampleBaselineMeta(): ObjectMeta? {
        val baselineInfo = transport.transaction(Ptp.OC_GET_OBJECT_INFO, intArrayOf(HANDLE_IMAGE))
        return if (baselineInfo.code == Ptp.RC_OK) parseObjectMeta(baselineInfo.data) else null
    }

    /**
     * Checks if a new photo has arrived in HANDLE_IMAGE that differs from [baselineMeta].
     * Returns the new [ObjectMeta] if available, or null if no new photo is ready.
     */
    fun checkNewPhotoAvailable(baselineMeta: ObjectMeta?): ObjectMeta? {
        val probe = transport.transaction(Ptp.OC_GET_OBJECT_INFO, intArrayOf(HANDLE_IMAGE))
        if (probe.code != Ptp.RC_OK) return null
        val currentMeta = parseObjectMeta(probe.data) ?: return null
        val isNew = if (baselineMeta == null) {
            currentMeta.size > 0
        } else {
            (currentMeta.filename != null && currentMeta.filename != baselineMeta.filename) ||
            (currentMeta.captureDate != null && currentMeta.captureDate != baselineMeta.captureDate) ||
            (currentMeta.sequenceNumber != 0L && currentMeta.sequenceNumber != baselineMeta.sequenceNumber) ||
            (currentMeta.size != baselineMeta.size)
        }
        return if (isNew) currentMeta else null
    }

    /**
     * Downloads the full-resolution photo currently in HANDLE_IMAGE.
     */
    fun downloadPhoto(): ByteArray {
        repeat(DOWNLOAD_RETRIES) {
            val r = transport.transaction(Ptp.OC_GET_OBJECT, intArrayOf(HANDLE_IMAGE))
            when (r.code) {
                Ptp.RC_OK -> {
                    val d = r.data
                    if (d.size < 4 || d[0] != 0xFF.toByte() || d[1] != 0xD8.toByte()) {
                        throw IOException("Camera returned a non-JPEG file – set image quality to JPEG or RAW+JPEG")
                    }
                    return d
                }
                Ptp.RC_ACCESS_DENIED, Ptp.RC_DEVICE_BUSY, Ptp.RC_INVALID_OBJECT_HANDLE -> Thread.sleep(250)
                else -> throw PtpException(r.code, "Photo download failed (0x%04X)".format(r.code))
            }
        }
        throw IOException("Timed out while downloading full-size photo from the camera")
    }

    /**
     * Fires the shutter (S1 half-press for AF/flash ready + S2 full-press to trip shutter and external flash),
     * and downloads the full-resolution JPEG from the camera's RAM buffer.
     */
    fun capturePhoto(): ByteArray {
        val baselineMeta = sampleBaselineMeta()
        Log.d("SonyPTP", "Baseline image in camera RAM before shutter: $baselineMeta")

        var s1Down = false
        var s2Down = false
        try {
            // 1. S1 Half-press: Wake up AF, exposure metering, and strobe trigger
            control(PROP_S1, 2)
            s1Down = true
            Thread.sleep(250)

            // 2. S2 Full-press: Fire shutter & hot-shoe flash trigger
            control(PROP_S2, 2)
            s2Down = true
            Thread.sleep(400) // Hold 400ms to guarantee shutter actuation and flash sync
        } finally {
            // 3. Release shutter and focus in reverse order
            if (s2Down) runCatching { control(PROP_S2, 1) }
            if (s1Down) runCatching { control(PROP_S1, 1) }
        }

        // 4. Wait for the NEW photo to be processed by BIONZ XR and written to RAM buffer
        val waitStart = System.currentTimeMillis()
        while (System.currentTimeMillis() - waitStart < IMAGE_TIMEOUT_MS) {
            val newMeta = checkNewPhotoAvailable(baselineMeta)
            if (newMeta != null) {
                Log.d("SonyPTP", "New photo detected in camera RAM: $newMeta (was: $baselineMeta)")
                Thread.sleep(100)
                return downloadPhoto()
            }
            Thread.sleep(POLL_MS)
        }

        throw IOException("Camera did not produce a new photo (check focus lock, card status, and exposure)")
    }

    fun close() {
        runCatching { transport.transaction(Ptp.OC_CLOSE_SESSION) }
        transport.close()
    }

    // ---------------------------------------------------------------- helpers

    /** SDIO_ControlDevice: 1 = release, 2 = press. */
    private fun control(prop: Int, value: Int) {
        val payload = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
        transport.execute(OC_SDIO_CONTROL_DEVICE, intArrayOf(prop), payload)
    }

    /**
     * Parses PTP ISO 15740 ObjectInfo dataset.
     * Offset 8: ObjectCompressedSize (uint32)
     * Offset 48: SequenceNumber (uint32)
     * Offset 52: Filename (PTP string: 1 byte char count + UTF-16LE chars)
     * Followed by CaptureDate (PTP string)
     */
    private fun parseObjectMeta(data: ByteArray?): ObjectMeta? {
        if (data == null || data.size < 52) return null
        return try {
            val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val size = bb.getInt(8).toLong() and 0xFFFFFFFFL
            val seq = bb.getInt(48).toLong() and 0xFFFFFFFFL

            fun readPtpString(offset: Int): Pair<String?, Int> {
                if (offset >= data.size) return null to offset
                val numChars = data[offset].toInt() and 0xFF
                if (numChars == 0) return null to (offset + 1)
                val byteLen = numChars * 2
                val end = offset + 1 + byteLen
                if (end > data.size) return null to data.size
                val sb = StringBuilder()
                for (i in 0 until numChars) {
                    val c = bb.getShort(offset + 1 + i * 2).toInt() and 0xFFFF
                    if (c != 0) sb.append(c.toChar())
                }
                val str = sb.toString().trim()
                return (if (str.isNotEmpty()) str else null) to end
            }

            val (filename, dateOffset) = readPtpString(52)
            val (captureDate, _) = readPtpString(dateOffset)

            ObjectMeta(
                size = size,
                sequenceNumber = seq,
                filename = filename,
                captureDate = captureDate
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun readProps(): Map<Int, Long> {
        val r = transport.transaction(OC_SDIO_GET_ALL_PROPS)
        if (r.code != Ptp.RC_OK) return emptyMap()
        return parseProps(r.data)
    }

    /** Live view data = 4-byte little-endian offset to the JPEG, a small header, then the JPEG. */
    private fun extractJpeg(data: ByteArray): ByteArray? {
        if (data.size < 16) return null
        var start = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int
        if (start < 4 || start + 2 > data.size || data[start] != 0xFF.toByte() || data[start + 1] != 0xD8.toByte()) {
            start = -1
            val limit = minOf(data.size - 2, 1024)
            for (i in 0 until limit) {
                if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte() && data[i + 2] == 0xFF.toByte()) {
                    start = i
                    break
                }
            }
        }
        if (start < 0) return null
        return data.copyOfRange(start, data.size)
    }

    /**
     * Minimal parser for SDIO_GetAllExtDevicePropInfo. Walks every property descriptor and
     * returns the current integer value of the ones we care about.
     */
    private fun parseProps(data: ByteArray): Map<Int, Long> {
        val out = HashMap<Int, Long>()
        if (data.size <= 8) return out
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var p = 8 // 4-byte count + 4 reserved

        fun u16(at: Int) = bb.getShort(at).toInt() and 0xFFFF

        try {
            while (p + 6 <= data.size) {
                val code = u16(p)
                val type = u16(p + 2)
                if (code < 0x5000) break
                p += 6 // code, type, getSet, isEnabled

                val sz = valueSize(bb, type, p)
                if (sz <= 0) break

                val currentValue = readInt(bb, type, p, sz)
                if (currentValue != null) out[code] = currentValue
                p += sz // skip current value

                val factorySz = valueSize(bb, type, p)
                if (factorySz <= 0) break
                p += factorySz // skip factory default

                if (p >= data.size) break
                val form = bb.get(p).toInt() and 0xFF
                p += 1
                when (form) {
                    0 -> Unit // none
                    1 -> { // RangeForm: min, max, step
                        val elemSz = valueSize(bb, type, p)
                        if (elemSz > 0) p += elemSz * 3
                    }
                    2 -> { // EnumForm: count, then elements
                        if (p + 2 <= data.size) {
                            val count = u16(p)
                            p += 2
                            val elemSz = valueSize(bb, type, p)
                            if (elemSz > 0) p += elemSz * count
                        }
                    }
                }
            }
        } catch (_: IndexOutOfBoundsException) {
            // truncated block – use what we have
        }
        return out
    }

    private fun valueSize(bb: ByteBuffer, type: Int, at: Int): Int = when (type) {
        1, 2 -> 1
        3, 4 -> 2
        5, 6 -> 4
        7, 8 -> 8
        9, 10 -> 16
        0xFFFF -> if (at < bb.limit()) 1 + 2 * (bb.get(at).toInt() and 0xFF) else -1
        else -> -1
    }

    private fun readInt(bb: ByteBuffer, type: Int, at: Int, size: Int): Long? = when (type) {
        1 -> bb.get(at).toLong()
        2 -> (bb.get(at).toLong() and 0xFF)
        3 -> bb.getShort(at).toLong()
        4 -> bb.getShort(at).toLong() and 0xFFFF
        5 -> bb.getInt(at).toLong()
        6 -> bb.getInt(at).toLong() and 0xFFFFFFFFL
        7, 8 -> if (size == 8) bb.getLong(at) else null
        else -> null
    }

    /** DeviceInfo dataset → model string (best effort, used for display only). */
    private fun parseModel(data: ByteArray): String? {
        return try {
            val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            fun readString(at: Int): Pair<String, Int> {
                val chars = bb.get(at).toInt() and 0xFF
                val sb = StringBuilder()
                for (i in 0 until chars) {
                    val c = bb.getShort(at + 1 + i * 2).toInt() and 0xFFFF
                    if (c != 0) sb.append(c.toChar())
                }
                return sb.toString() to (at + 1 + chars * 2)
            }
            var p = 8
            p = readString(p).second
            p += 2
            repeat(5) {
                val n = bb.getInt(p)
                p += 4 + n * 2
            }
            p = readString(p).second
            readString(p).first.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        // Sony vendor operations
        private const val OC_SDIO_CONNECT = 0x9201
        private const val OC_SDIO_GET_EXT_DEVICE_INFO = 0x9202
        private const val OC_SDIO_CONTROL_DEVICE = 0x9207
        private const val OC_SDIO_GET_ALL_PROPS = 0x9209

        // Sony properties / controls
        private const val PROP_S1 = 0xD2C1 // shutter half-press
        private const val PROP_S2 = 0xD2C2 // shutter full-press
        private const val PROP_FOCUS_FOUND = 0xD213
        private const val PROP_OBJECT_IN_MEMORY = 0xD215

        // Special object handles
        private const val HANDLE_IMAGE = 0xFFFFC001.toInt()
        private const val HANDLE_LIVE_VIEW = 0xFFFFC002.toInt()

        private const val PROTOCOL_2_00 = 0x00C8
        private const val PROTOCOL_3_00 = 0x012C

        private const val IMAGE_READY = 0x8000L
        private const val POLL_MS = 150L
        private const val IMAGE_TIMEOUT_MS = 20000L
        private const val DOWNLOAD_RETRIES = 25
        private const val SETTLE_AFTER_CONNECT_MS = 400L
    }
}
