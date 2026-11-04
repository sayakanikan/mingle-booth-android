package com.example.mingleboothkalakini.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mingleboothkalakini.camera.SonyCameraController
import com.example.mingleboothkalakini.camera.SonyCameraState

/** Live view frames from the Sony camera, drawn like a camera preview (center-crop). */
@Composable
fun SonyLiveFeed(
    controller: SonyCameraController,
    mirror: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val frame by controller.liveFrame.collectAsState()
    Box(modifier = modifier.background(Color.Black)) {
        frame?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = if (mirror) -1f else 1f }
            )
        }
    }
}

/** Title + hint for a non-ready camera state. */
fun describeSonyState(state: SonyCameraState): Pair<String, String> = when (state) {
    SonyCameraState.NoCamera -> "Sony camera not detected" to
        "Connect the camera with a USB-C to USB-C cable (for a6700) or Micro-USB (for a6000/a6400), " +
        "switch it on, and set Setup ▸ USB ▸ USB Connection Mode ▸ PC Remote.\n" +
        "(If using HDMI Capture Card on a6000/a6400, select 'External UVC / HDMI Capture' in Admin instead of 'Sony a6700 PC Remote')."
    SonyCameraState.NeedsPermission -> "USB permission needed" to
        "Tap “Allow” on the Android USB dialog (tick “Use by default” so it won't ask again)."
    SonyCameraState.WrongMode -> "Camera is not in PC Remote mode" to
        "Set the camera to Setup ▸ USB ▸ USB Connection Mode ▸ PC Remote (not USB Streaming / Mass Storage)."
    SonyCameraState.Connecting -> "Connecting to camera…" to "Starting the PC Remote session."
    is SonyCameraState.Ready -> state.model to "Camera ready"
    is SonyCameraState.Error -> "Camera connection problem" to
        "${state.message}\nReconnecting automatically…"
}

/** Centered status card shown in place of the live view while the Sony camera is unavailable. */
@Composable
fun SonyStatusCard(state: SonyCameraState, modifier: Modifier = Modifier) {
    val (title, hint) = describeSonyState(state)
    val busy = state is SonyCameraState.Connecting
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardCream.copy(alpha = 0.96f)),
            border = BorderStroke(1.5.dp, BorderWarm),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Terracotta, modifier = Modifier.padding(bottom = 12.dp))
                }
                Text(
                    title,
                    color = Espresso,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    hint,
                    color = MutedCocoa,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
