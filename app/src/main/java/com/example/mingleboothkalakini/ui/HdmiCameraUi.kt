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
import com.example.mingleboothkalakini.camera.HdmiCaptureController
import com.example.mingleboothkalakini.camera.HdmiCaptureState

/**
 * Live view frames from the HDMI capture card (e.g. Sony a6000 / a6400 via HDMI),
 * rendered smoothly with mirroring support and aspect ratio scaling.
 */
@Composable
fun HdmiLiveFeed(
    controller: HdmiCaptureController,
    mirror: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val frame by controller.liveFrame.collectAsState()
    val state by controller.state.collectAsState()

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

        if (state !is HdmiCaptureState.Ready) {
            HdmiStatusCard(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

fun describeHdmiState(state: HdmiCaptureState): Pair<String, String> = when (state) {
    HdmiCaptureState.NoDevice -> "HDMI capture card not detected" to
        "Connect your Sony a6000 / a6400 HDMI cable to the USB capture card, and plug it into the tablet.\n" +
        "Ensure the camera is powered on and HDMI Info Display is set to 'Off'."
    HdmiCaptureState.NeedsPermission -> "USB permission needed" to
        "Tap “Allow” on the Android USB dialog (tick “Always allow” so it won't ask again)."
    HdmiCaptureState.Connecting -> "Connecting to HDMI capture…" to "Starting video feed from camera."
    is HdmiCaptureState.Ready -> state.name to "HDMI video stream active"
    is HdmiCaptureState.Error -> "HDMI connection error" to
        "${state.message}\nReconnecting automatically…"
}

@Composable
fun HdmiStatusCard(state: HdmiCaptureState, modifier: Modifier = Modifier) {
    val (title, hint) = describeHdmiState(state)
    val busy = state is HdmiCaptureState.Connecting
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardCream.copy(alpha = 0.96f)),
            border = BorderStroke(1.5.dp, BorderWarm),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Terracotta, strokeWidth = 3.dp)
                    Spacer(Modifier.height(18.dp))
                }
                Text(
                    title,
                    color = Espresso,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    hint,
                    color = MutedCocoa,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "SONY a6000 / a6400 • HDMI CAPTURE",
                    color = Terracotta,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
