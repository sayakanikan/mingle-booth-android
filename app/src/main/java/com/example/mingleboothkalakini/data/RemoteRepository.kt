package com.example.mingleboothkalakini.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.mingleboothkalakini.util.BoothUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

object RemoteRepository {
    // Fast-fail client for lightweight Supabase REST JSON queries with HTTP/2 keep-alive pings
    private val dbHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Generous client for Cloudinary image/GIF uploads and downloads
    private val uploadHttp = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun resetConnections() {
        dbHttp.connectionPool.evictAll()
        uploadHttp.connectionPool.evictAll()
    }

    private fun formatDbError(e: Throwable): String = when (e) {
        is SocketTimeoutException -> "Database connection timed out"
        is UnknownHostException -> "No internet / cannot reach database"
        is ConnectException -> "Failed to connect to database"
        is SSLException -> "Secure connection interrupted"
        is IOException -> "Network error: ${e.message ?: "connection dropped"}"
        else -> e.message ?: "Database error"
    }

    private suspend fun <T> executeDbWithRecovery(block: () -> T): T {
        return try {
            block()
        } catch (e: IOException) {
            // Stale/half-open HTTP/2 or TCP socket in pool after venue Wi-Fi fluctuation:
            // evict pooled connections and retry once on a fresh socket.
            dbHttp.connectionPool.evictAll()
            delay(250)
            block()
        }
    }

    private fun supabaseRequest(pathAndQuery: String): Request.Builder {
        return Request.Builder()
            .url("${AppConfig.SUPABASE_URL}/rest/v1/$pathAndQuery")
            .header("apikey", AppConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${AppConfig.SUPABASE_ANON_KEY}")
    }

    suspend fun loadBoothSettings(): BoothSettings {
        return when (val res = loadBoothSettingsResult()) {
            is DbResult.Success -> res.data
            is DbResult.Error -> BoothSettings()
        }
    }

    suspend fun loadBoothSettingsResult(): DbResult<BoothSettings> = withContext(Dispatchers.IO) {
        try {
            val eventsArr = getJsonArray("events?select=*&order=is_active.desc,created_at.desc")
            var activeEvent: JSONObject? = null
            for (i in 0 until eventsArr.length()) {
                val obj = eventsArr.getJSONObject(i)
                if (obj.optBoolean("is_active", false)) {
                    activeEvent = obj
                    break
                }
            }

            val framesArr = getJsonArray("frames?select=*&order=created_at.asc")
            val frames = mutableListOf<FrameItem>()
            for (i in 0 until framesArr.length()) {
                frames.add(parseFrame(framesArr.getJSONObject(i)))
            }
            val activeFrame = frames.firstOrNull { it.isActive } ?: frames.firstOrNull()

            if (activeEvent == null) {
                return@withContext DbResult.Success(
                    BoothSettings(
                        eventId = null,
                        hasActiveEvent = false,
                        eventTitle = "No Active Event",
                        eventSubtitle = "Please activate an event in Admin",
                        activeFrame = activeFrame,
                        frames = frames
                    )
                )
            }

            val camDev = activeEvent.optString("camera_device_id", "FRONT").trim()
            val facing = when {
                camDev.isBlank() || camDev == "null" -> "FRONT"
                camDev.equals("SONY_PC_REMOTE", ignoreCase = true) -> "EXTERNAL"
                camDev.equals("FRONT", ignoreCase = true) -> "FRONT"
                camDev.equals("BACK", ignoreCase = true) -> "BACK"
                camDev.equals("EXTERNAL", ignoreCase = true) -> "EXTERNAL"
                camDev.startsWith("ID:", ignoreCase = true) -> "ID:${camDev.substringAfter(":")}"
                else -> "EXTERNAL"
            }

            DbResult.Success(
                BoothSettings(
                    eventId = activeEvent.optString("id", "default"),
                    hasActiveEvent = true,
                    cameraFacing = facing,
                    cameraResolution = activeEvent.optString("camera_resolution", "1920x1080").ifBlank { "1920x1080" },
                    mirrorCamera = activeEvent.optBoolean("mirror_camera", true),
                    mirrorResult = activeEvent.optBoolean("mirror_result", true),
                    saveToLocalGallery = activeEvent.optBoolean("save_to_local_gallery", activeEvent.optBoolean("saveToLocalGallery", false)),
                    eventTitle = activeEvent.optString("title", "Mingle Booth").ifBlank { "Mingle Booth" },
                    eventSubtitle = activeEvent.optString("subtitle", "by Kala Kini").ifBlank { "by Kala Kini" },
                    brandLogoUrl = activeEvent.optString("brand_logo_data_url", "").takeIf { it.isNotBlank() && it != "null" },
                    stripBgColor = activeEvent.optString("strip_bg_color", "#1a1a28").ifBlank { "#1a1a28" },
                    stripTextColor = activeEvent.optString("strip_text_color", "#ffffff").ifBlank { "#ffffff" },
                    stripShowTitle = activeEvent.optBoolean("strip_show_title", true),
                    stripShowDate = activeEvent.optBoolean("strip_show_date", true),
                    activeFrame = activeFrame,
                    frames = frames
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            DbResult.Error(formatDbError(e), e)
        }
    }

    suspend fun verifyAdminPassword(password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val arr = getJsonArray("events?id=eq.default&select=admin_password,admin_password_hash")
            if (arr.length() == 0) return@withContext password == "admin1234"
            val row = arr.getJSONObject(0)
            val plain = row.optString("admin_password", "").takeIf { it.isNotBlank() && it != "null" }
            val hash = row.optString("admin_password_hash", "").takeIf { it.isNotBlank() && it != "null" }
            when {
                plain != null -> password == plain
                hash != null && !hash.contains(":") -> password == hash
                else -> password == "admin1234" || password == plain
            }
        } catch (e: Exception) {
            password == "admin1234"
        }
    }

    suspend fun getEvents(): List<EventItem> {
        return when (val res = getEventsResult()) {
            is DbResult.Success -> res.data
            is DbResult.Error -> emptyList()
        }
    }

    suspend fun getEventsResult(): DbResult<List<EventItem>> = withContext(Dispatchers.IO) {
        try {
            val arr = getJsonArray("events?select=*,photo_sessions(id)&order=is_active.desc,created_at.desc")
            val list = mutableListOf<EventItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val sessionsArr = o.optJSONArray("photo_sessions")
                list.add(
                    EventItem(
                        id = o.optString("id"),
                        title = o.optString("title", "Mingle Booth"),
                        subtitle = o.optString("subtitle", "by Kala Kini"),
                        brandLogoUrl = o.optString("brand_logo_data_url", "").takeIf { it.isNotBlank() && it != "null" },
                        stripBgColor = o.optString("strip_bg_color", "#1a1a28"),
                        stripTextColor = o.optString("strip_text_color", "#ffffff"),
                        stripShowTitle = o.optBoolean("strip_show_title", true),
                        stripShowDate = o.optBoolean("strip_show_date", true),
                        defaultLayout = o.optString("default_layout", "3v"),
                        cameraDeviceId = o.optString("camera_device_id", "FRONT"),
                        cameraResolution = o.optString("camera_resolution", "1920x1080"),
                        mirrorCamera = o.optBoolean("mirror_camera", true),
                        mirrorResult = o.optBoolean("mirror_result", true),
                        saveToLocalGallery = o.optBoolean("save_to_local_gallery", o.optBoolean("saveToLocalGallery", false)),
                        isActive = o.optBoolean("is_active", false),
                        createdAt = o.optString("created_at", ""),
                        sessionCount = sessionsArr?.length() ?: 0
                    )
                )
            }
            DbResult.Success(list)
        } catch (e: Exception) {
            e.printStackTrace()
            DbResult.Error(formatDbError(e), e)
        }
    }

    suspend fun createEvent(title: String, subtitle: String, activate: Boolean): Boolean {
        return createEventResult(title, subtitle, activate) is DbResult.Success
    }

    suspend fun createEventResult(title: String, subtitle: String, activate: Boolean): DbResult<Unit> = withContext(Dispatchers.IO) {
        try {
            if (activate) {
                patchJson("events?is_active=eq.true", JSONObject().put("is_active", false))
            }
            // Generate UUID once before any retry and use upsert so retries never create duplicates
            val eventId = UUID.randomUUID().toString()
            val payload = JSONObject()
                .put("id", eventId)
                .put("title", title.trim())
                .put("subtitle", subtitle.trim().ifBlank { "by Kala Kini" })
                .put("is_active", activate)
            postJson("events", payload, upsert = true)
            DbResult.Success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            DbResult.Error(formatDbError(e), e)
        }
    }

    suspend fun updateEvent(
        eventId: String,
        title: String,
        subtitle: String,
        activate: Boolean
    ): Boolean {
        return updateEventResult(eventId, title, subtitle, activate) is DbResult.Success
    }

    suspend fun updateEventResult(
        eventId: String,
        title: String,
        subtitle: String,
        activate: Boolean
    ): DbResult<Unit> = withContext(Dispatchers.IO) {
        try {
            if (activate) {
                patchJson("events?is_active=eq.true", JSONObject().put("is_active", false))
            }
            val payload = JSONObject()
                .put("title", title.trim())
                .put("subtitle", subtitle.trim().ifBlank { "by Kala Kini" })
            if (activate) {
                payload.put("is_active", true)
            }
            patchJson("events?id=eq.$eventId", payload)
            DbResult.Success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            DbResult.Error(formatDbError(e), e)
        }
    }

    suspend fun activateEvent(eventId: String): Boolean {
        return activateEventResult(eventId) is DbResult.Success
    }

    suspend fun activateEventResult(eventId: String): DbResult<Unit> = withContext(Dispatchers.IO) {
        try {
            patchJson("events?is_active=eq.true", JSONObject().put("is_active", false))
            patchJson("events?id=eq.$eventId", JSONObject().put("is_active", true))
            DbResult.Success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            DbResult.Error(formatDbError(e), e)
        }
    }

    suspend fun deleteEvent(eventId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sessions = getSessions(eventId)
            sessions.forEach { deleteSession(it) }
            deleteRequest("events?id=eq.$eventId")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun activateFrame(frameId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            patchJson("frames?is_active=eq.true", JSONObject().put("is_active", false))
            patchJson("frames?id=eq.$frameId", JSONObject().put("is_active", true))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun addFrame(
        name: String,
        frameBitmap: Bitmap,
        slots: List<Slot>,
        eventId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val cloudUrl = uploadBitmapToCloudinary(
                bitmap = frameBitmap,
                subfolder = "frames",
                publicIdHint = "frame_${System.currentTimeMillis()}",
                tags = listOf("frame"),
                asPng = true
            )
            patchJson("frames?is_active=eq.true", JSONObject().put("is_active", false))

            val slotsJson = JSONArray()
            slots.forEach { s ->
                slotsJson.put(
                    JSONObject()
                        .put("slot_index", s.slotIndex)
                        .put("x", s.x)
                        .put("y", s.y)
                        .put("w", s.w)
                        .put("h", s.h)
                )
            }

            val payload = JSONObject()
                .put("event_id", eventId.ifBlank { "default" })
                .put("name", name)
                .put("data_url", cloudUrl)
                .put("width", frameBitmap.width)
                .put("height", frameBitmap.height)
                .put("slots", slotsJson)
                .put("is_active", true)

            postJson("frames", payload)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteFrame(frameId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            deleteRequest("frames?id=eq.$frameId")
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearActiveFrame(): Boolean = withContext(Dispatchers.IO) {
        try {
            patchJson("frames?is_active=eq.true", JSONObject().put("is_active", false))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateFrameSlots(
        frameId: String,
        slots: List<Slot>,
        width: Int? = null,
        height: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val slotsJson = JSONArray()
            slots.sortedBy { it.slotIndex }.forEachIndexed { idx, s ->
                slotsJson.put(
                    JSONObject()
                        .put("slot_index", idx)
                        .put("x", s.x)
                        .put("y", s.y)
                        .put("w", s.w)
                        .put("h", s.h)
                )
            }
            val payload = JSONObject().put("slots", slotsJson)
            if (width != null) payload.put("width", width)
            if (height != null) payload.put("height", height)
            patchJson("frames?id=eq.$frameId", payload)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateCameraAndBrandingSettings(
        eventId: String,
        cameraFacing: String,
        cameraResolution: String,
        mirrorCamera: Boolean,
        mirrorResult: Boolean,
        stripBgColor: String,
        stripTextColor: String,
        stripShowTitle: Boolean,
        stripShowDate: Boolean,
        saveToLocalGallery: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = eventId.ifBlank { "default" }
            val payload = JSONObject()
                .put("camera_device_id", cameraFacing)
                .put("camera_resolution", cameraResolution)
                .put("mirror_camera", mirrorCamera)
                .put("mirror_result", mirrorResult)
                .put("strip_bg_color", stripBgColor)
                .put("strip_text_color", stripTextColor)
                .put("strip_show_title", stripShowTitle)
                .put("strip_show_date", stripShowDate)
                .put("save_to_local_gallery", saveToLocalGallery)
            patchJson("events?id=eq.$target", payload)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getSessions(eventId: String? = null): List<PhotoSessionItem> {
        return when (val res = getSessionsResult(eventId)) {
            is DbResult.Success -> res.data
            is DbResult.Error -> emptyList()
        }
    }

    suspend fun getSessionsResult(eventId: String? = null): DbResult<List<PhotoSessionItem>> = withContext(Dispatchers.IO) {
        try {
            val filter = if (!eventId.isNullOrBlank()) "&event_id=eq.$eventId" else ""
            val arr = getJsonArray("photo_sessions?select=*,session_photos(*)$filter&order=created_at.desc&limit=100")
            val list = mutableListOf<PhotoSessionItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val photosArr = o.optJSONArray("session_photos") ?: JSONArray()
                val photos = mutableListOf<SessionPhotoItem>()
                for (j in 0 until photosArr.length()) {
                    val p = photosArr.getJSONObject(j)
                    photos.add(
                        SessionPhotoItem(
                            slotIndex = p.optInt("slot_index", j),
                            url = p.optString("url", ""),
                            slotX = p.optInt("slot_x", 0),
                            slotY = p.optInt("slot_y", 0),
                            slotW = p.optInt("slot_w", 0),
                            slotH = p.optInt("slot_h", 0)
                        )
                    )
                }
                val id = o.optString("id")
                val rawViewer = o.optString("viewer_url", "")
                val viewerUrl = if (rawViewer.startsWith("http")) rawViewer else "${AppConfig.WEB_VIEWER_BASE_URL}/s/$id"
                list.add(
                    PhotoSessionItem(
                        id = id,
                        eventId = o.optString("event_id", "default"),
                        layout = o.optString("layout", "3v"),
                        photoCount = o.optInt("photo_count", photos.size),
                        stripUrl = o.optString("strip_url", ""),
                        gifUrl = o.optString("gif_url", "").takeIf { it.isNotBlank() && it != "null" },
                        viewerUrl = viewerUrl,
                        stripW = o.optInt("strip_w", 800),
                        stripH = o.optInt("strip_h", 2400),
                        frameUrl = o.optString("frame_url", "").takeIf { it.isNotBlank() && it != "null" },
                        createdAt = o.optString("created_at", ""),
                        photos = photos.sortedBy { it.slotIndex }
                    )
                )
            }
            DbResult.Success(list)
        } catch (e: Exception) {
            e.printStackTrace()
            DbResult.Error(formatDbError(e), e)
        }
    }

    suspend fun uploadAndSaveSession(
        sessionId: String,
        settings: BoothSettings,
        stripBitmap: Bitmap,
        gifBytes: ByteArray?,
        originalBitmaps: List<Bitmap>,
        onProgress: (String) -> Unit
    ): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val slug = settings.eventTitle.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifBlank { "event" }
        val commonTags = listOf("mingle-booth", slug, "session_$sessionId")

        onProgress("Uploading photo strip…")
        val cloudStripUrl = try {
            uploadBitmapToCloudinary(
                bitmap = stripBitmap,
                subfolder = "strips",
                publicIdHint = "${slug}_strip_$sessionId",
                tags = commonTags + "strip",
                asPng = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        var cloudGifUrl: String? = null
        if (gifBytes != null) {
            onProgress("Uploading animated GIF…")
            cloudGifUrl = try {
                uploadBytesToCloudinary(
                    bytes = gifBytes,
                    mimeType = "image/gif",
                    fileName = "anim_$sessionId.gif",
                    subfolder = "gifs",
                    publicIdHint = "${slug}_gif_$sessionId",
                    tags = commonTags + "gif"
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        val originalUrls = mutableListOf<String>()
        for (i in originalBitmaps.indices) {
            onProgress("Uploading shot ${i + 1} of ${originalBitmaps.size}…")
            try {
                val url = uploadBitmapToCloudinary(
                    bitmap = originalBitmaps[i],
                    subfolder = "originals/$sessionId",
                    publicIdHint = "${slug}_photo_${sessionId}_${i + 1}",
                    tags = commonTags + listOf("original", "shot_${i + 1}"),
                    asPng = false
                )
                originalUrls.add(url)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        onProgress("Saving session to Supabase…")
        val viewerUrl = "${AppConfig.WEB_VIEWER_BASE_URL}/s/$sessionId"
        val activeFrame = settings.activeFrame
        val sortedSlots = activeFrame?.slots?.sortedBy { it.slotIndex } ?: emptyList()

        try {
            val sessionJson = JSONObject()
                .put("id", sessionId)
                .put("event_id", settings.eventId ?: "default")
                .put("layout", "frame:${activeFrame?.id ?: "custom"}")
                .put("photo_count", originalBitmaps.size)
                .put("strip_url", cloudStripUrl ?: "")
                .put("gif_url", cloudGifUrl)
                .put("viewer_url", viewerUrl)
                .put("strip_w", stripBitmap.width)
                .put("strip_h", stripBitmap.height)
                .put("frame_url", activeFrame?.id)

            postJson("photo_sessions", sessionJson)

            if (originalUrls.isNotEmpty()) {
                val photosArray = JSONArray()
                for (i in originalUrls.indices) {
                    val slot = sortedSlots.getOrNull(i)
                    photosArray.put(
                        JSONObject()
                            .put("session_id", sessionId)
                            .put("slot_index", i)
                            .put("url", originalUrls[i])
                            .put("slot_x", slot?.x ?: 0)
                            .put("slot_y", slot?.y ?: 0)
                            .put("slot_w", slot?.w ?: 0)
                            .put("slot_h", slot?.h ?: 0)
                    )
                }
                postJsonArray("session_photos", photosArray)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Pair(cloudStripUrl, cloudGifUrl)
    }

    suspend fun deleteSession(session: PhotoSessionItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val urls = mutableListOf<String>()
            if (session.stripUrl.isNotBlank()) urls.add(session.stripUrl)
            if (!session.gifUrl.isNullOrBlank()) urls.add(session.gifUrl)
            session.photos.forEach { if (it.url.isNotBlank()) urls.add(it.url) }

            urls.mapNotNull { extractCloudinaryPublicId(it) }.distinct().forEach { publicId ->
                destroyCloudinaryAsset(publicId)
            }

            deleteRequest("photo_sessions?id=eq.${session.id}")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun regenerateSession(
        session: PhotoSessionItem,
        settings: BoothSettings,
        frameCache: MutableMap<String, Bitmap>? = null,
        onProgress: (String) -> Unit
    ): PhotoSessionItem? = withContext(Dispatchers.IO) {
        try {
            var rawPhotos = session.photos.sortedBy { it.slotIndex }
            if (rawPhotos.isEmpty()) {
                onProgress("Fetching raw photos list from database…")
                val queriedArr = getJsonArray("session_photos?session_id=eq.${session.id}&order=slot_index.asc")
                val fetched = mutableListOf<SessionPhotoItem>()
                for (j in 0 until queriedArr.length()) {
                    val p = queriedArr.getJSONObject(j)
                    fetched.add(
                        SessionPhotoItem(
                            slotIndex = p.optInt("slot_index", j),
                            url = p.optString("url", ""),
                            slotX = p.optInt("slot_x", 0),
                            slotY = p.optInt("slot_y", 0),
                            slotW = p.optInt("slot_w", 0),
                            slotH = p.optInt("slot_h", 0)
                        )
                    )
                }
                rawPhotos = fetched.sortedBy { it.slotIndex }
            }

            if (rawPhotos.isEmpty()) {
                onProgress("No raw photos found for session #${session.id.takeLast(6).uppercase()}")
                return@withContext null
            }

            // Download original raw photos
            val downloadedBitmaps = mutableListOf<Bitmap>()
            for (i in rawPhotos.indices) {
                onProgress("Downloading raw photo ${i + 1} of ${rawPhotos.size}…")
                val bmp = downloadBitmap(rawPhotos[i].url)
                if (bmp == null) {
                    onProgress("Failed to download raw photo ${i + 1}")
                    return@withContext null
                }
                downloadedBitmaps.add(bmp)
            }

            // Determine matching frame and frame artwork
            val matchedFrame = settings.frames.firstOrNull { it.id == session.frameUrl } ?: settings.activeFrame
            val effectiveSettings = settings.copy(activeFrame = matchedFrame)

            val frameBitmap = if (!matchedFrame?.dataUrl.isNullOrBlank()) {
                val frameUrl = matchedFrame!!.dataUrl
                if (frameCache != null && frameCache.containsKey(frameUrl)) {
                    frameCache[frameUrl]
                } else {
                    onProgress("Loading frame overlay…")
                    val downloaded = downloadBitmap(frameUrl)
                    if (downloaded != null && frameCache != null) {
                        frameCache[frameUrl] = downloaded
                    }
                    downloaded
                }
            } else null

            // Re-composite strip
            onProgress("Compositing new photo strip…")
            val stripBitmap = BoothUtils.composeStrip(
                photos = downloadedBitmaps,
                settings = effectiveSettings,
                cachedFrameBitmap = frameBitmap
            )

            // Re-encode animated GIF in 720p HD (1280x720) Hi-Res Composite
            onProgress("Encoding animated GIF (720p Hi-Res Composite)…")
            val gifBytes = BoothUtils.buildAnimatedGif(downloadedBitmaps, width = 1280, height = 720)

            val slug = settings.eventTitle.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifBlank { "event" }
            val commonTags = listOf("mingle-booth", slug, "session_${session.id}", "regenerated")

            // Upload new strip
            onProgress("Uploading new photo strip…")
            val newStripUrl = uploadBitmapToCloudinary(
                bitmap = stripBitmap,
                subfolder = "strips",
                publicIdHint = "${slug}_strip_${session.id}_regen_${System.currentTimeMillis()}",
                tags = commonTags + "strip",
                asPng = false
            )

            // Upload new GIF
            var newGifUrl: String? = null
            if (gifBytes != null) {
                onProgress("Uploading new animated GIF…")
                newGifUrl = uploadBytesToCloudinary(
                    bytes = gifBytes,
                    mimeType = "image/gif",
                    fileName = "anim_${session.id}.gif",
                    subfolder = "gifs",
                    publicIdHint = "${slug}_gif_${session.id}_regen_${System.currentTimeMillis()}",
                    tags = commonTags + "gif"
                )
            }

            // Update database record in photo_sessions
            onProgress("Updating session database record…")
            val updatePayload = JSONObject()
                .put("strip_url", newStripUrl)
                .put("strip_w", stripBitmap.width)
                .put("strip_h", stripBitmap.height)
            if (newGifUrl != null) {
                updatePayload.put("gif_url", newGifUrl)
            }
            patchJson("photo_sessions?id=eq.${session.id}", updatePayload)

            // Clean up old assets if they were on Cloudinary
            val oldAssets = listOfNotNull(
                session.stripUrl.takeIf { it != newStripUrl },
                session.gifUrl.takeIf { it != newGifUrl }
            )
            oldAssets.mapNotNull { extractCloudinaryPublicId(it) }.distinct().forEach { publicId ->
                destroyCloudinaryAsset(publicId)
            }

            session.copy(
                stripUrl = newStripUrl,
                gifUrl = newGifUrl ?: session.gifUrl,
                stripW = stripBitmap.width,
                stripH = stripBitmap.height,
                photos = rawPhotos
            )
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress("Error: ${e.message}")
            null
        }
    }

    suspend fun downloadBitmap(urlOrDataUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (urlOrDataUrl.startsWith("data:image")) {
                val base64Part = urlOrDataUrl.substringAfter(",")
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            val req = Request.Builder().url(urlOrDataUrl).build()
            uploadHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadBytes(urlOrDataUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (urlOrDataUrl.startsWith("data:")) {
                val base64Part = urlOrDataUrl.substringAfter(",")
                return@withContext Base64.decode(base64Part, Base64.DEFAULT)
            }
            val req = Request.Builder().url(urlOrDataUrl).build()
            uploadHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.bytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun uploadBitmapToCloudinary(
        bitmap: Bitmap,
        subfolder: String,
        publicIdHint: String,
        tags: List<String>,
        asPng: Boolean
    ): String {
        val out = ByteArrayOutputStream()
        if (asPng) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } else {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return uploadBytesToCloudinary(
            bytes = out.toByteArray(),
            mimeType = if (asPng) "image/png" else "image/jpeg",
            fileName = if (asPng) "$publicIdHint.png" else "$publicIdHint.jpg",
            subfolder = subfolder,
            publicIdHint = publicIdHint,
            tags = tags
        )
    }

    private fun uploadBytesToCloudinary(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        subfolder: String,
        publicIdHint: String,
        tags: List<String>
    ): String {
        val folder = if (subfolder.isNotBlank()) "${AppConfig.CLOUDINARY_FOLDER}/$subfolder" else AppConfig.CLOUDINARY_FOLDER
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .addFormDataPart("upload_preset", AppConfig.CLOUDINARY_UPLOAD_PRESET)
            .addFormDataPart("folder", folder)
            .addFormDataPart("public_id", publicIdHint)

        if (tags.isNotEmpty()) {
            bodyBuilder.addFormDataPart("tags", tags.joinToString(","))
        }

        val req = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/${AppConfig.CLOUDINARY_CLOUD_NAME}/upload")
            .post(bodyBuilder.build())
            .build()

        uploadHttp.newCall(req).execute().use { resp ->
            val respText = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IllegalStateException("Cloudinary upload failed (${resp.code}): $respText")
            }
            val json = JSONObject(respText)
            return json.getString("secure_url")
        }
    }

    private fun extractCloudinaryPublicId(url: String): String? {
        if (!url.contains("cloudinary.com")) return null
        val regex = Regex("/upload/(?:v\\d+/)?(.+?)(\\.[a-zA-Z0-9]+)?$")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    private fun destroyCloudinaryAsset(publicId: String) {
        try {
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val toSign = "public_id=$publicId&timestamp=$timestamp${AppConfig.CLOUDINARY_API_SECRET}"
            val sha1 = MessageDigest.getInstance("SHA-1").digest(toSign.toByteArray())
                .joinToString("") { "%02x".format(it) }

            val form = okhttp3.FormBody.Builder()
                .add("public_id", publicId)
                .add("timestamp", timestamp)
                .add("api_key", AppConfig.CLOUDINARY_API_KEY)
                .add("signature", sha1)
                .build()

            val req = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/${AppConfig.CLOUDINARY_CLOUD_NAME}/image/destroy")
                .post(form)
                .build()

            uploadHttp.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    private fun parseFrame(o: JSONObject): FrameItem {
        val slots = mutableListOf<Slot>()
        val rawSlots = o.opt("slots")
        val slotsArr = when (rawSlots) {
            is JSONArray -> rawSlots
            is String -> try { JSONArray(rawSlots) } catch (_: Exception) { JSONArray() }
            else -> JSONArray()
        }
        for (i in 0 until slotsArr.length()) {
            val s = slotsArr.getJSONObject(i)
            slots.add(
                Slot(
                    slotIndex = s.optInt("slot_index", i),
                    x = s.optInt("x", 0),
                    y = s.optInt("y", 0),
                    w = s.optInt("w", 400),
                    h = s.optInt("h", 300)
                )
            )
        }
        return FrameItem(
            id = o.optString("id"),
            eventId = o.optString("event_id", "default"),
            name = o.optString("name", "Frame"),
            dataUrl = o.optString("data_url", ""),
            width = o.optInt("width", 1200).takeIf { it > 0 } ?: 1200,
            height = o.optInt("height", 3600).takeIf { it > 0 } ?: 3600,
            slots = slots.sortedBy { it.slotIndex },
            isActive = o.optBoolean("is_active", false)
        )
    }

    private suspend fun getJsonArray(pathAndQuery: String): JSONArray = executeDbWithRecovery {
        val req = supabaseRequest(pathAndQuery).get().build()
        dbHttp.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: "[]"
            if (!resp.isSuccessful) throw IllegalStateException("Supabase GET failed (${resp.code}): $body")
            JSONArray(body)
        }
    }

    private suspend fun postJson(table: String, json: JSONObject, upsert: Boolean = false) = executeDbWithRecovery {
        val preferHeader = if (upsert) "resolution=merge-duplicates,return=minimal" else "return=minimal"
        val req = supabaseRequest(table)
            .header("Prefer", preferHeader)
            .post(json.toString().toRequestBody(jsonMedia))
            .build()
        dbHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Supabase POST failed (${resp.code}): ${resp.body?.string()}")
            }
        }
    }

    private suspend fun postJsonArray(table: String, jsonArray: JSONArray) = executeDbWithRecovery {
        val req = supabaseRequest(table)
            .header("Prefer", "return=minimal")
            .post(jsonArray.toString().toRequestBody(jsonMedia))
            .build()
        dbHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Supabase POST array failed (${resp.code}): ${resp.body?.string()}")
            }
        }
    }

    private suspend fun patchJson(pathAndQuery: String, json: JSONObject) = executeDbWithRecovery {
        val req = supabaseRequest(pathAndQuery)
            .header("Prefer", "return=minimal")
            .patch(json.toString().toRequestBody(jsonMedia))
            .build()
        dbHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Supabase PATCH failed (${resp.code}): ${resp.body?.string()}")
            }
        }
    }

    private suspend fun deleteRequest(pathAndQuery: String) = executeDbWithRecovery {
        val req = supabaseRequest(pathAndQuery).delete().build()
        dbHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Supabase DELETE failed (${resp.code}): ${resp.body?.string()}")
            }
        }
    }
}
