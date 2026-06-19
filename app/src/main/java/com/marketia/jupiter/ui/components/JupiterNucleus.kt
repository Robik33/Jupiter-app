package com.marketia.jupiter.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.marketia.jupiter.ui.screens.nucleus.NucleusState

@Composable
fun JupiterNucleus(
    state: NucleusState,
    modifier: Modifier = Modifier
) {
    val sinEase = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

    val breathDuration = when (state) {
        NucleusState.Listening  -> 700
        NucleusState.Processing -> 500
        else                    -> 2600
    }

    val infinite = rememberInfiniteTransition(label = "nucleus")

    val breatheScale by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(breathDuration, easing = sinEase),
            repeatMode = RepeatMode.Reverse
        ), label = "breathe"
    )

    val waveProgress by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == NucleusState.Listening) 1500 else 3000, easing = LinearEasing)
        ), label = "wave"
    )

    val rotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing)
        ), label = "rotation"
    )

    val glowAlpha by infinite.animateFloat(
        initialValue = 0.06f, targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = sinEase),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    val coreColor by animateColorAsState(
        targetValue = when (state) {
            NucleusState.Idle       -> Color(0xFF00E5FF)
            NucleusState.Listening  -> Color(0xFF00FF88)
            NucleusState.Processing -> Color(0xFF7C4DFF)
            is NucleusState.Responding -> Color(0xFF00E5FF)
        },
        animationSpec = tween(400), label = "coreColor"
    )

    val accentColor by animateColorAsState(
        targetValue = when (state) {
            NucleusState.Idle       -> Color(0xFF7C4DFF)
            NucleusState.Listening  -> Color(0xFF00E5FF)
            NucleusState.Processing -> Color(0xFF00E5FF)
            is NucleusState.Responding -> Color(0xFF7C4DFF)
        },
        animationSpec = tween(400), label = "accentColor"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)
        val coreR = minOf(size.width, size.height) * 0.30f

        // Ambient background glow
        drawCircle(coreColor.copy(alpha = glowAlpha * 0.5f), coreR * 2.8f, center)

        // Expanding wave rings (2 staggered)
        for (i in 0..1) {
            val offset = i * 0.5f
            val wave = ((waveProgress + offset) % 1f)
            val waveR = coreR * (1.25f + wave * 1.4f)
            val waveAlpha = (1f - wave) * (if (state == NucleusState.Listening) 0.45f else 0.22f)
            drawCircle(coreColor.copy(alpha = waveAlpha), waveR, center, style = Stroke(1.5.dp.toPx()))
        }

        // Outer orbital arc (rotating CW)
        val outerOrbR = coreR * 1.22f
        drawArc(
            color = coreColor.copy(alpha = 0.35f),
            startAngle = rotation,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = Offset(cx - outerOrbR, cy - outerOrbR),
            size = Size(outerOrbR * 2, outerOrbR * 2),
            style = Stroke(1.dp.toPx())
        )

        // Inner orbital arc (rotating CCW)
        val innerOrbR = coreR * 1.08f
        drawArc(
            color = accentColor.copy(alpha = 0.25f),
            startAngle = -rotation * 0.65f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(cx - innerOrbR, cy - innerOrbR),
            size = Size(innerOrbR * 2, innerOrbR * 2),
            style = Stroke(0.5.dp.toPx())
        )

        // Core sphere — layered for depth
        val r = coreR * breatheScale
        drawCircle(accentColor.copy(alpha = 0.28f), r * 0.96f, center)
        drawCircle(coreColor.copy(alpha = 0.22f),   r * 0.80f, center)
        drawCircle(coreColor.copy(alpha = 0.32f),   r * 0.60f, center)
        drawCircle(Color.White.copy(alpha = 0.10f), r * 0.38f, center)

        // Bright center point
        drawCircle(coreColor.copy(alpha = 0.7f), 10.dp.toPx(), center)
        drawCircle(Color.White.copy(alpha = 0.9f), 3.dp.toPx(), center)
    }
}
