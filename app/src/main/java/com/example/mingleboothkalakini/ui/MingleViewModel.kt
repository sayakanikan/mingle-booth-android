package com.example.mingleboothkalakini.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mingleboothkalakini.data.*
import com.example.mingleboothkalakini.util.BoothUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BoothStep {
    SCREENSAVER,
    CAPTURE,
    REVIEW,
    PROCESSING,
    RESULT,
    ADMIN_LOGIN,
    ADMIN_DASHBOARD
}

class MingleViewModel(application: Application) : AndroidViewModel(application) {

    private val _step = MutableStateFlow(BoothStep.SCREENSAVER)
    val step: StateFlow<BoothStep> = _step.asStateFlow()

    private val _settings = MutableStateFlow(BoothSettings())
    val settings: StateFlow<BoothSettings> = _settings.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Capture & Review state
    private val _capturedPhotos = MutableStateFlow<List<Bitmap>>(emptyList())
    val capturedPhotos: StateFlow<List<Bitmap>> = _capturedPhotos.asStateFlow()

    private val _photosRevision = MutableStateFlow(0)
    val photosRevision: StateFlow<Int> = _photosRevision.asStateFlow()

    private val _retakeSlotIndex = MutableStateFlow<Int?>(null)
    val retakeSlotIndex: StateFlow<Int?> = _retakeSlotIndex.asStateFlow()

    private val _cachedFrameBitmap = MutableStateFlow<Bitmap?>(null)
    val cachedFrameBitmap: StateFlow<Bitmap?> = _cachedFrameBitmap.asStateFlow()

    // External / Bluetooth Shutter event
    private val _externalShutterTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val externalShutterTrigger: SharedFlow<Unit> = _externalShutterTrigger.asSharedFlow()

    fun triggerExternalShutter() {
        _externalShutterTrigger.tryEmit(Unit)
    }

    private val _lastModifiedSlot = MutableStateFlow(0)
    val lastModifiedSlot: StateFlow<Int> = _lastModifiedSlot.asStateFlow()

    // Processing & Result state
    private val _processingStage = MutableStateFlow(0)
    val processingStage: StateFlow<Int> = _processingStage.asStateFlow()

    private val _processingDetail = MutableStateFlow("")
    val processingDetail: StateFlow<String> = _processingDetail.asStateFlow()

    private val _boothResult = MutableStateFlow<BoothResult?>(null)
    val boothResult: StateFlow<BoothResult?> = _boothResult.asStateFlow()

    // Admin state
    private val _isAdminAuthed = MutableStateFlow(false)
    val isAdminAuthed: StateFlow<Boolean> = _isAdminAuthed.asStateFlow()

    private val _events = MutableStateFlow<List<EventItem>>(emptyList())
    val events: StateFlow<List<EventItem>> = _events.asStateFlow()

    private val _sessions = MutableStateFlow<List<PhotoSessionItem>>(emptyList())
    val sessions: StateFlow<List<PhotoSessionItem>> = _sessions.asStateFlow()

    private val _adminBusy = MutableStateFlow(false)
    val adminBusy: StateFlow<Boolean> = _adminBusy.asStateFlow()

    private val _dbError = MutableStateFlow<String?>(null)
    val dbError: StateFlow<String?> = _dbError.asStateFlow()

    private val _adminStatusMessage = MutableStateFlow("")
    val adminStatusMessage: StateFlow<String> = _adminStatusMessage.asStateFlow()

    private val _batchRegenerateState = MutableStateFlow(BatchRegenerateProgress())
    val batchRegenerateState: StateFlow<BatchRegenerateProgress> = _batchRegenerateState.asStateFlow()

    private var batchRegenerateJob: Job? = null

    init {
        val cached = BoothSettingsCache.loadSettings(getApplication())
        if (cached != null) {
            _settings.value = cached
            _cachedFrameBitmap.value = BoothSettingsCache.loadFrameBitmap(getApplication())
        }
        refreshBoothSettings()
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun refreshBoothSettings(forceResetSockets: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (forceResetSockets) {
                    RemoteRepository.resetConnections()
                }
                when (val res = RemoteRepository.loadBoothSettingsResult()) {
                    is DbResult.Success -> {
                        _dbError.value = null
                        val loaded = res.data
                        _settings.value = loaded
                        withContext(Dispatchers.IO) {
                            BoothSettingsCache.saveSettings(getApplication(), loaded)
                        }
                        val frameBmp = loaded.activeFrame?.dataUrl?.takeIf { it.isNotBlank() }?.let {
                            RemoteRepository.downloadBitmap(it)
                        }
                        if (frameBmp != null) {
                            _cachedFrameBitmap.value = frameBmp
                            withContext(Dispatchers.IO) {
                                BoothSettingsCache.saveFrameBitmap(getApplication(), frameBmp)
                            }
                        } else if (loaded.activeFrame == null) {
                            _cachedFrameBitmap.value = null
                            withContext(Dispatchers.IO) {
                                BoothSettingsCache.saveFrameBitmap(getApplication(), null)
                            }
                        }
                    }
                    is DbResult.Error -> {
                        _dbError.value = res.message
                        if (_settings.value.hasActiveEvent) {
                            showToast("Network unstable — keeping current event")
                        } else {
                            showToast("DB unreachable: ${res.message}")
                        }
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun reconnectDatabase() {
        if (_step.value == BoothStep.ADMIN_DASHBOARD) {
            loadAdminData(forceResetSockets = true)
        } else {
            refreshBoothSettings(forceResetSockets = true)
        }
    }

    fun startBoothCapture() {
        val s = _settings.value
        if (!s.hasActiveEvent) {
            openAdmin()
            return
        }
        val slots = s.activeFrame?.slots ?: emptyList()
        if (slots.isEmpty()) {
            showToast("No active frame with photo slots! Configure in Admin.")
            openAdmin()
            return
        }
        _retakeSlotIndex.value = null
        _capturedPhotos.value = emptyList()
        _lastModifiedSlot.value = 0
        _step.value = BoothStep.CAPTURE
    }

    fun onPhotosCaptured(newPhotos: List<Bitmap>) {
        val retakeIdx = _retakeSlotIndex.value
        if (retakeIdx != null && newPhotos.isNotEmpty()) {
            val current = _capturedPhotos.value.toMutableList()
            if (newPhotos.size == current.size && retakeIdx in newPhotos.indices) {
                // CaptureView passed the complete updated photo list with retake in place
                _capturedPhotos.value = newPhotos.toList()
            } else if (retakeIdx in current.indices) {
                // In case a single retaken photo was passed
                val retakenBmp = if (newPhotos.size > retakeIdx && newPhotos.size == current.size) {
                    newPhotos[retakeIdx]
                } else {
                    newPhotos.first()
                }
                current[retakeIdx] = retakenBmp
                _capturedPhotos.value = current.toList()
            } else {
                _capturedPhotos.value = newPhotos.toList()
            }
            _lastModifiedSlot.value = retakeIdx
        } else {
            _capturedPhotos.value = newPhotos.toList()
            _lastModifiedSlot.value = 0
        }
        _photosRevision.value += 1
        _retakeSlotIndex.value = null
        _step.value = BoothStep.REVIEW
    }

    fun cancelCapture() {
        if (_retakeSlotIndex.value != null) {
            _retakeSlotIndex.value = null
            _step.value = BoothStep.REVIEW
        } else {
            _step.value = BoothStep.SCREENSAVER
        }
    }

    fun retakeSingleSlot(slotIndex: Int) {
        _retakeSlotIndex.value = slotIndex
        _step.value = BoothStep.CAPTURE
    }

    fun restartAllCaptures() {
        _retakeSlotIndex.value = null
        _capturedPhotos.value = emptyList()
        _lastModifiedSlot.value = 0
        _step.value = BoothStep.CAPTURE
    }

    fun confirmAndProcessPhotos() {
        val photos = _capturedPhotos.value
        if (photos.isEmpty()) return
        val currentSettings = _settings.value

        _step.value = BoothStep.PROCESSING
        _processingStage.value = 0
        _processingDetail.value = "Composing strip…"

        viewModelScope.launch {
            try {
                val sessionId = System.currentTimeMillis().toString()
                val viewerUrl = "${AppConfig.WEB_VIEWER_BASE_URL}/s/$sessionId"

                // 1. Compose Strip
                _processingStage.value = 0
                _processingDetail.value = "Rendering high-res strip…"
                val stripBitmap = BoothUtils.composeStrip(
                    photos = photos,
                    settings = currentSettings,
                    cachedFrameBitmap = _cachedFrameBitmap.value
                )

                // 2. Build Animated GIF in Full HD / camera resolution
                _processingStage.value = 1
                _processingDetail.value = "Encoding animated GIF (1080p)…"
                val (resW, resH) = currentSettings.cameraResolution.split("x").let {
                    val w = it.getOrNull(0)?.toIntOrNull() ?: 1920
                    val h = it.getOrNull(1)?.toIntOrNull() ?: 1080
                    w to h
                }
                val gifBytes = BoothUtils.buildAnimatedGif(photos, width = resW, height = resH)

                // 2.5 Save Complete Backup to Local Gallery (before cloud upload)
                if (currentSettings.saveToLocalGallery) {
                    _processingDetail.value = "Saving backup to device gallery…"
                    withContext(Dispatchers.IO) {
                        BoothUtils.saveSessionToLocalGallery(
                            context = getApplication(),
                            sessionId = sessionId,
                            eventTitle = currentSettings.eventTitle,
                            stripBitmap = stripBitmap,
                            gifBytes = gifBytes,
                            originalPhotos = photos
                        )
                    }
                }

                // 3. Upload to Cloudinary & Save to Supabase
                _processingStage.value = 2
                val (cloudStripUrl, cloudGifUrl) = RemoteRepository.uploadAndSaveSession(
                    sessionId = sessionId,
                    settings = currentSettings,
                    stripBitmap = stripBitmap,
                    gifBytes = gifBytes,
                    originalBitmaps = photos,
                    onProgress = { msg ->
                        _processingDetail.value = msg
                    }
                )

                // 4. Generate QR Code pointing to mingle-booth.vercel.app/s/{sessionId}
                _processingStage.value = 3
                _processingDetail.value = "Generating QR Code…"
                val qrBitmap = BoothUtils.generateQrBitmap(viewerUrl, 512)

                _boothResult.value = BoothResult(
                    sessionId = sessionId,
                    stripBitmap = stripBitmap,
                    gifBytes = gifBytes,
                    viewerUrl = viewerUrl,
                    qrBitmap = qrBitmap,
                    cloudStripUrl = cloudStripUrl,
                    cloudGifUrl = cloudGifUrl
                )
                _step.value = BoothStep.RESULT
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Processing error: ${e.message}")
                _step.value = BoothStep.SCREENSAVER
            }
        }
    }

    fun returnToScreensaver() {
        _capturedPhotos.value = emptyList()
        _retakeSlotIndex.value = null
        _boothResult.value = null
        refreshBoothSettings()
        _step.value = BoothStep.SCREENSAVER
    }

    // Admin actions
    fun openAdmin() {
        if (_isAdminAuthed.value) {
            loadAdminData()
            _step.value = BoothStep.ADMIN_DASHBOARD
        } else {
            _step.value = BoothStep.ADMIN_LOGIN
        }
    }

    fun loginAdmin(password: String) {
        viewModelScope.launch {
            _adminBusy.value = true
            val ok = RemoteRepository.verifyAdminPassword(password)
            _adminBusy.value = false
            if (ok) {
                _isAdminAuthed.value = true
                loadAdminData()
                _step.value = BoothStep.ADMIN_DASHBOARD
            } else {
                showToast("Incorrect admin password")
            }
        }
    }

    fun logoutAdmin() {
        _isAdminAuthed.value = false
        returnToScreensaver()
    }

    fun loadAdminData(selectedEventId: String? = null, forceResetSockets: Boolean = false) {
        viewModelScope.launch {
            _adminBusy.value = true
            try {
                if (forceResetSockets) {
                    RemoteRepository.resetConnections()
                }
                var firstError: String? = null
                var activeSettings = _settings.value

                when (val settingsRes = RemoteRepository.loadBoothSettingsResult()) {
                    is DbResult.Success -> {
                        activeSettings = settingsRes.data
                        _settings.value = activeSettings
                        withContext(Dispatchers.IO) {
                            BoothSettingsCache.saveSettings(getApplication(), activeSettings)
                        }
                        val frameBmp = activeSettings.activeFrame?.dataUrl?.takeIf { it.isNotBlank() }?.let {
                            RemoteRepository.downloadBitmap(it)
                        }
                        if (frameBmp != null) {
                            _cachedFrameBitmap.value = frameBmp
                            withContext(Dispatchers.IO) {
                                BoothSettingsCache.saveFrameBitmap(getApplication(), frameBmp)
                            }
                        } else if (activeSettings.activeFrame == null) {
                            _cachedFrameBitmap.value = null
                        }
                    }
                    is DbResult.Error -> {
                        firstError = settingsRes.message
                    }
                }

                when (val evRes = RemoteRepository.getEventsResult()) {
                    is DbResult.Success -> _events.value = evRes.data
                    is DbResult.Error -> if (firstError == null) firstError = evRes.message
                }

                val targetEvent = selectedEventId ?: activeSettings.eventId
                when (val sessRes = RemoteRepository.getSessionsResult(targetEvent)) {
                    is DbResult.Success -> _sessions.value = sessRes.data
                    is DbResult.Error -> if (firstError == null) firstError = sessRes.message
                }

                _dbError.value = firstError
                if (firstError != null) {
                    showToast("DB connection issue: $firstError")
                } else if (forceResetSockets) {
                    showToast("Database reconnected!")
                }
            } finally {
                _adminBusy.value = false
            }
        }
    }

    fun createNewEvent(title: String, subtitle: String, activateNow: Boolean) {
        if (title.isBlank()) {
            showToast("Event title is required")
            return
        }
        viewModelScope.launch {
            _adminBusy.value = true
            try {
                when (val res = RemoteRepository.createEventResult(title, subtitle, activateNow)) {
                    is DbResult.Success -> {
                        _dbError.value = null
                        showToast("Event created!")
                        loadAdminData()
                    }
                    is DbResult.Error -> {
                        _dbError.value = res.message
                        showToast("Failed to create event: ${res.message}")
                    }
                }
            } finally {
                _adminBusy.value = false
            }
        }
    }

    fun updateExistingEvent(eventId: String, title: String, subtitle: String, activateNow: Boolean) {
        if (title.isBlank()) {
            showToast("Event title is required")
            return
        }
        viewModelScope.launch {
            _adminBusy.value = true
            try {
                when (val res = RemoteRepository.updateEventResult(eventId, title, subtitle, activateNow)) {
                    is DbResult.Success -> {
                        _dbError.value = null
                        showToast("Event updated successfully!")
                        loadAdminData(if (activateNow) eventId else null)
                    }
                    is DbResult.Error -> {
                        _dbError.value = res.message
                        showToast("Failed to update event: ${res.message}")
                    }
                }
            } finally {
                _adminBusy.value = false
            }
        }
    }

    fun setActiveEvent(eventId: String) {
        viewModelScope.launch {
            _adminBusy.value = true
            try {
                when (val res = RemoteRepository.activateEventResult(eventId)) {
                    is DbResult.Success -> {
                        _dbError.value = null
                        showToast("Active event switched!")
                        loadAdminData(eventId)
                    }
                    is DbResult.Error -> {
                        _dbError.value = res.message
                        showToast("Failed to switch event: ${res.message}")
                    }
                }
            } finally {
                _adminBusy.value = false
            }
        }
    }

    fun removeEvent(eventId: String) {
        viewModelScope.launch {
            _adminBusy.value = true
            val ok = RemoteRepository.deleteEvent(eventId)
            if (ok) {
                showToast("Event deleted")
                loadAdminData()
            } else {
                _adminBusy.value = false
                showToast("Failed to delete event")
            }
        }
    }

    fun setActiveFrame(frameId: String) {
        viewModelScope.launch {
            _adminBusy.value = true
            val ok = RemoteRepository.activateFrame(frameId)
            if (ok) {
                showToast("Frame activated!")
                loadAdminData()
            } else {
                _adminBusy.value = false
                showToast("Failed to activate frame")
            }
        }
    }

    fun addBuiltInFrameTemplate(type: String) {
        viewModelScope.launch {
            _adminBusy.value = true
            val currentTitle = _settings.value.eventTitle.ifBlank { "Mingle Booth" }
            val (name, bmp, slots) = BoothUtils.generateDefaultFrameTemplate(type, currentTitle)
            val ok = RemoteRepository.addFrame(
                name = name,
                frameBitmap = bmp,
                slots = slots,
                eventId = _settings.value.eventId ?: "default"
            )
            if (ok) {
                showToast("Added & activated \"$name\"!")
                loadAdminData()
            } else {
                _adminBusy.value = false
                showToast("Failed to add template frame")
            }
        }
    }

    fun deleteFrameItem(frameId: String) {
        viewModelScope.launch {
            _adminBusy.value = true
            val ok = RemoteRepository.deleteFrame(frameId)
            if (ok) {
                showToast("Frame removed")
                loadAdminData()
            } else {
                _adminBusy.value = false
                showToast("Failed to remove frame")
            }
        }
    }

    fun clearActiveFrame() {
        viewModelScope.launch {
            _adminBusy.value = true
            val ok = RemoteRepository.clearActiveFrame()
            if (ok) {
                showToast("Active frame cleared")
                loadAdminData()
            } else {
                _adminBusy.value = false
                showToast("Failed to clear active frame")
            }
        }
    }

    fun uploadCustomFrame(
        name: String,
        frameBitmap: Bitmap,
        slots: List<Slot>
    ) {
        viewModelScope.launch {
            _adminBusy.value = true
            _adminStatusMessage.value = "Uploading custom frame & slots…"
            val ok = RemoteRepository.addFrame(
                name = name,
                frameBitmap = frameBitmap,
                slots = slots,
                eventId = _settings.value.eventId ?: "default"
            )
            _adminStatusMessage.value = ""
            if (ok) {
                showToast("Frame \"$name\" saved & activated!")
                loadAdminData()
            } else {
                _adminBusy.value = false
                showToast("Failed to upload custom frame")
            }
        }
    }

    fun updateFrameSlots(
        frameId: String,
        slots: List<Slot>,
        width: Int,
        height: Int
    ) {
        viewModelScope.launch {
            _adminBusy.value = true
            _adminStatusMessage.value = "Saving frame slot placement…"
            val ok = RemoteRepository.updateFrameSlots(frameId, slots, width, height)
            _adminStatusMessage.value = ""
            if (ok) {
                showToast("Slots updated successfully!")
                loadAdminData()
            } else {
                _adminBusy.value = false
                showToast("Failed to update frame slots")
            }
        }
    }

    fun saveCameraAndBranding(
        cameraFacing: String,
        cameraResolution: String,
        mirrorCamera: Boolean,
        mirrorResult: Boolean,
        stripBgColor: String,
        stripTextColor: String,
        stripShowTitle: Boolean,
        stripShowDate: Boolean,
        saveToLocalGallery: Boolean = false
    ) {
        viewModelScope.launch {
            _adminBusy.value = true
            val eventId = _settings.value.eventId ?: "default"
            val ok = RemoteRepository.updateCameraAndBrandingSettings(
                eventId = eventId,
                cameraFacing = cameraFacing,
                cameraResolution = cameraResolution,
                mirrorCamera = mirrorCamera,
                mirrorResult = mirrorResult,
                stripBgColor = stripBgColor,
                stripTextColor = stripTextColor,
                stripShowTitle = stripShowTitle,
                stripShowDate = stripShowDate,
                saveToLocalGallery = saveToLocalGallery
            )
            if (ok) {
                showToast("Settings saved!")
                loadAdminData()
            } else {
                _adminBusy.value = false
                showToast("Failed to save settings")
            }
        }
    }

    fun deleteSessionItem(session: PhotoSessionItem) {
        viewModelScope.launch {
            _adminBusy.value = true
            val ok = RemoteRepository.deleteSession(session)
            if (ok) {
                showToast("Session deleted")
                _sessions.value = _sessions.value.filterNot { it.id == session.id }
            } else {
                showToast("Failed to delete session")
            }
            _adminBusy.value = false
        }
    }

    fun regenerateSessionItem(session: PhotoSessionItem) {
        viewModelScope.launch {
            _adminBusy.value = true
            _adminStatusMessage.value = "Starting regeneration for #${session.id.takeLast(6).uppercase()}…"
            try {
                val updated = RemoteRepository.regenerateSession(
                    session = session,
                    settings = _settings.value,
                    onProgress = { msg ->
                        _adminStatusMessage.value = msg
                    }
                )
                if (updated != null) {
                    if (_settings.value.saveToLocalGallery) {
                        _adminStatusMessage.value = "Updating local gallery backup…"
                        val rawPhotos = updated.photos.mapNotNull { RemoteRepository.downloadBitmap(it.url) }
                        val stripBmp = RemoteRepository.downloadBitmap(updated.stripUrl)
                        val gifBytes = if (!updated.gifUrl.isNullOrBlank()) RemoteRepository.downloadBytes(updated.gifUrl!!) else null
                        if (stripBmp != null) {
                            BoothUtils.saveSessionToLocalGallery(
                                context = getApplication(),
                                sessionId = updated.id,
                                eventTitle = _settings.value.eventTitle,
                                stripBitmap = stripBmp,
                                gifBytes = gifBytes,
                                originalPhotos = rawPhotos
                            )
                        }
                    }
                    _sessions.value = _sessions.value.map { if (it.id == updated.id) updated else it }
                    showToast("Session #${session.id.takeLast(6).uppercase()} regenerated successfully!")
                } else {
                    showToast("Failed to regenerate session #${session.id.takeLast(6).uppercase()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Regeneration error: ${e.message}")
            } finally {
                _adminBusy.value = false
                _adminStatusMessage.value = ""
            }
        }
    }

    fun regenerateAllEventSessions(eventId: String, eventTitle: String) {
        if (_batchRegenerateState.value.isRunning) {
            showToast("A batch regeneration job is already in progress.")
            return
        }

        batchRegenerateJob = viewModelScope.launch(Dispatchers.IO) {
            _batchRegenerateState.value = BatchRegenerateProgress(
                isRunning = true,
                eventId = eventId,
                eventTitle = eventTitle,
                currentIndex = 0,
                totalCount = 0,
                detailMessage = "Fetching sessions for $eventTitle from cloud database…"
            )

            try {
                val targetEventId = if (eventId == "default" || eventId.isBlank()) null else eventId
                val eventSessions = RemoteRepository.getSessions(targetEventId).let { list ->
                    if (targetEventId == null) list.filter { it.eventId.isBlank() || it.eventId == "default" } else list
                }

                if (eventSessions.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        showToast("No photo sessions found for $eventTitle")
                    }
                    _batchRegenerateState.value = BatchRegenerateProgress()
                    return@launch
                }

                val total = eventSessions.size
                _batchRegenerateState.value = _batchRegenerateState.value.copy(
                    totalCount = total,
                    detailMessage = "Found $total sessions. Starting regeneration…"
                )

                var succeeded = 0
                var failed = 0
                val frameCache = mutableMapOf<String, Bitmap>()

                for (idx in eventSessions.indices) {
                    if (!isActive) break
                    val session = eventSessions[idx]
                    val sessionShortId = session.id.takeLast(6).uppercase()

                    _batchRegenerateState.value = _batchRegenerateState.value.copy(
                        currentIndex = idx + 1,
                        currentSessionId = session.id,
                        detailMessage = "[$sessionShortId ${idx + 1}/$total] Initializing…"
                    )

                    val updated = RemoteRepository.regenerateSession(
                        session = session,
                        settings = _settings.value,
                        frameCache = frameCache,
                        onProgress = { step ->
                            _batchRegenerateState.value = _batchRegenerateState.value.copy(
                                detailMessage = "[$sessionShortId ${idx + 1}/$total] $step"
                            )
                        }
                    )

                    if (updated != null) {
                        succeeded++
                        if (_settings.value.saveToLocalGallery) {
                            try {
                                val rawPhotos = updated.photos.mapNotNull { RemoteRepository.downloadBitmap(it.url) }
                                val stripBmp = RemoteRepository.downloadBitmap(updated.stripUrl)
                                val gifBytes = if (!updated.gifUrl.isNullOrBlank()) RemoteRepository.downloadBytes(updated.gifUrl!!) else null
                                if (stripBmp != null) {
                                    BoothUtils.saveSessionToLocalGallery(
                                        context = getApplication(),
                                        sessionId = updated.id,
                                        eventTitle = _settings.value.eventTitle,
                                        stripBitmap = stripBmp,
                                        gifBytes = gifBytes,
                                        originalPhotos = rawPhotos
                                    )
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        _sessions.value = _sessions.value.map { if (it.id == updated.id) updated else it }
                    } else {
                        failed++
                    }

                    _batchRegenerateState.value = _batchRegenerateState.value.copy(
                        successCount = succeeded,
                        failureCount = failed
                    )
                }

                val finalMessage = if (isActive) {
                    "Completed: $succeeded succeeded, $failed failed ($total total)"
                } else {
                    "Batch regeneration cancelled after $succeeded sessions."
                }

                withContext(Dispatchers.Main) {
                    showToast(finalMessage)
                    loadAdminData()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showToast("Batch regeneration error: ${e.message}")
                }
            } finally {
                _batchRegenerateState.value = BatchRegenerateProgress()
            }
        }
    }

    fun cancelBatchRegeneration() {
        batchRegenerateJob?.cancel()
        batchRegenerateJob = null
        _batchRegenerateState.value = BatchRegenerateProgress()
        showToast("Batch regeneration cancelled")
    }
}
