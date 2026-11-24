package com.example.mingleboothkalakini.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.mingleboothkalakini.R
import com.example.mingleboothkalakini.camera.HdmiCaptureController
import com.example.mingleboothkalakini.camera.HdmiCaptureState
import com.example.mingleboothkalakini.camera.SonyCameraController
import com.example.mingleboothkalakini.camera.SonyCameraState
import com.example.mingleboothkalakini.data.BoothSettings
import com.example.mingleboothkalakini.data.EventItem
import com.example.mingleboothkalakini.data.FrameItem
import com.example.mingleboothkalakini.data.PhotoSessionItem
import com.example.mingleboothkalakini.data.BatchRegenerateProgress
import com.example.mingleboothkalakini.data.RemoteRepository
import com.example.mingleboothkalakini.data.Slot
import com.example.mingleboothkalakini.util.BoothUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AdminLoginScreen(
    isBusy: Boolean,
    onLogin: (String) -> Unit,
    onBackToBooth: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroCanvasBrush)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardCream),
            shape = RoundedCornerShape(30.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            border = BorderStroke(1.5.dp, BorderWarm),
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(RetroCardBrush)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_mingle_booth),
                    contentDescription = "Mingle Booth Retro Logo",
                    modifier = Modifier
                        .height(110.dp)
                        .widthIn(max = 200.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Studio Host Access",
                    style = TextStyle(
                        brush = RetroTitleBrush,
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )
                )
                Text(
                    "Enter studio passcode",
                    color = MutedCocoa,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Passcode", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Espresso,
                        unfocusedTextColor = Espresso,
                        focusedBorderColor = Terracotta,
                        unfocusedBorderColor = BorderWarm,
                        focusedLabelColor = Terracotta,
                        unfocusedLabelColor = MutedCocoa
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackToBooth,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, BorderWarm),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Espresso)
                    ) {
                        Text("Back", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onLogin(password) },
                        enabled = !isBusy && password.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("UNLOCK STUDIO", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDashboardScreen(
    settings: BoothSettings,
    events: List<EventItem>,
    sessions: List<PhotoSessionItem>,
    isBusy: Boolean,
    onCreateEvent: (String, String, Boolean) -> Unit,
    onUpdateEvent: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onActivateEvent: (String) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onActivateFrame: (String) -> Unit,
    onAddTemplateFrame: (String) -> Unit,
    onUploadCustomFrame: (String, Bitmap, List<Slot>) -> Unit = { _, _, _ -> },
    onUpdateFrameSlots: (String, List<Slot>, Int, Int) -> Unit = { _, _, _, _ -> },
    onClearActiveFrame: () -> Unit = {},
    onDeleteFrame: (String) -> Unit,
    onSaveCameraAndBranding: (String, String, Boolean, Boolean, String, String, Boolean, Boolean, Boolean) -> Unit,
    onDeleteSession: (PhotoSessionItem) -> Unit,
    onRegenerateSession: (PhotoSessionItem) -> Unit = {},
    batchProgress: BatchRegenerateProgress = BatchRegenerateProgress(),
    onRegenerateAllEventPhotos: (String, String) -> Unit = { _, _ -> },
    onCancelBatchRegeneration: () -> Unit = {},
    adminStatusMessage: String = "",
    dbError: String? = null,
    onReconnectDb: () -> Unit = {},
    onFilterGalleryByEvent: (String?) -> Unit,
    onReturnToBooth: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "✨ Events & Frames",
        "📷 Camera & Strip",
        "🎞️ Gallery (${sessions.size})"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroCanvasBrush)
            .systemBarsPadding()
    ) {
        // Top Header Bar
        Surface(
            color = CardCream,
            border = BorderStroke(1.dp, BorderWarm),
            modifier = Modifier.fillMaxWidth()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompact = maxWidth < 600.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RetroCardBrush)
                        .padding(
                            horizontal = if (isCompact) 10.dp else 16.dp,
                            vertical = if (isCompact) 8.dp else 10.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_mingle_booth),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .height(if (isCompact) 32.dp else 44.dp)
                                .widthIn(max = if (isCompact) 60.dp else 80.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.width(if (isCompact) 6.dp else 10.dp))
                        Column {
                            Text(
                                if (isCompact) "Studio Admin" else "Mingle Booth Studio",
                                style = TextStyle(
                                    brush = RetroTitleBrush,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Black,
                                    fontSize = if (isCompact) 15.sp else 18.sp
                                ),
                                maxLines = 1
                            )
                            if (!isCompact) {
                                Text(
                                    text = "LIVE EVENT • ${settings.eventTitle.uppercase()}",
                                    color = SageGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCompact) {
                            FilledTonalIconButton(
                                onClick = onReconnectDb,
                                enabled = !isBusy,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (dbError != null) Color(0xFFFEE2E2) else SoftPeach,
                                    contentColor = if (dbError != null) Color(0xFFDC2626) else Espresso
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync DB", modifier = Modifier.size(18.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .shadow(4.dp, RoundedCornerShape(50), spotColor = Terracotta)
                                    .clip(RoundedCornerShape(50))
                                    .background(RetroButtonBrush)
                                    .clickable { onReturnToBooth() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "BOOTH",
                                        color = Color.White,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = onLogout,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = SurfaceSand,
                                    contentColor = MutedCocoa
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Logout", modifier = Modifier.size(18.dp))
                            }
                        } else {
                            OutlinedButton(
                                onClick = onReconnectDb,
                                enabled = !isBusy,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(
                                    1.dp,
                                    if (dbError != null) Color(0xFFDC2626).copy(alpha = 0.6f) else BorderWarm
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (dbError != null) Color(0xFFDC2626) else Espresso
                                )
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reconnect DB",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (dbError != null) "Reconnect DB" else "Sync",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .shadow(4.dp, RoundedCornerShape(50), spotColor = Terracotta)
                                    .clip(RoundedCornerShape(50))
                                    .background(RetroButtonBrush)
                                    .clickable { onReturnToBooth() }
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "LAUNCH BOOTH",
                                        color = Color.White,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onLogout,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, BorderWarm),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedCocoa)
                            ) {
                                Text("Logout", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Tab Bar
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardCream,
            contentColor = Terracotta,
            edgePadding = 16.dp
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTab == idx) Terracotta else MutedCocoa,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = if (selectedTab == idx) FontWeight.ExtraBold else FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                    }
                )
            }
        }

        // Database Connection Warning Banner
        if (dbError != null) {
            Surface(
                color = Color(0xFFFEE2E2),
                border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Database Connection Issue",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF991B1B)
                            )
                            Text(
                                text = dbError,
                                fontSize = 11.sp,
                                color = Color(0xFF7F1D1D),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Button(
                        onClick = onReconnectDb,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reconnect DB", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Background Batch Regeneration Progress Banner
        if (batchProgress.isRunning) {
            Surface(
                color = SoftPeach,
                border = BorderStroke(1.dp, Terracotta.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Terracotta
                            )
                            Text(
                                text = "REGENERATING ALL PHOTOS • ${batchProgress.eventTitle.uppercase()}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Espresso,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "${batchProgress.currentIndex} / ${batchProgress.totalCount} (${batchProgress.percentage}%)",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Terracotta
                            )
                            OutlinedButton(
                                onClick = onCancelBatchRegeneration,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                            ) {
                                Text("Cancel", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    LinearProgressIndicator(
                        progress = { batchProgress.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Terracotta,
                        trackColor = Color.White.copy(alpha = 0.8f)
                    )

                    Text(
                        text = batchProgress.detailMessage,
                        fontSize = 10.sp,
                        color = MutedCocoa,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else if (isBusy) {
            LinearProgressIndicator(
                color = Terracotta,
                trackColor = SoftPeach,
                modifier = Modifier.fillMaxWidth()
            )
            if (adminStatusMessage.isNotBlank()) {
                Surface(
                    color = SoftPeach.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Terracotta
                        )
                        Text(
                            text = adminStatusMessage,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Espresso
                        )
                    }
                }
            }
        }

        // Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> EventsAndFramesTab(
                    settings = settings,
                    events = events,
                    isBusy = isBusy,
                    dbError = dbError,
                    onReconnectDb = onReconnectDb,
                    batchProgress = batchProgress,
                    onCreateEvent = onCreateEvent,
                    onUpdateEvent = onUpdateEvent,
                    onActivateEvent = onActivateEvent,
                    onDeleteEvent = onDeleteEvent,
                    onActivateFrame = onActivateFrame,
                    onAddTemplateFrame = onAddTemplateFrame,
                    onUploadCustomFrame = onUploadCustomFrame,
                    onUpdateFrameSlots = onUpdateFrameSlots,
                    onClearActiveFrame = onClearActiveFrame,
                    onDeleteFrame = onDeleteFrame,
                    onOpenEventGallery = { evId ->
                        onFilterGalleryByEvent(evId)
                        selectedTab = 2
                    },
                    onRegenerateAllEventPhotos = onRegenerateAllEventPhotos
                )
                1 -> CameraAndBrandingTab(
                    settings = settings,
                    isBusy = isBusy,
                    onSave = onSaveCameraAndBranding
                )
                2 -> GalleryTab(
                    sessions = sessions,
                    events = events,
                    activeEventId = settings.eventId,
                    isBusy = isBusy,
                    batchProgress = batchProgress,
                    onFilterByEvent = onFilterGalleryByEvent,
                    onDeleteSession = onDeleteSession,
                    onRegenerateSession = onRegenerateSession,
                    onRegenerateAllEventPhotos = onRegenerateAllEventPhotos
                )
            }
        }
    }
}

private data class PendingFrameUpload(
    val fileName: String,
    val bitmap: Bitmap,
    val format: String
)

private data class EditingFrameTarget(
    val existingFrameId: String?,
    val name: String,
    val bitmap: Bitmap,
    val initialSlots: List<Slot>
)

@Composable
private fun EventsAndFramesTab(
    settings: BoothSettings,
    events: List<EventItem>,
    isBusy: Boolean,
    dbError: String? = null,
    onReconnectDb: () -> Unit = {},
    batchProgress: BatchRegenerateProgress = BatchRegenerateProgress(),
    onCreateEvent: (String, String, Boolean) -> Unit,
    onUpdateEvent: (String, String, String, Boolean) -> Unit,
    onActivateEvent: (String) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onActivateFrame: (String) -> Unit,
    onAddTemplateFrame: (String) -> Unit,
    onUploadCustomFrame: (String, Bitmap, List<Slot>) -> Unit = { _, _, _ -> },
    onUpdateFrameSlots: (String, List<Slot>, Int, Int) -> Unit = { _, _, _, _ -> },
    onClearActiveFrame: () -> Unit = {},
    onDeleteFrame: (String) -> Unit,
    onOpenEventGallery: (String) -> Unit,
    onRegenerateAllEventPhotos: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<EventItem?>(null) }
    var eventForQrDialog by remember { mutableStateOf<EventItem?>(null) }
    var eventQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var eventToRegenerateAll by remember { mutableStateOf<EventItem?>(null) }

    var pendingUpload by remember { mutableStateOf<PendingFrameUpload?>(null) }
    var editingFrameTarget by remember { mutableStateOf<EditingFrameTarget?>(null) }
    var isAnalyzingFrame by remember { mutableStateOf(false) }

    val framePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isAnalyzingFrame = true
        scope.launch {
            val fileName = withContext(Dispatchers.IO) {
                var name = "custom_frame.png"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                }
                name
            }
            val bmp = withContext(Dispatchers.IO) {
                BoothUtils.decodeBitmapFromUri(context, uri)
            }
            isAnalyzingFrame = false
            if (bmp == null) {
                Toast.makeText(context, "Failed to read PNG image", Toast.LENGTH_SHORT).show()
            } else {
                val ratio = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
                val detectedFormat = if (ratio < 0.5f) "2R" else "4R"
                pendingUpload = PendingFrameUpload(
                    fileName = fileName,
                    bitmap = bmp,
                    format = detectedFormat
                )
            }
        }
    }

    LaunchedEffect(eventForQrDialog) {
        val ev = eventForQrDialog
        if (ev != null) {
            eventQrBitmap = null
            eventQrBitmap = BoothUtils.generateQrBitmap(ev.galleryUrl, 512)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Section 1: Events
        Card(
            colors = CardDefaults.cardColors(containerColor = CardCream),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            border = BorderStroke(1.dp, BorderWarm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RetroCardBrush)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "✨ Celebration Events",
                            style = TextStyle(
                                brush = RetroTitleBrush,
                                fontFamily = FontFamily.Serif,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                        )
                        Text(
                            "Select the active event displayed on the welcome screen",
                            color = MutedCocoa,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Event", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (events.isEmpty()) {
                    Surface(
                        color = SurfaceSand.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderWarm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (dbError != null) "Unable to load events from database" else "No events created yet",
                                color = Espresso,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = dbError ?: "Tap \"New Event\" to create your first photobooth event.",
                                color = MutedCocoa,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            if (dbError != null) {
                                Button(
                                    onClick = onReconnectDb,
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Reconnect DB", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                events.forEach { ev ->
                    Surface(
                        color = if (ev.isActive) SoftPeach.copy(alpha = 0.65f) else SurfaceSand.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (ev.isActive) Terracotta.copy(alpha = 0.55f) else BorderWarm
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            val isCompact = maxWidth < 600.dp
                            if (isCompact) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ev.title,
                                            color = Espresso,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (ev.isActive) {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                color = SageGreen.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(50),
                                                border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "ACTIVE",
                                                    color = SageGreen,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${ev.subtitle} · ${ev.sessionCount} sessions",
                                        color = MutedCocoa,
                                        fontSize = 12.sp
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!ev.isActive) {
                                            FilledTonalButton(
                                                onClick = { onActivateEvent(ev.id) },
                                                enabled = !isBusy,
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = SoftPeach,
                                                    contentColor = Espresso
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text("Activate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        IconButton(
                                            onClick = { eventToEdit = ev },
                                            enabled = !isBusy
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Event", tint = Terracotta)
                                        }

                                        IconButton(
                                            onClick = { eventForQrDialog = ev },
                                            enabled = !isBusy
                                        ) {
                                            Icon(Icons.Default.QrCode, contentDescription = "Show All Photos Barcode / QR", tint = Terracotta)
                                        }

                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Event Photos Link", ev.galleryUrl))
                                                Toast.makeText(context, "Copied link for all photos: ${ev.galleryUrl}", Toast.LENGTH_SHORT).show()
                                            },
                                            enabled = !isBusy
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Link for All Photos", tint = Espresso)
                                        }

                                        IconButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ev.galleryUrl))
                                                context.startActivity(intent)
                                            },
                                            enabled = !isBusy
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = "Open Web Gallery", tint = Espresso)
                                        }

                                        OutlinedButton(
                                            onClick = { onOpenEventGallery(ev.id) },
                                            border = BorderStroke(1.dp, BorderWarm),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Espresso),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text("Photos", fontSize = 11.sp)
                                        }

                                        IconButton(
                                            onClick = { eventToRegenerateAll = ev },
                                            enabled = !isBusy && !batchProgress.isRunning
                                        ) {
                                            Icon(
                                                Icons.Default.Sync,
                                                contentDescription = "Regenerate All Photos in Event",
                                                tint = if (batchProgress.isRunning && batchProgress.eventId == ev.id) Terracotta else Espresso
                                            )
                                        }

                                        if (ev.id != "default") {
                                            IconButton(
                                                onClick = { onDeleteEvent(ev.id) },
                                                enabled = !isBusy
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Event", tint = Color(0xFFDC2626))
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = ev.title,
                                                color = Espresso,
                                                fontFamily = FontFamily.Serif,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            if (ev.isActive) {
                                                Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    color = SageGreen.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(50),
                                                    border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.4f))
                                                ) {
                                                    Text(
                                                        text = "ACTIVE",
                                                        color = SageGreen,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${ev.subtitle} · ${ev.sessionCount} sessions",
                                            color = MutedCocoa,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!ev.isActive) {
                                            FilledTonalButton(
                                                onClick = { onActivateEvent(ev.id) },
                                                enabled = !isBusy,
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = SoftPeach,
                                                    contentColor = Espresso
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text("Activate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        IconButton(
                                            onClick = { eventToEdit = ev },
                                            enabled = !isBusy
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Event", tint = Terracotta)
                                        }

                                        IconButton(
                                            onClick = { eventForQrDialog = ev },
                                            enabled = !isBusy
                                        ) {
                                            Icon(Icons.Default.QrCode, contentDescription = "Show All Photos Barcode / QR", tint = Terracotta)
                                        }

                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Event Photos Link", ev.galleryUrl))
                                                Toast.makeText(context, "Copied link for all photos: ${ev.galleryUrl}", Toast.LENGTH_SHORT).show()
                                            },
                                            enabled = !isBusy
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Link for All Photos", tint = Espresso)
                                        }

                                        IconButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ev.galleryUrl))
                                                context.startActivity(intent)
                                            },
                                            enabled = !isBusy
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = "Open Web Gallery", tint = Espresso)
                                        }

                                        OutlinedButton(
                                            onClick = { onOpenEventGallery(ev.id) },
                                            border = BorderStroke(1.dp, BorderWarm),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Espresso),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text("Photos", fontSize = 11.sp)
                                        }

                                        IconButton(
                                            onClick = { eventToRegenerateAll = ev },
                                            enabled = !isBusy && !batchProgress.isRunning
                                        ) {
                                            Icon(
                                                Icons.Default.Sync,
                                                contentDescription = "Regenerate All Photos in Event",
                                                tint = if (batchProgress.isRunning && batchProgress.eventId == ev.id) Terracotta else Espresso
                                            )
                                        }

                                        if (ev.id != "default") {
                                            IconButton(
                                                onClick = { onDeleteEvent(ev.id) },
                                                enabled = !isBusy
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Event", tint = Color(0xFFDC2626))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Frames & Quick Templates
        Card(
            colors = CardDefaults.cardColors(containerColor = CardCream),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            border = BorderStroke(1.dp, BorderWarm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RetroCardBrush)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "🎞️ Photo Strip Frames & Layouts",
                            style = TextStyle(
                                brush = RetroTitleBrush,
                                fontFamily = FontFamily.Serif,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                        )
                        Text(
                            "Upload a custom transparent PNG frame (auto-detects photo slots) or pick a built-in template:",
                            color = MutedCocoa,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (settings.activeFrame != null) {
                            OutlinedButton(
                                onClick = onClearActiveFrame,
                                enabled = !isBusy,
                                border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Clear Active", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { framePickerLauncher.launch("image/*") },
                            enabled = !isBusy && !isAnalyzingFrame,
                            colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isAnalyzingFrame) "Analyzing…" else "Upload Custom PNG",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Quick Add Built-in Templates
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val templates = listOf(
                        "3-strip-white" to "🤍 Retro Cream 3-Strip",
                        "3-strip-dark" to "✨ Classic Dark 3-Strip",
                        "4-strip-dark" to "🎞️ Analog 4-Strip",
                        "2x2-dark" to "📸 Vintage 2×2 Grid"
                    )
                    templates.forEach { (type, label) ->
                        FilledTonalButton(
                            onClick = { onAddTemplateFrame(type) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SoftPeach,
                                contentColor = Espresso
                            )
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (settings.frames.isEmpty()) {
                    Text(
                        "No frames in database yet. Upload a custom PNG or tap any template button above!",
                        color = WarmGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        settings.frames.forEach { frame ->
                            FrameRowCard(
                                frame = frame,
                                isActive = settings.activeFrame?.id == frame.id,
                                isBusy = isBusy || isAnalyzingFrame,
                                onActivate = { onActivateFrame(frame.id) },
                                onEditSlots = {
                                    isAnalyzingFrame = true
                                    scope.launch {
                                        val bmp = RemoteRepository.downloadBitmap(frame.dataUrl)
                                        isAnalyzingFrame = false
                                        if (bmp == null) {
                                            Toast.makeText(
                                                context,
                                                "Failed to load frame image for slot editor",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            editingFrameTarget = EditingFrameTarget(
                                                existingFrameId = frame.id,
                                                name = frame.name,
                                                bitmap = bmp,
                                                initialSlots = frame.slots
                                            )
                                        }
                                    }
                                },
                                onDelete = { onDeleteFrame(frame.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var subtitle by remember { mutableStateOf("by Kala Kini") }
        var activateNow by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = CardCream,
            titleContentColor = Espresso,
            textContentColor = MutedCocoa,
            title = { Text("Create New Event", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Event / Couple Title *") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Espresso,
                            unfocusedTextColor = Espresso,
                            focusedBorderColor = Terracotta,
                            unfocusedBorderColor = BorderWarm
                        )
                    )
                    OutlinedTextField(
                        value = subtitle,
                        onValueChange = { subtitle = it },
                        label = { Text("Subtitle / Date") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Espresso,
                            unfocusedTextColor = Espresso,
                            focusedBorderColor = Terracotta,
                            unfocusedBorderColor = BorderWarm
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = activateNow,
                            onCheckedChange = { activateNow = it },
                            colors = CheckboxDefaults.colors(checkedColor = Terracotta)
                        )
                        Text("Set as Active Event immediately", color = Espresso, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateEvent(title, subtitle, activateNow)
                        showCreateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White)
                ) {
                    Text("Create Event", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = MutedCocoa)
                }
            }
        )
    }

    if (eventToEdit != null) {
        val ev = eventToEdit!!
        var title by remember(ev.id) { mutableStateOf(ev.title) }
        var subtitle by remember(ev.id) { mutableStateOf(ev.subtitle) }
        var activateNow by remember(ev.id) { mutableStateOf(ev.isActive) }

        AlertDialog(
            onDismissRequest = { if (!isBusy) eventToEdit = null },
            containerColor = CardCream,
            titleContentColor = Espresso,
            textContentColor = MutedCocoa,
            title = { Text("Edit Event", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Event / Couple Title *") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Espresso,
                            unfocusedTextColor = Espresso,
                            focusedBorderColor = Terracotta,
                            unfocusedBorderColor = BorderWarm
                        )
                    )
                    OutlinedTextField(
                        value = subtitle,
                        onValueChange = { subtitle = it },
                        label = { Text("Subtitle / Date") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Espresso,
                            unfocusedTextColor = Espresso,
                            focusedBorderColor = Terracotta,
                            unfocusedBorderColor = BorderWarm
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = activateNow,
                            onCheckedChange = { activateNow = it },
                            enabled = !ev.isActive,
                            colors = CheckboxDefaults.colors(checkedColor = Terracotta)
                        )
                        Text(
                            if (ev.isActive) "Currently Active Event" else "Set as Active Event immediately",
                            color = Espresso,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateEvent(ev.id, title, subtitle, activateNow)
                        eventToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { eventToEdit = null }) {
                    Text("Cancel", color = MutedCocoa)
                }
            }
        )
    }

    if (eventForQrDialog != null) {
        val ev = eventForQrDialog!!
        Dialog(onDismissRequest = { eventForQrDialog = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardCream),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.5.dp, Terracotta.copy(alpha = 0.5f)),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(RetroCardBrush)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "All Photos Barcode / QR",
                        style = TextStyle(
                            brush = RetroTitleBrush,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${ev.title} · ${ev.subtitle}",
                        color = Espresso,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        ev.galleryUrl,
                        color = Terracotta,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderWarm),
                        modifier = Modifier.size(220.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (eventQrBitmap != null) {
                                Image(
                                    bitmap = eventQrBitmap!!.asImageBitmap(),
                                    contentDescription = "Event Photos Barcode QR",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                )
                            } else {
                                CircularProgressIndicator(color = Terracotta)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Scan barcode / QR with phone camera to view and download all photos from this event.",
                        color = MutedCocoa,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Event Photos Link", ev.galleryUrl))
                                Toast.makeText(context, "Copied link to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SoftPeach,
                                contentColor = Espresso
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Terracotta, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Link", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ev.galleryUrl))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SoftPeach,
                                contentColor = Espresso
                            )
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Espresso, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { eventForQrDialog = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Close", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (eventToRegenerateAll != null) {
        val ev = eventToRegenerateAll!!
        Dialog(onDismissRequest = { if (!batchProgress.isRunning) eventToRegenerateAll = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardCream),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.5.dp, Terracotta.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .background(RetroCardBrush)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        tint = Terracotta,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Regenerate Event Photos",
                        style = TextStyle(
                            brush = RetroTitleBrush,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "[ ${ev.title.uppercase()} ]",
                        color = Terracotta,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Regenerate all photo sessions for this event in the background?\n\nThis will re-composite all photo strips in full template resolution (2R/4R) and re-encode 720p Hi-Res Composite animated GIFs from the original raw photos.\n\nYou can continue using the booth while the background process is running.",
                        color = Espresso,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { eventToRegenerateAll = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderWarm)
                        ) {
                            Text("Cancel", color = Espresso)
                        }

                        Button(
                            onClick = {
                                val target = eventToRegenerateAll
                                eventToRegenerateAll = null
                                if (target != null) {
                                    onRegenerateAllEventPhotos(target.id, target.title)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White)
                        ) {
                            Text("Start", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (pendingUpload != null) {
        val upload = pendingUpload!!
        var frameTitle by remember(upload.fileName) { mutableStateOf(upload.fileName) }
        var selectedFormat by remember(upload.format) { mutableStateOf(upload.format) }

        Dialog(onDismissRequest = { pendingUpload = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardCream),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.5.dp, Terracotta.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .background(RetroCardBrush)
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Confirm Frame Size",
                        style = TextStyle(
                            brush = RetroTitleBrush,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        "Select standard print format for \"${upload.fileName}\"",
                        color = MutedCocoa,
                        fontSize = 12.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1A1A28))
                            .border(1.dp, BorderWarm, RoundedCornerShape(14.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = upload.bitmap.asImageBitmap(),
                            contentDescription = "Uploaded frame preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Text(
                        text = "Original Resolution: ${upload.bitmap.width} × ${upload.bitmap.height} px",
                        color = Terracotta,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )

                    OutlinedTextField(
                        value = frameTitle,
                        onValueChange = { frameTitle = it },
                        label = { Text("Frame Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Espresso,
                            unfocusedTextColor = Espresso,
                            focusedBorderColor = Terracotta,
                            unfocusedBorderColor = BorderWarm
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val formats = listOf(
                            Triple("2R", "2R Photostrip", "2\" × 6\" Strip (1:3)"),
                            Triple("4R", "4R Postcard", "4\" × 6\" Card (2:3)")
                        )
                        formats.forEach { (fmt, title, desc) ->
                            val isSel = selectedFormat == fmt
                            Surface(
                                color = if (isSel) SoftPeach else SurfaceSand.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    if (isSel) 2.dp else 1.dp,
                                    if (isSel) Terracotta else BorderWarm
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedFormat = fmt }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = title,
                                        color = Espresso,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = desc,
                                        color = MutedCocoa,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { pendingUpload = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderWarm)
                        ) {
                            Text("Cancel", color = Espresso)
                        }
                        Button(
                            onClick = {
                                val target = pendingUpload ?: return@Button
                                val finalName = "${frameTitle.ifBlank { target.fileName }} ($selectedFormat)"
                                pendingUpload = null
                                isAnalyzingFrame = true
                                scope.launch {
                                    val detectedSlots = withContext(Dispatchers.Default) {
                                        BoothUtils.detectSlots(target.bitmap)
                                    }
                                    isAnalyzingFrame = false
                                    Toast.makeText(
                                        context,
                                        if (detectedSlots.isNotEmpty()) {
                                            "Detected ${detectedSlots.size} slot(s) for $selectedFormat frame!"
                                        } else {
                                            "No transparent cutouts found — draw slots manually"
                                        },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    editingFrameTarget = EditingFrameTarget(
                                        existingFrameId = null,
                                        name = finalName,
                                        bitmap = target.bitmap,
                                        initialSlots = detectedSlots
                                    )
                                }
                            },
                            modifier = Modifier.weight(1.4f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White)
                        ) {
                            Text("Detect & Edit Slots", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (editingFrameTarget != null) {
        val target = editingFrameTarget!!
        FrameSlotEditorDialog(
            frameName = target.name,
            frameBitmap = target.bitmap,
            initialSlots = target.initialSlots,
            onDismiss = { editingFrameTarget = null },
            onSave = { finalSlots ->
                editingFrameTarget = null
                if (target.existingFrameId == null) {
                    onUploadCustomFrame(target.name, target.bitmap, finalSlots)
                } else {
                    onUpdateFrameSlots(
                        target.existingFrameId,
                        finalSlots,
                        target.bitmap.width,
                        target.bitmap.height
                    )
                }
            }
        )
    }
}

@Composable
private fun FrameRowCard(
    frame: FrameItem,
    isActive: Boolean,
    isBusy: Boolean,
    onActivate: () -> Unit,
    onEditSlots: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (isActive) SoftPeach.copy(alpha = 0.65f) else SurfaceSand.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isActive) Terracotta.copy(alpha = 0.6f) else BorderWarm
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            val isCompact = maxWidth < 550.dp
            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardCream)
                                .border(1.dp, BorderWarm, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = frame.dataUrl,
                                contentDescription = frame.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(frame.name, color = Espresso, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "${frame.slots.size} EXP · ${frame.width}×${frame.height}PX",
                                color = MutedCocoa,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                        if (isActive) {
                            Surface(
                                color = SageGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "✓ ACTIVE",
                                    color = SageGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onEditSlots,
                            enabled = !isBusy,
                            border = BorderStroke(1.dp, BorderWarm),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Espresso),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(13.dp), tint = Terracotta)
                            Spacer(Modifier.width(4.dp))
                            Text("Edit Slots", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (!isActive) {
                            Spacer(Modifier.width(6.dp))
                            Button(
                                onClick = onActivate,
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text("Use Frame", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = onDelete, enabled = !isBusy) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Frame", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardCream)
                                .border(1.dp, BorderWarm, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = frame.dataUrl,
                                contentDescription = frame.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(frame.name, color = Espresso, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "${frame.slots.size} EXP · ${frame.width}×${frame.height}PX",
                                color = MutedCocoa,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onEditSlots,
                            enabled = !isBusy,
                            border = BorderStroke(1.dp, BorderWarm),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Espresso),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = Terracotta)
                            Spacer(Modifier.width(4.dp))
                            Text("Edit Slots", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (isActive) {
                            Surface(
                                color = SageGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "✓ ACTIVE",
                                    color = SageGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Button(
                                onClick = onActivate,
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Use Frame", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(onClick = onDelete, enabled = !isBusy) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Frame", tint = Color(0xFFDC2626))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraAndBrandingTab(
    settings: BoothSettings,
    isBusy: Boolean,
    onSave: (String, String, Boolean, Boolean, String, String, Boolean, Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var facing by remember(settings.cameraFacing) { mutableStateOf(settings.cameraFacing) }
    var resolution by remember(settings.cameraResolution) { mutableStateOf(settings.cameraResolution) }
    var mirrorCamera by remember(settings.mirrorCamera) { mutableStateOf(settings.mirrorCamera) }
    var mirrorResult by remember(settings.mirrorResult) { mutableStateOf(settings.mirrorResult) }
    var bgColor by remember(settings.stripBgColor) { mutableStateOf(settings.stripBgColor) }
    var textColor by remember(settings.stripTextColor) { mutableStateOf(settings.stripTextColor) }
    var showTitle by remember(settings.stripShowTitle) { mutableStateOf(settings.stripShowTitle) }
    var showDate by remember(settings.stripShowDate) { mutableStateOf(settings.stripShowDate) }
    var saveToLocalGallery by remember(settings.saveToLocalGallery) { mutableStateOf(settings.saveToLocalGallery) }

    var cameraOptions by remember { mutableStateOf(BoothUtils.getAvailableCameraOptions(context)) }
    var usbSummary by remember { mutableStateOf(BoothUtils.getConnectedUsbCameraSummary(context)) }
    var usbRaw by remember { mutableStateOf(BoothUtils.describeAllUsbDevices(context)) }
    var sonyConnected by remember { mutableStateOf(BoothUtils.findConnectedSonyCamera(context) != null) }
    var usbHasPermission by remember { mutableStateOf(BoothUtils.hasSonyUsbPermission(context)) }

    // Live-refresh USB state so the Grant / Test buttons react to plug-in and permission changes
    LaunchedEffect(Unit) {
        while (true) {
            cameraOptions = BoothUtils.getAvailableCameraOptions(context)
            usbSummary = BoothUtils.getConnectedUsbCameraSummary(context)
            usbRaw = BoothUtils.describeAllUsbDevices(context)
            sonyConnected = BoothUtils.findConnectedSonyCamera(context) != null
            usbHasPermission = BoothUtils.hasSonyUsbPermission(context)
            delay(1500)
        }
    }
    var isTestingCamera by remember { mutableStateOf(false) }
    var testCameraError by remember { mutableStateOf<String?>(null) }
    var showSonyGuide by remember { mutableStateOf(false) }
    var guideSection by remember { mutableIntStateOf(0) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val isExternalFacing = facing.equals("EXTERNAL", ignoreCase = true) ||
        facing.equals("SONY_PC_REMOTE", ignoreCase = true)

    val adminSonyController = remember(isTestingCamera, isExternalFacing) {
        if (isTestingCamera && isExternalFacing) SonyCameraController.getInstance(context) else null
    }
    val adminSonyState by if (adminSonyController != null) {
        adminSonyController.state.collectAsState()
    } else {
        remember { mutableStateOf<SonyCameraState>(SonyCameraState.NoCamera) }
    }

    val adminHdmiController = remember(isTestingCamera, isExternalFacing) {
        if (isTestingCamera && isExternalFacing) HdmiCaptureController.getInstance(context) else null
    }
    val adminHdmiState by if (adminHdmiController != null) {
        adminHdmiController.state.collectAsState()
    } else {
        remember { mutableStateOf<HdmiCaptureState>(HdmiCaptureState.NoDevice) }
    }

    val isAdminHdmiActive = isExternalFacing && (adminHdmiState !is HdmiCaptureState.NoDevice)

    DisposableEffect(isTestingCamera, isExternalFacing, isAdminHdmiActive) {
        if (isTestingCamera && isExternalFacing) {
            adminSonyController?.start()
            adminSonyController?.setLiveViewEnabled(!isAdminHdmiActive)
            adminHdmiController?.start()
            adminHdmiController?.setLiveViewEnabled(isAdminHdmiActive)
        }
        onDispose {
            adminSonyController?.setLiveViewEnabled(false)
            adminHdmiController?.setLiveViewEnabled(false)
        }
    }

    LaunchedEffect(isTestingCamera, facing, isExternalFacing) {
        if (isExternalFacing) {
            // CameraX is not bound when previewing Sony via USB-C PC Remote or HDMI Capture
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            testCameraError = null
            return@LaunchedEffect
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            provider.unbindAll()
            if (isTestingCamera) {
                testCameraError = null
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = BoothUtils.resolveCameraSelector(provider, facing)
                try {
                    provider.bindToLifecycle(lifecycleOwner, selector, preview)
                } catch (e: Exception) {
                    testCameraError = "Selected camera not ready (${e.localizedMessage ?: "error"})"
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardCream),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            border = BorderStroke(1.dp, BorderWarm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RetroCardBrush)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "📷 Camera Configuration",
                            style = TextStyle(
                                brush = RetroTitleBrush,
                                fontFamily = FontFamily.Serif,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                        )
                        Text(
                            "Supports Built-in Cameras, Sony a6000 / a6400 / a6700 & HDMI Capture Cards",
                            color = MutedCocoa,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            cameraOptions = BoothUtils.getAvailableCameraOptions(context)
                            usbSummary = BoothUtils.getConnectedUsbCameraSummary(context)
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, BorderWarm),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Espresso),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Terracotta, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh USB", color = Espresso, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // USB Hardware Status Banner
                Surface(
                    color = if (usbSummary != null) SageGreen.copy(alpha = 0.1f) else SurfaceSand.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (usbSummary != null) SageGreen.copy(alpha = 0.45f) else BorderWarm
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(if (usbSummary != null) "🟢" else "🔌", fontSize = 16.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (usbSummary != null) "Sony Camera / USB Device Connected" else "No External USB Camera Detected",
                                    color = if (usbSummary != null) SageGreen else Espresso,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = usbSummary
                                        ?: "Plug in your Sony a6000 / a6400 / a6700 (PC Remote / USB Streaming) or HDMI capture card.\n$usbRaw",
                                    color = MutedCocoa,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (sonyConnected) {
                            var testShutterStatus by remember { mutableStateOf<String?>(null) }
                            val coroutineScope = rememberCoroutineScope()
                            val hasPermission = usbHasPermission

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!hasPermission) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "⚠️ Android USB Permission Needed to trigger camera",
                                            color = WarmGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = {
                                                BoothUtils.requestSonyUsbPermission(context)
                                            },
                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White),
                                            shape = RoundedCornerShape(50),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                                        ) {
                                            Text("Grant USB Access", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = testShutterStatus
                                            ?: if (hasPermission) "⚡ Sony Shutter & Flash Trigger ready" else "Connect USB and grant permission above",
                                        color = if (hasPermission) Espresso else MutedCocoa,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                testShutterStatus = "Connecting & firing shutter…"
                                                val fired = BoothUtils.triggerSonyUsbShutter(context)
                                                testShutterStatus = if (fired) {
                                                    "✅ Shutter & Flash Fired!"
                                                } else {
                                                    "❌ Failed. If prompt appeared, tap Allow and retry!"
                                                }
                                            }
                                        },
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, Terracotta),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Text("⚡ Test Shutter / Flash", color = Terracotta, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Text("Camera Source / Device", color = Espresso, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cameraOptions.forEach { option ->
                        val isSelected = facing == option.id || (option.id == "EXTERNAL" && facing.equals("SONY_PC_REMOTE", ignoreCase = true))
                        Surface(
                            onClick = { facing = option.id },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) SoftPeach else SurfaceSand.copy(alpha = 0.55f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Terracotta else BorderWarm
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option.label,
                                    color = Espresso,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (option.isExternal) {
                                    Surface(
                                        color = Terracotta.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "SONY / UVC",
                                            color = Terracotta,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text("Capture Resolution", color = Espresso, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        "3840x2160" to "4K UHD (Sony a6400 / a6700)",
                        "1920x1080" to "1080p Full HD (Recommended — a6000 / a6400 / a6700)",
                        "1280x720" to "720p HD (Fastest)"
                    ).forEach { (res, label) ->
                        FilterChip(
                            selected = resolution == res,
                            onClick = { resolution = res },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SoftPeach,
                                selectedLabelColor = Espresso,
                                labelColor = MutedCocoa
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mirror Live Preview", color = Espresso, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Horizontal flip on screen for natural posing", color = MutedCocoa, fontSize = 12.sp)
                    }
                    Switch(
                        checked = mirrorCamera,
                        onCheckedChange = { mirrorCamera = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Terracotta)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mirror Saved Photo Result", color = Espresso, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Flip final captured photo horizontally", color = MutedCocoa, fontSize = 12.sp)
                    }
                    Switch(
                        checked = mirrorResult,
                        onCheckedChange = { mirrorResult = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Terracotta)
                    )
                }

                // Live Camera Test Preview
                HorizontalDivider(color = BorderWarm)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Live Camera Test Feed", color = Espresso, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Verify clean HDMI / USB feed and mirror orientation", color = MutedCocoa, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { isTestingCamera = !isTestingCamera },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTestingCamera) Color(0xFFDC2626) else Terracotta,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(if (isTestingCamera) "⏹ Stop Feed" else "▶ Test Camera", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (testCameraError != null) {
                    Text(testCameraError!!, color = WarmGold, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }

                if (isTestingCamera) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                            .border(2.dp, BorderWarm, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isExternalFacing) {
                            if (isAdminHdmiActive && adminHdmiController != null) {
                                HdmiLiveFeed(
                                    controller = adminHdmiController,
                                    mirror = mirrorCamera,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (adminSonyController != null) {
                                SonyLiveFeed(
                                    controller = adminSonyController,
                                    mirror = mirrorCamera,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (adminSonyState !is SonyCameraState.Ready) {
                                    SonyStatusCard(
                                        state = adminSonyState,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { scaleX = if (mirrorCamera) -1f else 1f }
                            )
                        }

                        Surface(
                            color = CardCream.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "SOURCE: $facing • TARGET: $resolution • MIRROR: ${if (mirrorCamera) "ON" else "OFF"}",
                                color = Espresso,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Collapsible Sony a6000 / a6400 / a6700 & Flash Setup Guide
        Card(
            colors = CardDefaults.cardColors(containerColor = CardCream),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            border = BorderStroke(1.dp, Terracotta.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RetroCardBrush)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSonyGuide = !showSonyGuide },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "📖 Sony a6000 / a6400 / a6700 & Studio Flash Guide",
                            style = TextStyle(
                                brush = RetroTitleBrush,
                                fontFamily = FontFamily.Serif,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            "Tap to view Dual USB Hub (HDMI + Shutter Trigger), USB Streaming & Strobe settings",
                            color = MutedCocoa,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = if (showSonyGuide) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Terracotta
                    )
                }

                if (showSonyGuide) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            0 to "🔌 1. Cables & Dual Hub",
                            1 to "⚙️ 2. Sony Menu Setup",
                            2 to "⚡ 3. Flash / Lighting",
                            3 to "🎛️ 4. Exposure Cheat Sheet"
                        ).forEach { (idx, title) ->
                            FilterChip(
                                selected = guideSection == idx,
                                onClick = { guideSection = idx },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                label = { Text(title, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SoftPeach,
                                    selectedLabelColor = Espresso,
                                    labelColor = MutedCocoa
                                )
                            )
                        }
                    }

                    Surface(
                        color = SurfaceSand.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BorderWarm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (guideSection) {
                                0 -> {
                                    Text("Single Micro-USB / USB-C Cable (Direct OTG Connection):", color = Terracotta, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("• Sony a6400: Connect camera Micro-USB port to tablet USB-C port using a USB OTG data cable.\n• Sony a6700: Connect camera USB-C port directly to tablet USB-C port.\n• Delivers full Live Preview stream AND full-resolution photo downloads straight over the single USB cable!\n• No capture card or HDMI cable needed.", color = Espresso, fontSize = 12.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text("Optional Dual Hub Connection (Alternative for a6000 / a6400):", color = Espresso, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("• If you prefer HDMI for 60fps hardware video feed: Connect USB Hub to tablet. Plug 1 (Video): Micro-HDMI → HDMI Capture Card. Plug 2 (Shutter/Photo Transfer): Micro-USB → USB Hub.", color = MutedCocoa, fontSize = 12.sp)
                                }
                                1 -> {
                                    Text("1. Sony a6400 Menu Checklist (CRITICAL):", color = Terracotta, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("• MENU → Network (🌐) → [Ctrl w/ Smartphone] → Set to [OFF] (If ON, PC Remote is blocked!)\n• MENU → Setup (🧰) → [USB Connection] → Set to [PC Remote] (Do NOT use Auto or Mass Storage)\n• MENU → Setup (🧰) → [PC Remote Settings] → [Still Img. Save Dest.] → Set to [PC+Camera] (Ensures camera shutter sends photo to app)\n• MENU → Setup (🧰) → [USB Power Supply] → Set to [OFF]", color = Espresso, fontSize = 12.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("2. Sony a6700 Menu Checklist:", color = Espresso, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("• MENU → Setup (🧰) → USB → USB Connection Mode → PC Remote\n• MENU → Network (🌐) → Cnct./PC Remote → PC Remote Function → PC Remote → ON\n• MENU → Network (🌐) → Cnct./PC Remote → PC Remote Function → Still Img. Save Dest. → PC+Camera", color = MutedCocoa, fontSize = 12.sp)
                                }
                                2 -> {
                                    Text("External Studio Flash / Hot-Shoe Strobe (Godox):", color = Terracotta, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("• Mount your wireless flash transmitter (e.g. Godox Xpro-S / X2T-S) onto the Sony hot-shoe.\n• Because Mingle Booth fires the physical Sony shutter via PC Remote PTP (S1 focus + S2 release), the hot-shoe sync triggers your external flash and strobe automatically on every pose!\n• Set MENU → Shooting (📷) → Shutter/Silent → Silent Mode → OFF (Flash requires mechanical / electronic front curtain shutter).\n• Set MENU → Shooting (📷) → Image Quality/Rec → Live View Display → Setting Effect → Shot. Result Preview OFF so the live preview doesn't appear black indoors.", color = Espresso, fontSize = 12.sp)
                                }
                                3 -> {
                                    Text("Recommended Booth Exposure (Manual Mode 'M'):", color = Espresso, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("• Camera Dial: Manual Mode (M)\n• Shutter Speed: 1/125s – 1/160s (within Sony flash sync speed limit)\n• Aperture: f/4.0 – f/5.6 (deep depth of field for groups)\n• ISO: 100 – 400 (cleanest image with external strobe)\n• Focus Mode: AF-S (Single-shot AF with Wide/Zone) or Manual Focus (MF) locked to booth backdrop distance\n• Image Quality: JPEG Fine / Extra Fine (or RAW+JPEG)", color = Espresso, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardCream),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            border = BorderStroke(1.dp, BorderWarm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RetroCardBrush)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "🎨 Keepsake Strip Appearance",
                    style = TextStyle(
                        brush = RetroTitleBrush,
                        fontFamily = FontFamily.Serif,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = bgColor,
                        onValueChange = { bgColor = it },
                        label = { Text("Strip Bg Hex", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Espresso,
                            unfocusedTextColor = Espresso,
                            focusedBorderColor = Terracotta,
                            unfocusedBorderColor = BorderWarm
                        )
                    )
                    OutlinedTextField(
                        value = textColor,
                        onValueChange = { textColor = it },
                        label = { Text("Strip Text Hex", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Espresso,
                            unfocusedTextColor = Espresso,
                            focusedBorderColor = Terracotta,
                            unfocusedBorderColor = BorderWarm
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Event Title on Strip Footer", color = Espresso, fontSize = 14.sp)
                    Switch(
                        checked = showTitle,
                        onCheckedChange = { showTitle = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Terracotta)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Capture Date on Strip Footer", color = Espresso, fontSize = 14.sp)
                    Switch(
                        checked = showDate,
                        onCheckedChange = { showDate = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Terracotta)
                    )
                }

                HorizontalDivider(color = BorderWarm.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Save to Local Device Gallery", color = Espresso, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Complete backup: saves photostrip, animated GIF, and all original poses to device Pictures before cloud upload",
                            color = Espresso.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = saveToLocalGallery,
                        onCheckedChange = { saveToLocalGallery = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SageGreen)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(50), spotColor = Terracotta)
                        .clip(RoundedCornerShape(50))
                        .background(RetroButtonBrush)
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(enabled = !isBusy) {
                            onSave(facing, resolution, mirrorCamera, mirrorResult, bgColor, textColor, showTitle, showDate, saveToLocalGallery)
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "💾 SAVE CAMERA & STRIP SETTINGS",
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryTab(
    sessions: List<PhotoSessionItem>,
    events: List<EventItem>,
    activeEventId: String?,
    isBusy: Boolean = false,
    batchProgress: BatchRegenerateProgress = BatchRegenerateProgress(),
    onFilterByEvent: (String?) -> Unit,
    onDeleteSession: (PhotoSessionItem) -> Unit,
    onRegenerateSession: (PhotoSessionItem) -> Unit = {},
    onRegenerateAllEventPhotos: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var selectedFilterId by remember(activeEventId) { mutableStateOf(activeEventId) }
    var qrDialogSession by remember { mutableStateOf<PhotoSessionItem?>(null) }
    var qrDialogBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sessionToRegenerate by remember { mutableStateOf<PhotoSessionItem?>(null) }
    var eventQrDialogForGallery by remember { mutableStateOf<EventItem?>(null) }
    var eventQrBitmapForGallery by remember { mutableStateOf<Bitmap?>(null) }
    var eventToRegenerateAllInGallery by remember { mutableStateOf<EventItem?>(null) }

    LaunchedEffect(qrDialogSession) {
        val s = qrDialogSession
        if (s != null) {
            qrDialogBitmap = null
            qrDialogBitmap = BoothUtils.generateQrBitmap(s.viewerUrl, 512)
        }
    }

    LaunchedEffect(eventQrDialogForGallery) {
        val ev = eventQrDialogForGallery
        if (ev != null) {
            eventQrBitmapForGallery = null
            eventQrBitmapForGallery = BoothUtils.generateQrBitmap(ev.galleryUrl, 512)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Event filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedFilterId == null,
                onClick = {
                    selectedFilterId = null
                    onFilterByEvent(null)
                },
                label = { Text("All Events") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SoftPeach,
                    selectedLabelColor = Espresso,
                    labelColor = MutedCocoa
                )
            )
            events.forEach { ev ->
                FilterChip(
                    selected = selectedFilterId == ev.id,
                    onClick = {
                        selectedFilterId = ev.id
                        onFilterByEvent(ev.id)
                    },
                    label = { Text(ev.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SoftPeach,
                        selectedLabelColor = Espresso,
                        labelColor = MutedCocoa
                    )
                )
            }
        }

        val currentEvent = events.find { it.id == selectedFilterId }
        if (currentEvent != null) {
            Surface(
                color = SoftPeach.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Terracotta.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Event Web Gallery",
                                color = Espresso,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                currentEvent.galleryUrl,
                                color = Terracotta,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "Scan QR or copy link to view all photos captured for this event",
                            color = MutedCocoa,
                            fontSize = 10.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { eventQrDialogForGallery = currentEvent }
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = "Event Barcode QR", tint = Terracotta)
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Event Photos Link", currentEvent.galleryUrl))
                                Toast.makeText(context, "Copied link for all photos: ${currentEvent.galleryUrl}", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Event Link", tint = Espresso)
                        }
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentEvent.galleryUrl))
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Open Web Gallery", tint = Espresso)
                        }
                        IconButton(
                            onClick = { eventToRegenerateAllInGallery = currentEvent },
                            enabled = !isBusy && !batchProgress.isRunning
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Regenerate All Event Photos",
                                tint = if (batchProgress.isRunning && batchProgress.eventId == currentEvent.id) Terracotta else Espresso
                            )
                        }
                    }
                }
            }
        }

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No photo sessions captured for this event yet.",
                    color = MutedCocoa,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sessions, key = { it.id }) { session ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardCream),
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, BorderWarm)
                    ) {
                        Column(
                            modifier = Modifier
                                .background(RetroCardBrush)
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceSand),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = session.stripUrl,
                                    contentDescription = "Session Strip",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "[ ${session.photoCount} EXP · #${session.id.takeLast(6).uppercase()} ]",
                                color = Espresso,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { qrDialogSession = session },
                                    enabled = !isBusy
                                ) {
                                    Icon(Icons.Default.QrCode, contentDescription = "Show QR", tint = Terracotta)
                                }

                                IconButton(
                                    onClick = { sessionToRegenerate = session },
                                    enabled = !isBusy
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Regenerate Session Assets", tint = Terracotta)
                                }

                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(session.viewerUrl))
                                        context.startActivity(intent)
                                    },
                                    enabled = !isBusy
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = "Open Web Viewer", tint = Espresso)
                                }

                                IconButton(
                                    onClick = { onDeleteSession(session) },
                                    enabled = !isBusy
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Session", tint = Color(0xFFDC2626))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (sessionToRegenerate != null) {
        val s = sessionToRegenerate!!
        Dialog(onDismissRequest = { if (!isBusy) sessionToRegenerate = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardCream),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.5.dp, Terracotta.copy(alpha = 0.5f)),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(RetroCardBrush)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Regenerate Session Photos",
                        style = TextStyle(
                            brush = RetroTitleBrush,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "[ SESSION #${s.id.takeLast(6).uppercase()} ]",
                        color = Terracotta,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Regenerate this session from its original raw photos?\n\nThis will re-composite the high-resolution photo strip and re-encode the animated GIF with crisp colors and smooth dithering.",
                        color = Espresso,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { sessionToRegenerate = null },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderWarm)
                        ) {
                            Text("Cancel", color = Espresso)
                        }

                        Button(
                            onClick = {
                                val target = sessionToRegenerate
                                sessionToRegenerate = null
                                if (target != null) {
                                    onRegenerateSession(target)
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White)
                        ) {
                            Text("Regenerate", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (qrDialogSession != null) {
        val s = qrDialogSession!!
        Dialog(onDismissRequest = { qrDialogSession = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardCream),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.5.dp, Terracotta.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .background(RetroCardBrush)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Keepsake QR Code",
                        style = TextStyle(
                            brush = RetroTitleBrush,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(s.viewerUrl, color = Terracotta, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Spacer(Modifier.height(16.dp))

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderWarm),
                        modifier = Modifier.size(200.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (qrDialogBitmap != null) {
                                Image(
                                    bitmap = qrDialogBitmap!!.asImageBitmap(),
                                    contentDescription = "QR",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp)
                                )
                            } else {
                                CircularProgressIndicator(color = Terracotta)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { qrDialogSession = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (eventQrDialogForGallery != null) {
        val ev = eventQrDialogForGallery!!
        Dialog(onDismissRequest = { eventQrDialogForGallery = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardCream),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.5.dp, Terracotta.copy(alpha = 0.5f)),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(RetroCardBrush)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "All Photos Barcode / QR",
                        style = TextStyle(
                            brush = RetroTitleBrush,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${ev.title} · ${ev.subtitle}",
                        color = Espresso,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        ev.galleryUrl,
                        color = Terracotta,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderWarm),
                        modifier = Modifier.size(220.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (eventQrBitmapForGallery != null) {
                                Image(
                                    bitmap = eventQrBitmapForGallery!!.asImageBitmap(),
                                    contentDescription = "Event Photos Barcode QR",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                )
                            } else {
                                CircularProgressIndicator(color = Terracotta)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Scan barcode / QR with phone camera to view and download all photos from this event.",
                        color = MutedCocoa,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Event Photos Link", ev.galleryUrl))
                                Toast.makeText(context, "Copied link to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SoftPeach,
                                contentColor = Espresso
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Terracotta, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Link", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ev.galleryUrl))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SoftPeach,
                                contentColor = Espresso
                            )
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Espresso, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { eventQrDialogForGallery = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Close", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (eventToRegenerateAllInGallery != null) {
        val ev = eventToRegenerateAllInGallery!!
        Dialog(onDismissRequest = { if (!batchProgress.isRunning) eventToRegenerateAllInGallery = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardCream),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.5.dp, Terracotta.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .background(RetroCardBrush)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        tint = Terracotta,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Regenerate Event Photos",
                        style = TextStyle(
                            brush = RetroTitleBrush,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "[ ${ev.title.uppercase()} ]",
                        color = Terracotta,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Regenerate all photo sessions for this event in the background?\n\nThis will re-composite all photo strips in full template resolution (2R/4R) and re-encode 720p Hi-Res Composite animated GIFs from the original raw photos.\n\nYou can continue using the booth while the background process is running.",
                        color = Espresso,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { eventToRegenerateAllInGallery = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderWarm)
                        ) {
                            Text("Cancel", color = Espresso)
                        }

                        Button(
                            onClick = {
                                val target = eventToRegenerateAllInGallery
                                eventToRegenerateAllInGallery = null
                                if (target != null) {
                                    onRegenerateAllEventPhotos(target.id, target.title)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White)
                        ) {
                            Text("Start", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
