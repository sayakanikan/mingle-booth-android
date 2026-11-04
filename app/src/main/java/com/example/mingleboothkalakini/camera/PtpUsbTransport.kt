package com.example.mingleboothkalakini.camera

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** PTP response codes / opcodes used by the booth. */
object Ptp {
    // Container types
    const val TYPE_COMMAND = 1
    const val TYPE_DATA = 2
    const val TYPE_RESPONSE = 3
    const val TYPE_EVENT = 4

    // Standard operations
    const val OC_GET_DEVICE_INFO = 0x1001
    const val OC_OPEN_SESSION = 0x1002
    const val OC_CLOSE_SESSION = 0x1003
    const val OC_GET_OBJECT_INFO = 0x1008
    const val OC_GET_OBJECT = 0x1009

    // Standard response codes
    const val RC_OK = 0x2001
    const val RC_INVALID_OBJECT_HANDLE = 0x2009
    const val RC_ACCESS_DENIED = 0x200F
    const val RC_DEVICE_BUSY = 0x2019
    const val RC_SESSION_ALREADY_OPEN = 0x201E
}

/** A PTP transaction that completed with a non-OK response code. */
class PtpException(val responseCode: Int, message: String) : IOException(message)

class PtpResult(val code: Int, val params: IntArray, val data: ByteArray)

/** USB interface + bulk endpoints of a PTP (Still Image) capable device. */
class PtpInterface(
    val iface: UsbInterface,
    val bulkIn: UsbEndpoint,
    val bulkOut: UsbEndpoint
)

/**
 * Robust PTP-over-USB transport (ISO 15740) on top of Android's USB host API.
 * Uses a zero-indexed shift buffer so no packet bytes are ever lost between data and response phases.
 * All calls are blocking – always call from a background thread.
 */
class PtpUsbTransport private constructor(
    private val connection: UsbDeviceConnection,
    private val ptpInterface: PtpInterface
) {
    private var txId = 0

    // Buffer for incoming stream, always starts at index 0
    private var rxData = ByteArray(CHUNK_SIZE * 2)
    private var rxCount = 0

    fun resetTransactionId() {
        txId = 0
        rxCount = 0
    }

    /**
     * Drains any leftover bytes in the camera's bulk IN pipe from an interrupted prior session.
     * Uses a short timeout so a clean endpoint returns immediately.
     */
    fun drainStaleData() {
        val discard = ByteArray(CHUNK_SIZE)
        var drained = 0
        while (true) {
            val n = connection.bulkTransfer(ptpInterface.bulkIn, discard, discard.size, 50)
            if (n <= 0) break
            drained += n
        }
        if (drained > 0) {
            Log.d(TAG, "Drained $drained stale bytes from camera IN endpoint")
        }
        rxCount = 0
    }

    /** Executes one PTP transaction and returns the response even if its code is not OK. */
    @Synchronized
    fun transaction(opcode: Int, params: IntArray = IntArray(0), sendData: ByteArray? = null): PtpResult {
        val tx = txId
        txId = if (txId == -2) 1 else txId + 1

        writeAll(buildContainer(opcode, tx, params))
        if (sendData != null) writeAll(buildDataContainer(opcode, tx, sendData))

        var data = EMPTY
        while (true) {
            val c = readContainer()
            when (c.type) {
                Ptp.TYPE_DATA -> data = c.payload
                Ptp.TYPE_RESPONSE -> {
                    val bb = ByteBuffer.wrap(c.payload).order(ByteOrder.LITTLE_ENDIAN)
                    val respParams = IntArray(c.payload.size / 4) { bb.getInt(it * 4) }
                    return PtpResult(c.code, respParams, data)
                }
                else -> Unit // stray event / command container – ignore
            }
        }
    }

    /** Like [transaction] but throws [PtpException] unless the camera answered OK. */
    fun execute(opcode: Int, params: IntArray = IntArray(0), sendData: ByteArray? = null): PtpResult {
        val r = transaction(opcode, params, sendData)
        if (r.code != Ptp.RC_OK) {
            throw PtpException(r.code, "PTP operation 0x%04X failed (response 0x%04X)".format(opcode, r.code))
        }
        return r
    }

    fun close() {
        try { connection.releaseInterface(ptpInterface.iface) } catch (_: Exception) {}
        try { connection.close() } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------- framing

    private class Container(val type: Int, val code: Int, val tx: Int, val payload: ByteArray)

    private fun buildContainer(opcode: Int, tx: Int, params: IntArray): ByteArray {
        val len = 12 + params.size * 4
        val bb = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(len)
        bb.putShort(Ptp.TYPE_COMMAND.toShort())
        bb.putShort(opcode.toShort())
        bb.putInt(tx)
        params.forEach { bb.putInt(it) }
        return bb.array()
    }

    private fun buildDataContainer(opcode: Int, tx: Int, payload: ByteArray): ByteArray {
        val len = 12 + payload.size
        val bb = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(len)
        bb.putShort(Ptp.TYPE_DATA.toShort())
        bb.putShort(opcode.toShort())
        bb.putInt(tx)
        bb.put(payload)
        return bb.array()
    }

    private fun writeAll(buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val toWrite = minOf(buf.size - off, CHUNK_SIZE)
            val n = connection.bulkTransfer(ptpInterface.bulkOut, buf, off, toWrite, TIMEOUT_MS)
            if (n <= 0) throw IOException("USB write failed (camera disconnected or timed out)")
            off += n
        }
    }

    private fun ensureRxBytes(needed: Int) {
        if (needed > rxData.size) {
            var newSize = rxData.size * 2
            while (newSize < needed) newSize *= 2
            rxData = rxData.copyOf(newSize)
        }
        while (rxCount < needed) {
            val toRead = minOf(rxData.size - rxCount, CHUNK_SIZE)
            val n = connection.bulkTransfer(
                ptpInterface.bulkIn, rxData, rxCount, toRead, TIMEOUT_MS
            )
            if (n < 0) throw IOException("USB read failed (camera disconnected or timed out)")
            if (n == 0) continue
            rxCount += n
        }
    }

    private fun readContainer(): Container {
        ensureRxBytes(12)
        val length = (rxData[0].toLong() and 0xFF) or
            ((rxData[1].toLong() and 0xFF) shl 8) or
            ((rxData[2].toLong() and 0xFF) shl 16) or
            ((rxData[3].toLong() and 0xFF) shl 24)
        val type = (rxData[4].toInt() and 0xFF) or ((rxData[5].toInt() and 0xFF) shl 8)
        val code = (rxData[6].toInt() and 0xFF) or ((rxData[7].toInt() and 0xFF) shl 8)
        val tx = (rxData[8].toInt() and 0xFF) or
            ((rxData[9].toInt() and 0xFF) shl 8) or
            ((rxData[10].toInt() and 0xFF) shl 16) or
            ((rxData[11].toInt() and 0xFF) shl 24)

        if (length < 12 || length > MAX_CONTAINER_SIZE) {
            throw IOException("Invalid PTP container length $length")
        }

        val payloadLen = (length - 12).toInt()
        val payload = ByteArray(payloadLen)

        if (length <= 256 * 1024) {
            ensureRxBytes(length.toInt())
            System.arraycopy(rxData, 12, payload, 0, payloadLen)

            // Shift leftover bytes to index 0 so the next container always starts at index 0
            val remaining = rxCount - length.toInt()
            if (remaining > 0) {
                System.arraycopy(rxData, length.toInt(), rxData, 0, remaining)
            }
            rxCount = remaining
        } else {
            // Large payload (full-resolution captured photo)
            val availablePayload = minOf(rxCount - 12, payloadLen)
            if (availablePayload > 0) {
                System.arraycopy(rxData, 12, payload, 0, availablePayload)
            }
            rxCount = 0

            var got = availablePayload
            while (got < payloadLen) {
                val toRead = minOf(payloadLen - got, CHUNK_SIZE)
                val n = connection.bulkTransfer(ptpInterface.bulkIn, payload, got, toRead, TIMEOUT_PHOTO_MS)
                if (n < 0) throw IOException("USB read failed while downloading photo payload")
                if (n > 0) got += n
            }
        }

        return Container(type, code, tx, payload)
    }

    companion object {
        private const val TAG = "SonyPTP"
        private val EMPTY = ByteArray(0)
        private const val TIMEOUT_MS = 5000
        private const val TIMEOUT_PHOTO_MS = 10000
        private const val CHUNK_SIZE = 16 * 1024
        private const val MAX_CONTAINER_SIZE = 128L * 1024 * 1024

        /** Finds the PTP (Still Image class 6, or vendor class with a bulk pair) interface. */
        fun locate(device: UsbDevice): PtpInterface? {
            var fallback: PtpInterface? = null
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                var bulkIn: UsbEndpoint? = null
                var bulkOut: UsbEndpoint? = null
                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) bulkIn = ep else bulkOut = ep
                }
                if (bulkIn == null || bulkOut == null) continue
                val candidate = PtpInterface(iface, bulkIn, bulkOut)
                when (iface.interfaceClass) {
                    UsbConstants.USB_CLASS_STILL_IMAGE -> return candidate
                    UsbConstants.USB_CLASS_VENDOR_SPEC -> if (fallback == null) fallback = candidate
                }
            }
            return fallback
        }

        /** Opens the device and claims its PTP interface. Needs USB permission already granted. */
        fun open(usbManager: UsbManager, device: UsbDevice): PtpUsbTransport {
            val ptpIface = locate(device)
                ?: throw IOException("Device has no PTP interface (is the camera in PC Remote mode?)")
            val connection = usbManager.openDevice(device)
                ?: throw IOException("Cannot open USB device (permission missing?)")
            if (!connection.claimInterface(ptpIface.iface, true)) {
                connection.close()
                throw IOException("Cannot claim the camera's USB interface (is another app using it?)")
            }
            val transport = PtpUsbTransport(connection, ptpIface)
            transport.drainStaleData()
            Log.d(TAG, "Opened PTP transport on USB device: ${device.deviceName}")
            return transport
        }
    }
}
