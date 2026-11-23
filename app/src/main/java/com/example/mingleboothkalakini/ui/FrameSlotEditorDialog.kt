package com.example.mingleboothkalakini.ui

import android.graphics.Bitmap
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mingleboothkalakini.data.Slot
import com.example.mingleboothkalakini.util.BoothUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class SlotDragMode {
    MOVE,
    RESIZE_TL,
    RESIZE_TR,
    RESIZE_BL,
    RESIZE_BR,
    DRAW
}

private data class DrawRectPreview(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float
)

@Composable
fun FrameSlotEditorDialog(
    frameName: String,
    frameBitmap: Bitmap,
    initialSlots: List<Slot>,
    onDismiss: () -> Unit,
    onSave: (List<Slot>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val frameImageBitmap = remember(frameBitmap) { frameBitmap.asImageBitmap() }

    var slots by remember(frameBitmap, initialSlots) {
        mutableStateOf(
            initialSlots.sortedBy { it.slotIndex }.mapIndexed { idx, s -> s.copy(slotIndex = idx) }
        )
    }
    var selectedSlotIndex by remember { mutableStateOf<Int?>(null) }
    var addMode by remember { mutableStateOf(false) }
    var isDetecting by remember { mutableStateOf(false) }
    var drawPreview by remember { mutableStateOf<DrawRectPreview?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardCream),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, Terracotta.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RetroCardBrush)
            ) {
                // Header
                Surface(
                    color = CardCream,
                    border = BorderStroke(1.dp, BorderWarm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Configure Photo Placement Slots",
                                style = TextStyle(
                                    brush = RetroTitleBrush,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 19.sp
                                )
                            )
                            Text(
                                text = "$frameName · ${frameBitmap.width}×${frameBitmap.height}px · Tap to select, drag to move, drag corners to resize",
                                color = MutedCocoa,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    if (isDetecting) return@FilledTonalButton
                                    isDetecting = true
                                    scope.launch {
                                        val detected = withContext(Dispatchers.Default) {
                                            BoothUtils.detectSlots(frameBitmap)
                                        }
                                        isDetecting = false
                                        if (detected.isNotEmpty()) {
                                            slots = detected
                                            selectedSlotIndex = 0
                                            Toast.makeText(
                                                context,
                                                "Detected ${detected.size} transparent slot(s)!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "No transparent cutouts detected. Use '+ Draw Slot' to place manually.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !isDetecting,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = SoftPeach,
                                    contentColor = Espresso
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Terracotta,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isDetecting) "Detecting…" else "Auto-Detect",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            FilledTonalButton(
                                onClick = { addMode = !addMode },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (addMode) Terracotta else SoftPeach,
                                    contentColor = if (addMode) Color.White else Espresso
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.AddBox,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (addMode) "Drawing Mode ON" else "+ Draw Slot",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Espresso)
                            }
                        }
                    }
                }

                // Split Editor Body: Canvas Left + Slots Sidebar Right
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Left Interactive Canvas Pane
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF1A1A28))
                            .border(1.5.dp, if (addMode) Terracotta else BorderWarm, RoundedCornerShape(18.dp))
                    ) {
                        val density = LocalDensity.current
                        val boxW = constraints.maxWidth.toFloat()
                        val boxH = constraints.maxHeight.toFloat()
                        val frameW = frameBitmap.width.toFloat().coerceAtLeast(1f)
                        val frameH = frameBitmap.height.toFloat().coerceAtLeast(1f)

                        val padPx = with(density) { 16.dp.toPx() }
                        val availW = (boxW - padPx * 2f).coerceAtLeast(1f)
                        val availH = (boxH - padPx * 2f).coerceAtLeast(1f)
                        val scale = min(availW / frameW, availH / frameH).coerceAtLeast(0.01f)
                        val drawW = frameW * scale
                        val drawH = frameH * scale
                        val offsetX = (boxW - drawW) / 2f
                        val offsetY = (boxH - drawH) / 2f

                        val currentSlots by rememberUpdatedState(slots)
                        val currentSelected by rememberUpdatedState(selectedSlotIndex)
                        val currentAddMode by rememberUpdatedState(addMode)

                        val badgeTextPaint = remember {
                            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.WHITE
                                textAlign = android.graphics.Paint.Align.CENTER
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            }
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(scale, offsetX, offsetY, frameW, frameH) {
                                    val minSlotPx = max(40f, 30f / scale)
                                    val handleHitRadiusPx = with(density) { 26.dp.toPx() }

                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()
                                        val startPos = down.position
                                        val startFx = ((startPos.x - offsetX) / scale).coerceIn(0f, frameW)
                                        val startFy = ((startPos.y - offsetY) / scale).coerceIn(0f, frameH)

                                        var dragMode: SlotDragMode? = null
                                        var activeSlotSnapshot: Slot? = null

                                        if (currentAddMode) {
                                            dragMode = SlotDragMode.DRAW
                                            drawPreview = DrawRectPreview(startFx, startFy, startFx, startFy)
                                        } else {
                                            val sel = currentSelected?.let { idx ->
                                                currentSlots.firstOrNull { it.slotIndex == idx }
                                            }
                                            if (sel != null) {
                                                val tl = Offset(offsetX + sel.x * scale, offsetY + sel.y * scale)
                                                val tr = Offset(offsetX + (sel.x + sel.w) * scale, offsetY + sel.y * scale)
                                                val bl = Offset(offsetX + sel.x * scale, offsetY + (sel.y + sel.h) * scale)
                                                val br = Offset(offsetX + (sel.x + sel.w) * scale, offsetY + (sel.y + sel.h) * scale)

                                                dragMode = when {
                                                    hypot(startPos.x - tl.x, startPos.y - tl.y) <= handleHitRadiusPx -> SlotDragMode.RESIZE_TL
                                                    hypot(startPos.x - tr.x, startPos.y - tr.y) <= handleHitRadiusPx -> SlotDragMode.RESIZE_TR
                                                    hypot(startPos.x - bl.x, startPos.y - bl.y) <= handleHitRadiusPx -> SlotDragMode.RESIZE_BL
                                                    hypot(startPos.x - br.x, startPos.y - br.y) <= handleHitRadiusPx -> SlotDragMode.RESIZE_BR
                                                    else -> null
                                                }
                                                if (dragMode != null) {
                                                    activeSlotSnapshot = sel
                                                }
                                            }

                                            if (dragMode == null) {
                                                val rawFx = (startPos.x - offsetX) / scale
                                                val rawFy = (startPos.y - offsetY) / scale
                                                val hit = currentSlots
                                                    .sortedByDescending { it.slotIndex }
                                                    .firstOrNull { s ->
                                                        rawFx >= s.x && rawFx <= s.x + s.w &&
                                                            rawFy >= s.y && rawFy <= s.y + s.h
                                                    }
                                                if (hit != null) {
                                                    selectedSlotIndex = hit.slotIndex
                                                    dragMode = SlotDragMode.MOVE
                                                    activeSlotSnapshot = hit
                                                } else {
                                                    selectedSlotIndex = null
                                                }
                                            }
                                        }

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                                ?: event.changes.firstOrNull()
                                                ?: break

                                            if (!change.pressed) {
                                                if (dragMode == SlotDragMode.DRAW) {
                                                    val preview = drawPreview
                                                    drawPreview = null
                                                    if (preview != null) {
                                                        val rx = min(preview.x1, preview.x2).roundToInt()
                                                        val ry = min(preview.y1, preview.y2).roundToInt()
                                                        val rw = abs(preview.x2 - preview.x1).roundToInt()
                                                        val rh = abs(preview.y2 - preview.y1).roundToInt()
                                                        if (rw >= minSlotPx && rh >= minSlotPx) {
                                                            val newIdx = currentSlots.size
                                                            val newSlot = Slot(
                                                                slotIndex = newIdx,
                                                                x = rx.coerceIn(0, (frameW - 1).toInt()),
                                                                y = ry.coerceIn(0, (frameH - 1).toInt()),
                                                                w = rw.coerceAtMost(frameW.toInt() - rx),
                                                                h = rh.coerceAtMost(frameH.toInt() - ry)
                                                            )
                                                            slots = currentSlots + newSlot
                                                            selectedSlotIndex = newIdx
                                                        }
                                                    }
                                                    addMode = false
                                                }
                                                break
                                            }

                                            change.consume()
                                            val curPos = change.position
                                            val curFx = ((curPos.x - offsetX) / scale).coerceIn(0f, frameW)
                                            val curFy = ((curPos.y - offsetY) / scale).coerceIn(0f, frameH)

                                            if (dragMode == SlotDragMode.DRAW) {
                                                drawPreview = drawPreview?.copy(x2 = curFx, y2 = curFy)
                                                continue
                                            }

                                            val snap = activeSlotSnapshot ?: continue
                                            val dx = (curPos.x - startPos.x) / scale
                                            val dy = (curPos.y - startPos.y) / scale

                                            val updated = when (dragMode) {
                                                SlotDragMode.MOVE -> {
                                                    val nx = (snap.x + dx).roundToInt()
                                                        .coerceIn(0, (frameW - snap.w).toInt().coerceAtLeast(0))
                                                    val ny = (snap.y + dy).roundToInt()
                                                        .coerceIn(0, (frameH - snap.h).toInt().coerceAtLeast(0))
                                                    snap.copy(x = nx, y = ny)
                                                }
                                                SlotDragMode.RESIZE_BR -> {
                                                    val nw = (snap.w + dx).roundToInt()
                                                        .coerceIn(minSlotPx.roundToInt(), (frameW - snap.x).toInt().coerceAtLeast(minSlotPx.roundToInt()))
                                                    val nh = (snap.h + dy).roundToInt()
                                                        .coerceIn(minSlotPx.roundToInt(), (frameH - snap.y).toInt().coerceAtLeast(minSlotPx.roundToInt()))
                                                    snap.copy(w = nw, h = nh)
                                                }
                                                SlotDragMode.RESIZE_TR -> {
                                                    val nw = (snap.w + dx).roundToInt()
                                                        .coerceIn(minSlotPx.roundToInt(), (frameW - snap.x).toInt().coerceAtLeast(minSlotPx.roundToInt()))
                                                    val newH = (snap.h - dy).coerceIn(minSlotPx, (snap.y + snap.h).toFloat())
                                                    val newY = (snap.y + snap.h - newH).roundToInt().coerceAtLeast(0)
                                                    snap.copy(y = newY, w = nw, h = newH.roundToInt())
                                                }
                                                SlotDragMode.RESIZE_BL -> {
                                                    val newW = (snap.w - dx).coerceIn(minSlotPx, (snap.x + snap.w).toFloat())
                                                    val newX = (snap.x + snap.w - newW).roundToInt().coerceAtLeast(0)
                                                    val nh = (snap.h + dy).roundToInt()
                                                        .coerceIn(minSlotPx.roundToInt(), (frameH - snap.y).toInt().coerceAtLeast(minSlotPx.roundToInt()))
                                                    snap.copy(x = newX, w = newW.roundToInt(), h = nh)
                                                }
                                                SlotDragMode.RESIZE_TL -> {
                                                    val newW = (snap.w - dx).coerceIn(minSlotPx, (snap.x + snap.w).toFloat())
                                                    val newX = (snap.x + snap.w - newW).roundToInt().coerceAtLeast(0)
                                                    val newH = (snap.h - dy).coerceIn(minSlotPx, (snap.y + snap.h).toFloat())
                                                    val newY = (snap.y + snap.h - newH).roundToInt().coerceAtLeast(0)
                                                    snap.copy(x = newX, y = newY, w = newW.roundToInt(), h = newH.roundToInt())
                                                }
                                                else -> snap
                                            }

                                            slots = currentSlots.map {
                                                if (it.slotIndex == snap.slotIndex) updated else it
                                            }
                                        }
                                    }
                                }
                        ) {
                            // Checkerboard-like backdrop behind transparent frame cutouts
                            drawRect(
                                color = Color(0xFF2B2B3D),
                                topLeft = Offset(offsetX, offsetY),
                                size = Size(drawW, drawH)
                            )

                            drawImage(
                                image = frameImageBitmap,
                                dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                                dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt())
                            )

                            val sorted = slots.sortedBy { it.slotIndex }
                            val badgeR = with(density) { 13.dp.toPx() }
                            val handleR = with(density) { 8.dp.toPx() }
                            badgeTextPaint.textSize = with(density) { 12.sp.toPx() }

                            for (drawSelectedPass in listOf(false, true)) {
                                for (slot in sorted) {
                                    val isSel = slot.slotIndex == selectedSlotIndex
                                    if (isSel != drawSelectedPass) continue

                                    val sx = offsetX + slot.x * scale
                                    val sy = offsetY + slot.y * scale
                                    val sw = slot.w * scale
                                    val sh = slot.h * scale

                                    val fillClr = if (isSel) Color(0x45C084FC) else Color(0x2838BDF8)
                                    val strokeClr = if (isSel) Color(0xFFC084FC) else Color(0xFF38BDF8)
                                    val strokeWidth = with(density) { if (isSel) 2.5.dp.toPx() else 1.5.dp.toPx() }

                                    drawRect(
                                        color = fillClr,
                                        topLeft = Offset(sx, sy),
                                        size = Size(sw, sh)
                                    )
                                    drawRect(
                                        color = strokeClr,
                                        topLeft = Offset(sx, sy),
                                        size = Size(sw, sh),
                                        style = Stroke(width = strokeWidth)
                                    )

                                    if (isSel) {
                                        val corners = listOf(
                                            Offset(sx, sy),
                                            Offset(sx + sw, sy),
                                            Offset(sx, sy + sh),
                                            Offset(sx + sw, sy + sh)
                                        )
                                        for (corner in corners) {
                                            drawCircle(color = Color.White, radius = handleR, center = corner)
                                            drawCircle(
                                                color = Color(0xFFC084FC),
                                                radius = handleR,
                                                center = corner,
                                                style = Stroke(width = with(density) { 2.dp.toPx() })
                                            )
                                        }
                                    }

                                    // Number badge
                                    val bx = sx + badgeR + with(density) { 6.dp.toPx() }
                                    val by = sy + badgeR + with(density) { 6.dp.toPx() }
                                    drawCircle(color = strokeClr, radius = badgeR, center = Offset(bx, by))
                                    val textY = by - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
                                    drawContext.canvas.nativeCanvas.drawText(
                                        "${slot.slotIndex + 1}",
                                        bx,
                                        textY,
                                        badgeTextPaint
                                    )
                                }
                            }

                            // Rubberband draw preview
                            drawPreview?.let { preview ->
                                val rx = offsetX + min(preview.x1, preview.x2) * scale
                                val ry = offsetY + min(preview.y1, preview.y2) * scale
                                val rw = abs(preview.x2 - preview.x1) * scale
                                val rh = abs(preview.y2 - preview.y1) * scale
                                drawRect(
                                    color = Color(0x33C084FC),
                                    topLeft = Offset(rx, ry),
                                    size = Size(rw, rh)
                                )
                                drawRect(
                                    color = Color(0xFFC084FC),
                                    topLeft = Offset(rx, ry),
                                    size = Size(rw, rh),
                                    style = Stroke(
                                        width = with(density) { 2.dp.toPx() },
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                    )
                                )
                            }
                        }

                        if (addMode) {
                            Surface(
                                color = Terracotta.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp)
                            ) {
                                Text(
                                    text = "Touch & drag on the frame to draw a new photo slot",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Right Sidebar: Photo Slots List & Actions
                    Surface(
                        color = CardCream,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, BorderWarm),
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Photo Slots",
                                    color = Espresso,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Surface(
                                    color = SoftPeach,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, Terracotta.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "${slots.size} slot${if (slots.size != 1) "s" else ""}",
                                        color = Terracotta,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            if (slots.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No slots defined yet.\nTap '+ Draw Slot' or 'Auto-Detect' above.",
                                        color = MutedCocoa,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Serif
                                    )
                                }
                            } else {
                                val sorted = slots.sortedBy { it.slotIndex }
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(sorted, key = { it.slotIndex }) { slot ->
                                        val isSel = slot.slotIndex == selectedSlotIndex
                                        Surface(
                                            color = if (isSel) SoftPeach.copy(alpha = 0.8f) else SurfaceSand.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSel) Terracotta else BorderWarm
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedSlotIndex = slot.slotIndex }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isSel) Color(0xFFC084FC) else Color(0xFF38BDF8)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "${slot.slotIndex + 1}",
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = "${slot.w} × ${slot.h} px",
                                                            color = Espresso,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp
                                                        )
                                                        Text(
                                                            text = "at (${slot.x}, ${slot.y})",
                                                            color = MutedCocoa,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = {
                                                            val idx = slot.slotIndex
                                                            if (idx > 0) {
                                                                slots = slots.map {
                                                                    when (it.slotIndex) {
                                                                        idx -> it.copy(slotIndex = idx - 1)
                                                                        idx - 1 -> it.copy(slotIndex = idx)
                                                                        else -> it
                                                                    }
                                                                }
                                                                selectedSlotIndex = idx - 1
                                                            }
                                                        },
                                                        enabled = slot.slotIndex > 0,
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.ArrowUpward,
                                                            contentDescription = "Capture earlier",
                                                            tint = if (slot.slotIndex > 0) Espresso else MutedCocoa.copy(alpha = 0.3f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            val idx = slot.slotIndex
                                                            if (idx < sorted.size - 1) {
                                                                slots = slots.map {
                                                                    when (it.slotIndex) {
                                                                        idx -> it.copy(slotIndex = idx + 1)
                                                                        idx + 1 -> it.copy(slotIndex = idx)
                                                                        else -> it
                                                                    }
                                                                }
                                                                selectedSlotIndex = idx + 1
                                                            }
                                                        },
                                                        enabled = slot.slotIndex < sorted.size - 1,
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.ArrowDownward,
                                                            contentDescription = "Capture later",
                                                            tint = if (slot.slotIndex < sorted.size - 1) Espresso else MutedCocoa.copy(alpha = 0.3f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            val delIdx = slot.slotIndex
                                                            val remaining = slots
                                                                .filter { it.slotIndex != delIdx }
                                                                .sortedBy { it.slotIndex }
                                                                .mapIndexed { i, s -> s.copy(slotIndex = i) }
                                                            slots = remaining
                                                            selectedSlotIndex = when {
                                                                selectedSlotIndex == delIdx -> null
                                                                selectedSlotIndex != null && selectedSlotIndex!! > delIdx -> selectedSlotIndex!! - 1
                                                                else -> selectedSlotIndex
                                                            }
                                                        },
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = "Delete slot",
                                                            tint = Color(0xFFDC2626),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Footer Buttons
                            Button(
                                onClick = {
                                    if (slots.isEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "Please define at least one photo slot.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        val finalSlots = slots
                                            .sortedBy { it.slotIndex }
                                            .mapIndexed { i, s -> s.copy(slotIndex = i) }
                                        onSave(finalSlots)
                                    }
                                },
                                enabled = slots.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Terracotta,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Frame", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, BorderWarm)
                            ) {
                                Text("Cancel", color = Espresso)
                            }
                        }
                    }
                }
            }
        }
    }
}
