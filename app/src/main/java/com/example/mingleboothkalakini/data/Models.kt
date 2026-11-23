package com.example.mingleboothkalakini.data

import android.graphics.Bitmap

object AppConfig {
    const val SUPABASE_URL = "https://qnwgftksanobuducqebx.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFud2dmdGtzYW5vYnVkdWNxZWJ4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI0MTY2MDUsImV4cCI6MjEwNzk5MjYwNX0._7yO7t-WxqxX0OJau91TFJal07HHrAR3w3Hp-n9O9gU"

    const val CLOUDINARY_CLOUD_NAME = "gnbvoihw"
    const val CLOUDINARY_UPLOAD_PRESET = "mingle_booth"
    const val CLOUDINARY_FOLDER = "mingle-booth"
    const val CLOUDINARY_API_KEY = "174247797413655"
    const val CLOUDINARY_API_SECRET = "9jynNu7L3ifyO6aedWG8mIN8GiY"

    const val WEB_VIEWER_BASE_URL = "https://mingle-booth.vercel.app"
}

data class Slot(
    val slotIndex: Int,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int
)

data class FrameItem(
    val id: String,
    val eventId: String,
    val name: String,
    val dataUrl: String,
    val width: Int,
    val height: Int,
    val slots: List<Slot>,
    val isActive: Boolean
)

data class EventItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val brandLogoUrl: String?,
    val stripBgColor: String,
    val stripTextColor: String,
    val stripShowTitle: Boolean,
    val stripShowDate: Boolean,
    val defaultLayout: String,
    val cameraDeviceId: String?,
    val cameraResolution: String,
    val mirrorCamera: Boolean,
    val mirrorResult: Boolean,
    val saveToLocalGallery: Boolean = false,
    val isActive: Boolean,
    val createdAt: String,
    val sessionCount: Int = 0
) {
    val galleryUrl: String
        get() = "${AppConfig.WEB_VIEWER_BASE_URL}/e/$id"
}

data class SessionPhotoItem(
    val slotIndex: Int,
    val url: String,
    val slotX: Int,
    val slotY: Int,
    val slotW: Int,
    val slotH: Int
)

data class PhotoSessionItem(
    val id: String,
    val eventId: String,
    val layout: String,
    val photoCount: Int,
    val stripUrl: String,
    val gifUrl: String?,
    val viewerUrl: String,
    val stripW: Int,
    val stripH: Int,
    val frameUrl: String?,
    val createdAt: String,
    val photos: List<SessionPhotoItem> = emptyList()
)

data class BoothSettings(
    val eventId: String? = null,
    val hasActiveEvent: Boolean = false,
    val cameraFacing: String = "FRONT", // FRONT, BACK, EXTERNAL
    val cameraResolution: String = "1920x1080",
    val mirrorCamera: Boolean = true,
    val mirrorResult: Boolean = true,
    val saveToLocalGallery: Boolean = false,
    val eventTitle: String = "Mingle Booth",
    val eventSubtitle: String = "by Kala Kini",
    val brandLogoUrl: String? = null,
    val stripBgColor: String = "#1a1a28",
    val stripTextColor: String = "#ffffff",
    val stripShowTitle: Boolean = true,
    val stripShowDate: Boolean = true,
    val activeFrame: FrameItem? = null,
    val frames: List<FrameItem> = emptyList()
)

data class BoothResult(
    val sessionId: String,
    val stripBitmap: Bitmap,
    val gifBytes: ByteArray?,
    val viewerUrl: String,
    val qrBitmap: Bitmap,
    val cloudStripUrl: String?,
    val cloudGifUrl: String?
)

data class CameraDeviceOption(
    val id: String,
    val label: String,
    val isExternal: Boolean = false
)

