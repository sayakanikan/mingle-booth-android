package com.example.mingleboothkalakini.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException

/**
 * Native Android UVC (USB Video Class) session for HDMI capture cards
 * (e.g. MacroSilicon MS2109/MS2130, Zasprec, Elgato Cam Link) over USB Bulk transfer.
 *
 * Operates purely through Android's USB Host API (android.hardware.usb), requiring no
 * native NDK or external libraries.
 */
class HdmiCaptureSession(
    private val connection: UsbDeviceConnection,
    private val device: UsbDevice
) : Closeable {

    companion object {
        private const val TAG = "HdmiCaptureSession"

        // UVC Interface Class & Subclasses
        private const val CC_VIDEO = 14
        private const val SC_VIDEOCONTROL = 1
        private const val SC_VIDEOSTREAMING = 2

        // UVC Class-Specific Request Codes
        private const val SET_CUR = 0x01
        private const val GET_CUR = 0x81
        private const val GET_MIN = 0x82
        private const val GET_MAX = 0x83
        private const val GET_DEF = 0x87

        // VideoStreaming Interface Control Selectors
        private const val VS_PROBE_CONTROL = 0x01
        private const val VS_COMMIT_CONTROL = 0x02

        // Request types: Class-specific Interface requests (0x21 OUT, 0xA1 IN)
        private const val REQ_TYPE_SET = 0x21
        private const val REQ_TYPE_GET = 0xA1

        // UVC CS_INTERFACE Subtypes
        private const val VS_FORMAT_UNCOMPRESSED = 0x04
        private const val VS_FRAME_UNCOMPRESSED = 0x05
        private const val VS_FORMAT_MJPEG = 0x06
        private const val VS_FRAME_MJPEG = 0x07


    }

    data class FrameDescriptor(
        val frameIndex: Int,
        val width: Int,
        val height: Int,
        val defaultInterval: Int
    )

    data class FormatDescriptor(
        val formatIndex: Int,
        val isMjpeg: Boolean,
        val frames: List<FrameDescriptor>
    )

    private var vcInterface: UsbInterface? = null
    private var vsInterface: UsbInterface? = null
    private var bulkInEndpoint: UsbEndpoint? = null

    private var selectedFormat: FormatDescriptor? = null
    private var selectedFrame: FrameDescriptor? = null

    @Volatile
    private var isStreaming = false

    init {
        findInterfacesAndEndpoints()
    }

    private fun findInterfacesAndEndpoints() {
        // 1. Locate VC and VS interfaces
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == CC_VIDEO) {
                when (iface.interfaceSubclass) {
                    SC_VIDEOCONTROL -> vcInterface = iface
                    SC_VIDEOSTREAMING -> vsInterface = iface
                }
            }
        }

        // Fallback: If subclasses are 0 or not strictly standard, search endpoints
        if (vsInterface == null) {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)
                    if (ep.direction == UsbConstants.USB_DIR_IN &&
                        (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK || ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC)
                    ) {
                        vsInterface = iface
                        break
                    }
                }
                if (vsInterface != null) break
            }
        }

        val vs = vsInterface ?: throw IOException("No VideoStreaming interface found on USB device")

        // 2. Locate Bulk or Isoc IN endpoint on VS interface
        for (e in 0 until vs.endpointCount) {
            val ep = vs.getEndpoint(e)
            if (ep.direction == UsbConstants.USB_DIR_IN) {
                bulkInEndpoint = ep
                break
            }
        }

        if (bulkInEndpoint == null) {
            throw IOException("No Video IN endpoint found on VideoStreaming interface")
        }

        Log.d(TAG, "UVC Device initialized: VC iface=${vcInterface?.id}, VS iface=${vs.id}, EP=0x${bulkInEndpoint?.address?.toString(16)}")
    }

    /**
     * Parses the raw USB configuration descriptors to identify available formats and resolutions.
     */
    private fun parseDescriptors(): List<FormatDescriptor> {
        val raw = try {
            connection.rawDescriptors
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        val formats = mutableListOf<FormatDescriptor>()
        var curFormatIndex = -1
        var curIsMjpeg = false
        var curFrames = mutableListOf<FrameDescriptor>()

        var offset = 0
        while (offset + 2 <= raw.size) {
            val bLength = raw[offset].toInt() and 0xFF
            if (bLength < 2 || offset + bLength > raw.size) break
            val bDescType = raw[offset + 1].toInt() and 0xFF

            if (bDescType == 0x24) { // CS_INTERFACE
                val subtype = raw[offset + 2].toInt() and 0xFF
                when (subtype) {
                    VS_FORMAT_MJPEG -> {
                        if (curFormatIndex != -1) {
                            formats.add(FormatDescriptor(curFormatIndex, curIsMjpeg, curFrames.toList()))
                            curFrames.clear()
                        }
                        curFormatIndex = raw.getOrNull(offset + 3)?.toInt()?.and(0xFF) ?: 1
                        curIsMjpeg = true
                    }
                    VS_FORMAT_UNCOMPRESSED -> {
                        if (curFormatIndex != -1) {
                            formats.add(FormatDescriptor(curFormatIndex, curIsMjpeg, curFrames.toList()))
                            curFrames.clear()
                        }
                        curFormatIndex = raw.getOrNull(offset + 3)?.toInt()?.and(0xFF) ?: 1
                        curIsMjpeg = false
                    }
                    VS_FRAME_MJPEG, VS_FRAME_UNCOMPRESSED -> {
                        if (curFormatIndex != -1 && offset + 25 <= raw.size) {
                            val fIndex = raw[offset + 3].toInt() and 0xFF
                            val w = (raw[offset + 5].toInt() and 0xFF) or ((raw[offset + 6].toInt() and 0xFF) shl 8)
                            val h = (raw[offset + 7].toInt() and 0xFF) or ((raw[offset + 8].toInt() and 0xFF) shl 8)
                            val interval = (raw[offset + 21].toInt() and 0xFF) or
                                ((raw[offset + 22].toInt() and 0xFF) shl 8) or
                                ((raw[offset + 23].toInt() and 0xFF) shl 8) or
                                ((raw[offset + 24].toInt() and 0xFF) shl 24)
                            curFrames.add(FrameDescriptor(fIndex, w, h, interval))
                        }
                    }
                }
            }
            offset += bLength
        }

        if (curFormatIndex != -1) {
            formats.add(FormatDescriptor(curFormatIndex, curIsMjpeg, curFrames.toList()))
        }

        Log.d(TAG, "Parsed UVC formats: $formats")
        return formats
    }

    /**
     * Starts the UVC video stream and invokes [onFrame] for every successfully decoded frame.
     * Blocks the calling thread; must be run on Dispatchers.IO.
     */
    fun startStreaming(preferredWidth: Int = 1920, preferredHeight: Int = 1080, onFrame: (Bitmap) -> Unit) {
        val vs = vsInterface ?: throw IOException("VideoStreaming interface not available")
        val ep = bulkInEndpoint ?: throw IOException("Video IN endpoint not available")

        // 1. Claim interfaces
        val vcClaimed = vcInterface?.let { connection.claimInterface(it, true) } ?: false
        val vsClaimed = connection.claimInterface(vs, true)
        Log.d(TAG, "Interface claims: vc=$vcClaimed, vs=$vsClaimed, epAddress=0x${ep.address.toString(16)}, maxPacket=${ep.maxPacketSize}")

        // 2. Select format & resolution
        val formats = parseDescriptors()
        // Prefer MJPEG for efficiency and high framerate (30/60fps)
        val mjpegFormat = formats.firstOrNull { it.isMjpeg } ?: formats.firstOrNull()
        val formatIndex = mjpegFormat?.formatIndex ?: 1
        val isMjpeg = mjpegFormat?.isMjpeg ?: true

        // Find closest resolution (e.g. 1920x1080 or 1280x720)
        val frameDesc = mjpegFormat?.frames?.minByOrNull {
            val dw = it.width - preferredWidth
            val dh = it.height - preferredHeight
            dw * dw + dh * dh
        } ?: mjpegFormat?.frames?.firstOrNull()

        val frameIndex = frameDesc?.frameIndex ?: 1
        val frameInterval = frameDesc?.defaultInterval?.takeIf { it > 0 } ?: 333333 // 30 fps
        val targetWidth = frameDesc?.width ?: preferredWidth
        val targetHeight = frameDesc?.height ?: preferredHeight

        Log.d(TAG, "Selected UVC mode: formatIndex=$formatIndex (mjpeg=$isMjpeg), frameIndex=$frameIndex (${targetWidth}x${targetHeight})")

        // 3. Negotiate UVC Probe and Commit controls (returns dwMaxPayloadTransferSize)
        val maxPayloadSize = negotiateProbeAndCommit(vs.id, formatIndex, frameIndex, frameInterval)

        isStreaming = true

        // 4. Stream loop over Bulk Endpoint using exact negotiated payload size
        val rxBuffer = ByteArray(maxPayloadSize)
        val frameStream = ByteArrayOutputStream(512 * 1024)
        var lastFid = -1
        var loggedPackets = 0
        var totalFramesDecoded = 0
        var lastErrorLogTime = 0L

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        try {
            while (isStreaming) {
                val bytesRead = connection.bulkTransfer(ep, rxBuffer, rxBuffer.size, 1000)
                if (bytesRead <= 0) {
                    val now = System.currentTimeMillis()
                    if (now - lastErrorLogTime > 2000) {
                        lastErrorLogTime = now
                        Log.w(TAG, "bulkTransfer returned $bytesRead (timeout or stall)")
                    }
                    continue
                }

                if (loggedPackets < 10) {
                    loggedPackets++
                    val hexPreview = rxBuffer.take(minOf(16, bytesRead)).joinToString(" ") { "%02X".format(it) }
                    Log.d(TAG, "Bulk packet #$loggedPackets: bytesRead=$bytesRead, hex=[$hexPreview]")
                }

                val headerLen = rxBuffer[0].toInt() and 0xFF
                if (headerLen !in 2..bytesRead) {
                    continue
                }

                val headerInfo = rxBuffer[1].toInt() and 0xFF
                val fid = headerInfo and 0x01
                val isEof = (headerInfo and 0x02) != 0
                val isErr = (headerInfo and 0x40) != 0

                val payloadLen = bytesRead - headerLen
                if (payloadLen > 0 && !isErr) {
                    // If Frame ID toggled, we transitioned to a new frame
                    if (lastFid != -1 && fid != lastFid && frameStream.size() > 0) {
                        dispatchFrame(frameStream.toByteArray(), isMjpeg, targetWidth, targetHeight, decodeOptions) { bmp ->
                            totalFramesDecoded++
                            if (totalFramesDecoded <= 5 || totalFramesDecoded % 60 == 0) {
                                val centerPixel = bmp.getPixel(bmp.width / 2, bmp.height / 2)
                                Log.i(TAG, "Decoded frame #$totalFramesDecoded (${bmp.width}x${bmp.height}), centerPixel=0x${Integer.toHexString(centerPixel)}")
                            }
                            onFrame(bmp)
                        }
                        frameStream.reset()
                    }
                    lastFid = fid
                    frameStream.write(rxBuffer, headerLen, payloadLen)
                }

                // Check for EOF bit OR JPEG End-Of-Image marker (0xFF, 0xD9)
                val hasEoi = isMjpeg && payloadLen >= 2 &&
                    rxBuffer[bytesRead - 2] == 0xFF.toByte() && rxBuffer[bytesRead - 1] == 0xD9.toByte()

                if ((isEof || hasEoi) && frameStream.size() > 0) {
                    dispatchFrame(frameStream.toByteArray(), isMjpeg, targetWidth, targetHeight, decodeOptions) { bmp ->
                        totalFramesDecoded++
                        if (totalFramesDecoded <= 5 || totalFramesDecoded % 60 == 0) {
                            val centerPixel = bmp.getPixel(bmp.width / 2, bmp.height / 2)
                            Log.i(TAG, "Decoded frame #$totalFramesDecoded (${bmp.width}x${bmp.height}), centerPixel=0x${Integer.toHexString(centerPixel)}")
                        }
                        onFrame(bmp)
                    }
                    frameStream.reset()
                    lastFid = -1
                }
            }
        } finally {
            isStreaming = false
            runCatching { connection.releaseInterface(vs) }
            vcInterface?.let { runCatching { connection.releaseInterface(it) } }
        }
    }

    private fun dispatchFrame(
        bytes: ByteArray,
        isMjpeg: Boolean,
        width: Int,
        height: Int,
        options: BitmapFactory.Options,
        onFrame: (Bitmap) -> Unit
    ) {
        try {
            if (isMjpeg) {
                // Find SOI marker (0xFF 0xD8) to ensure clean JPEG decode
                var soi = -1
                val limit = minOf(bytes.size - 1, 256)
                for (i in 0 until limit) {
                    if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD8.toByte()) {
                        soi = i
                        break
                    }
                }
                val start = if (soi != -1) soi else 0
                val bmp = BitmapFactory.decodeByteArray(bytes, start, bytes.size - start, options)
                if (bmp != null) {
                    onFrame(bmp)
                } else {
                    Log.w(TAG, "BitmapFactory returned null for MJPEG frame size=${bytes.size}, soi=$soi")
                }
            } else {
                val bmp = decodeYuy2(bytes, width, height)
                if (bmp != null) {
                    onFrame(bmp)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame decode error: ${e.message}")
        }
    }

    private fun decodeYuy2(yuy2: ByteArray, width: Int, height: Int): Bitmap? {
        val totalPixels = width * height
        if (yuy2.size < totalPixels * 2) return null
        val pixels = IntArray(totalPixels)
        var yIdx = 0
        var pIdx = 0
        val maxLen = totalPixels * 2

        while (yIdx < maxLen && pIdx < totalPixels) {
            val y0 = yuy2[yIdx].toInt() and 0xFF
            val u = (yuy2[yIdx + 1].toInt() and 0xFF) - 128
            val y1 = yuy2[yIdx + 2].toInt() and 0xFF
            val v = (yuy2[yIdx + 3].toInt() and 0xFF) - 128
            yIdx += 4

            val r0 = (y0 + 1.402f * v).toInt().coerceIn(0, 255)
            val g0 = (y0 - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255)
            val b0 = (y0 + 1.772f * u).toInt().coerceIn(0, 255)
            pixels[pIdx++] = (0xFF shl 24) or (r0 shl 16) or (g0 shl 8) or b0

            if (pIdx < totalPixels) {
                val r1 = (y1 + 1.402f * v).toInt().coerceIn(0, 255)
                val g1 = (y1 - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255)
                val b1 = (y1 + 1.772f * u).toInt().coerceIn(0, 255)
                pixels[pIdx++] = (0xFF shl 24) or (r1 shl 16) or (g1 shl 8) or b1
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Executes the standard UVC Probe & Commit negotiation handshake on the VideoStreaming interface.
     * Returns the negotiated dwMaxPayloadTransferSize in bytes.
     */
    private fun negotiateProbeAndCommit(vsInterfaceId: Int, formatIndex: Int, frameIndex: Int, frameInterval: Int): Int {
        val probeBuf = ByteArray(34) // Support up to UVC 1.5 length

        // Step 1: GET_CUR to get device defaults
        var probeLen = connection.controlTransfer(
            REQ_TYPE_GET, GET_CUR, VS_PROBE_CONTROL shl 8, vsInterfaceId, probeBuf, probeBuf.size, 2000
        )
        if (probeLen <= 0) {
            Log.w(TAG, "GET_CUR on VS_PROBE_CONTROL returned $probeLen, using fallback 26-byte probe")
            probeLen = 26
        }

        val actualProbe = probeBuf.copyOf(probeLen.coerceIn(26, 34))

        // Step 2: Set requested format, frame, and interval
        actualProbe[0] = 0x01 // bmHint: dwFrameInterval
        actualProbe[1] = 0x00
        actualProbe[2] = formatIndex.toByte()
        actualProbe[3] = frameIndex.toByte()
        actualProbe[4] = (frameInterval and 0xFF).toByte()
        actualProbe[5] = ((frameInterval shr 8) and 0xFF).toByte()
        actualProbe[6] = ((frameInterval shr 16) and 0xFF).toByte()
        actualProbe[7] = ((frameInterval shr 24) and 0xFF).toByte()

        val probeReqHex = actualProbe.joinToString(" ") { "%02X".format(it) }
        Log.d(TAG, "Probe request bytes: [$probeReqHex]")

        // Step 3: SET_CUR on VS_PROBE_CONTROL
        val setProbeRes = connection.controlTransfer(
            REQ_TYPE_SET, SET_CUR, VS_PROBE_CONTROL shl 8, vsInterfaceId, actualProbe, actualProbe.size, 2000
        )
        Log.d(TAG, "SET_CUR(PROBE) result: $setProbeRes")

        // Step 4: GET_CUR on VS_PROBE_CONTROL (read back negotiated settings)
        val getProbeRes = connection.controlTransfer(
            REQ_TYPE_GET, GET_CUR, VS_PROBE_CONTROL shl 8, vsInterfaceId, actualProbe, actualProbe.size, 2000
        )
        val probeRespHex = actualProbe.joinToString(" ") { "%02X".format(it) }
        Log.d(TAG, "GET_CUR(PROBE) result: $getProbeRes, negotiated bytes: [$probeRespHex]")

        // Step 5: SET_CUR on VS_COMMIT_CONTROL (lock configuration and start streaming)
        val commitRes = connection.controlTransfer(
            REQ_TYPE_SET, SET_CUR, VS_COMMIT_CONTROL shl 8, vsInterfaceId, actualProbe, actualProbe.size, 2000
        )
        Log.d(TAG, "SET_CUR(COMMIT) result: $commitRes")
        if (commitRes < 0) {
            Log.w(TAG, "UVC COMMIT returned $commitRes, attempting stream anyway")
        }

        val maxPayload = if (actualProbe.size >= 26) {
            (actualProbe[22].toInt() and 0xFF) or
                ((actualProbe[23].toInt() and 0xFF) shl 8) or
                ((actualProbe[24].toInt() and 0xFF) shl 16) or
                ((actualProbe[25].toInt() and 0xFF) shl 24)
        } else {
            16384
        }
        val bufferSize = if (maxPayload in 512..65536) maxPayload else 16384
        Log.i(TAG, "Negotiated dwMaxPayloadTransferSize: $maxPayload bytes -> bufferSize=$bufferSize")
        return bufferSize
    }

    override fun close() {
        isStreaming = false
        try {
            vsInterface?.let { connection.releaseInterface(it) }
            vcInterface?.let { connection.releaseInterface(it) }
            connection.close()
        } catch (_: Exception) {
        }
    }
}
