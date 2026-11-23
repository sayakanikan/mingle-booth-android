package com.example.mingleboothkalakini.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object BoothSettingsCache {
    private const val PREFS_NAME = "mingle_booth_cache"
    private const val KEY_SETTINGS_JSON = "last_good_settings_json"
    private const val FRAME_CACHE_FILENAME = "cached_active_frame.png"

    fun saveSettings(context: Context, settings: BoothSettings) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!settings.hasActiveEvent) {
                prefs.edit().remove(KEY_SETTINGS_JSON).apply()
                File(context.filesDir, FRAME_CACHE_FILENAME).delete()
                return
            }

            val root = JSONObject().apply {
                put("eventId", settings.eventId ?: "")
                put("hasActiveEvent", settings.hasActiveEvent)
                put("cameraFacing", settings.cameraFacing)
                put("cameraResolution", settings.cameraResolution)
                put("mirrorCamera", settings.mirrorCamera)
                put("mirrorResult", settings.mirrorResult)
                put("saveToLocalGallery", settings.saveToLocalGallery)
                put("eventTitle", settings.eventTitle)
                put("eventSubtitle", settings.eventSubtitle)
                put("brandLogoUrl", settings.brandLogoUrl ?: JSONObject.NULL)
                put("stripBgColor", settings.stripBgColor)
                put("stripTextColor", settings.stripTextColor)
                put("stripShowTitle", settings.stripShowTitle)
                put("stripShowDate", settings.stripShowDate)
                put("activeFrameId", settings.activeFrame?.id ?: "")

                val framesArr = JSONArray()
                settings.frames.forEach { f ->
                    framesArr.put(frameToJson(f))
                }
                put("frames", framesArr)
            }
            prefs.edit().putString(KEY_SETTINGS_JSON, root.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSettings(context: Context): BoothSettings? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SETTINGS_JSON, null) ?: return null
            val o = JSONObject(raw)
            if (!o.optBoolean("hasActiveEvent", false)) return null

            val frames = mutableListOf<FrameItem>()
            val framesArr = o.optJSONArray("frames") ?: JSONArray()
            for (i in 0 until framesArr.length()) {
                frames.add(jsonToFrame(framesArr.getJSONObject(i)))
            }
            val activeFrameId = o.optString("activeFrameId", "")
            val activeFrame = frames.firstOrNull { it.id == activeFrameId }
                ?: frames.firstOrNull { it.isActive }
                ?: frames.firstOrNull()

            BoothSettings(
                eventId = o.optString("eventId", "").ifBlank { null },
                hasActiveEvent = true,
                cameraFacing = o.optString("cameraFacing", "FRONT"),
                cameraResolution = o.optString("cameraResolution", "1920x1080"),
                mirrorCamera = o.optBoolean("mirrorCamera", true),
                mirrorResult = o.optBoolean("mirrorResult", true),
                saveToLocalGallery = o.optBoolean("saveToLocalGallery", false),
                eventTitle = o.optString("eventTitle", "Mingle Booth"),
                eventSubtitle = o.optString("eventSubtitle", "by Kala Kini"),
                brandLogoUrl = o.optString("brandLogoUrl", "").takeIf { it.isNotBlank() && it != "null" },
                stripBgColor = o.optString("stripBgColor", "#1a1a28"),
                stripTextColor = o.optString("stripTextColor", "#ffffff"),
                stripShowTitle = o.optBoolean("stripShowTitle", true),
                stripShowDate = o.optBoolean("stripShowDate", true),
                activeFrame = activeFrame,
                frames = frames
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveFrameBitmap(context: Context, bitmap: Bitmap?) {
        try {
            val file = File(context.filesDir, FRAME_CACHE_FILENAME)
            if (bitmap == null) {
                file.delete()
                return
            }
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadFrameBitmap(context: Context): Bitmap? {
        return try {
            val file = File(context.filesDir, FRAME_CACHE_FILENAME)
            if (!file.exists()) null else BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    private fun frameToJson(f: FrameItem): JSONObject {
        val slotsArr = JSONArray()
        f.slots.forEach { s ->
            slotsArr.put(
                JSONObject()
                    .put("slotIndex", s.slotIndex)
                    .put("x", s.x)
                    .put("y", s.y)
                    .put("w", s.w)
                    .put("h", s.h)
            )
        }
        return JSONObject()
            .put("id", f.id)
            .put("eventId", f.eventId)
            .put("name", f.name)
            // Avoid writing multi-MB inline base64 dataUrl into SharedPreferences
            .put("dataUrl", if (f.dataUrl.startsWith("data:")) "" else f.dataUrl)
            .put("width", f.width)
            .put("height", f.height)
            .put("isActive", f.isActive)
            .put("slots", slotsArr)
    }

    private fun jsonToFrame(o: JSONObject): FrameItem {
        val slots = mutableListOf<Slot>()
        val arr = o.optJSONArray("slots") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            slots.add(
                Slot(
                    slotIndex = s.optInt("slotIndex", i),
                    x = s.optInt("x", 0),
                    y = s.optInt("y", 0),
                    w = s.optInt("w", 400),
                    h = s.optInt("h", 300)
                )
            )
        }
        return FrameItem(
            id = o.optString("id"),
            eventId = o.optString("eventId", "default"),
            name = o.optString("name", "Frame"),
            dataUrl = o.optString("dataUrl", ""),
            width = o.optInt("width", 1200).takeIf { it > 0 } ?: 1200,
            height = o.optInt("height", 3600).takeIf { it > 0 } ?: 3600,
            slots = slots.sortedBy { it.slotIndex },
            isActive = o.optBoolean("isActive", false)
        )
    }
}
