package com.example.mingleboothkalakini.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mingleboothkalakini.data.*
import com.example.mingleboothkalakini.util.BoothUtils
import kotlinx.coroutines.Dispatchers
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

    init {
        refreshBoothSettings()
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun refreshBoothSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            val loaded = RemoteRepository.loadBoothSettings()
            _settings.value = loaded
            _cachedFrameBitmap.value = loaded.activeFrame?.dataUrl?.takeIf { it.isNotBlank() }?.let {
                RemoteRepository.downloadBitmap(it)
            }
            _isLoading.value = false
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

                // 2. Build Animated GIF
                _processingStage.value = 1
                _processingDetail.value = "Encoding animated GIF…"
                val gifBytes = BoothUtils.buildAnimatedGif(photos)

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

    fun loadAdminData(selectedEventId: String? = null) {
        viewModelScope.launch {
            _adminBusy.value = true
            val loadedSettings = RemoteRepository.loadBoothSettings()
            _settings.value = loadedSettings
            _cachedFrameBitmap.value = loadedSettings.activeFrame?.dataUrl?.takeIf { it.isNotBlank() }?.let {
                RemoteRepository.downloadBitmap(it)
            }
            val evList = RemoteRepository.getEvents()
            _events.value = evList
            val targetEvent = selectedEventId ?: loadedSettings.eventId
            _sessions.value = RemoteRepository.getSessions(targetEvent)
            _adminBusy.value = false
        }
    }

    fun createNewEvent(title: String, subtitle: String, activateNow: Boolean) {
        if (title.isBlank()) {
            showToast("Event title is required")
            return
        }
        viewModelScope.launch {
            _adminBusy.value = true
            val ok = RemoteRepository.createEvent(title, subtitle, activateNow)
            if (ok) {
                showToast("Event created!")
                loadAdminData()
            } else {
                _adminBusy.value = false
                showToast("Failed to create event")
            }
        }
    }

    fun setActiveEvent(eventId: String) {
        viewModelScope.launch {
            _adminBusy.value = true
            val ok = RemoteRepository.activateEvent(eventId)
            if (ok) {
                showToast("Active event switched!")
                loadAdminData(eventId)
            } else {
                _adminBusy.value = false
                showToast("Failed to switch event")
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
}
