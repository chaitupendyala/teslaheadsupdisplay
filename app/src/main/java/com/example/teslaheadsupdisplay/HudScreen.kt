package com.chaitalkstech.teslaheadsupdisplay

import android.media.ToneGenerator
import android.media.AudioManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun HudScreen(
    currentSpeedMph: Int,
    speedLimit: String,
    currentRoadName: String = "",
    heading: Float = 0f,
    batteryLevel: Int = 0,
    isBatteryCharging: Boolean = false,
    thermalTemp: Float = 0f,
    isAutoDarkMode: Boolean = true,
    isFromCache: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var manualDarkMode by remember { mutableStateOf<Boolean?>(null) }
    val isDarkMode = manualDarkMode ?: isAutoDarkMode

    // Colors based on mode
    val backgroundColor = if (isDarkMode) Color.Black else Color.White
    val mainTextColor = if (isDarkMode) Color.Cyan else Color.Black
    val secondaryTextColor = if (isDarkMode) Color.Gray else Color.DarkGray

    // --- SPEED LIMIT WARNING LOGIC ---
    val limitInt = speedLimit.toIntOrNull()
    val isOverLimit = limitInt != null && currentSpeedMph > limitInt
    val isCriticalOverLimit = limitInt != null && currentSpeedMph > (limitInt + 10)

    val speedColor = when {
        isCriticalOverLimit -> Color.Red
        isOverLimit -> Color(0xFFFFA500) // Orange
        else -> mainTextColor
    }

    // Audio Warning
    var hasWarnedForCurrentLimit by remember { mutableStateOf(false) }
    LaunchedEffect(currentSpeedMph, speedLimit) {
        if (isOverLimit && !hasWarnedForCurrentLimit) {
            try {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                hasWarnedForCurrentLimit = true
            } catch (e: Exception) {}
        } else if (!isOverLimit) {
            hasWarnedForCurrentLimit = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp)
    ) {
        // --- TOP LEFT: TOGGLE ---
        CoolModeToggle(
            isDarkMode = isDarkMode,
            onToggle = { manualDarkMode = !isDarkMode },
            modifier = Modifier.align(Alignment.TopStart)
        )

        // --- TOP RIGHT: COMPASS, BATTERY, THERMAL ---
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Thermal Status
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${thermalTemp.roundToInt()}°C",
                    color = if (thermalTemp > 45) Color.Red else secondaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("TEMP", color = secondaryTextColor, fontSize = 10.sp)
            }

            // Battery Status
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$batteryLevel%${if (isBatteryCharging) "⚡" else ""}",
                    color = if (batteryLevel < 20) Color.Red else secondaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("BATT", color = secondaryTextColor, fontSize = 10.sp)
            }

            // Compass / Heading
            val direction = when (heading) {
                in 337.5..360.0, in 0.0..22.5 -> "N"
                in 22.5..67.5 -> "NE"
                in 67.5..112.5 -> "E"
                in 112.5..157.5 -> "SE"
                in 157.5..202.5 -> "S"
                in 202.5..247.5 -> "SW"
                in 247.5..292.5 -> "W"
                in 292.5..337.5 -> "NW"
                else -> "N"
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = direction,
                    color = mainTextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text("${heading.roundToInt()}°", color = secondaryTextColor, fontSize = 12.sp)
            }
        }

        // --- CENTER: SPEEDOMETER ---
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$currentSpeedMph",
                color = speedColor,
                fontSize = 180.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "MPH",
                color = secondaryTextColor,
                fontSize = 24.sp,
                modifier = Modifier.offset(y = (-20).dp)
            )
        }

        // --- BOTTOM LEFT: ROAD NAME & REFRESH ---
        Row(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (currentRoadName.isNotEmpty()) {
                Text(
                    text = currentRoadName.uppercase(),
                    color = mainTextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }
            if (isFromCache) {
                Text(
                    text = "♻️",
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable { onRefresh() }
                        .padding(4.dp)
                )
            }
        }

        // --- BOTTOM RIGHT: SPEED LIMIT SIGN ---
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(100.dp)
                .background(Color.White, shape = CircleShape)
                .border(width = if (isOverLimit) 8.dp else 6.dp, color = Color.Red, shape = CircleShape)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LIMIT", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = speedLimit, color = Color.Black, fontSize = 42.sp, fontWeight = FontWeight.Black)
            }
        }

        // Warning Text
        if (isOverLimit) {
            Text(
                text = "SLOW DOWN",
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
            )
        }
    }
}

@Composable
fun CoolModeToggle(
    isDarkMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trackWidth = 70.dp
    val trackHeight = 34.dp
    val thumbSize = 26.dp
    val gap = 4.dp

    val trackColor by animateColorAsState(
        targetValue = if (isDarkMode) Color(0xFF2C2C2C) else Color(0xFFE0E0E0),
        animationSpec = tween(300)
    )
    
    val thumbOffset by animateDpAsState(
        targetValue = if (isDarkMode) (trackWidth - thumbSize - gap) else gap,
        animationSpec = tween(300)
    )

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(trackHeight / 2))
            .background(trackColor)
            .clickable { onToggle() }
            .padding(gap),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("☀️", fontSize = 14.sp)
            Text("🌙", fontSize = 14.sp)
        }

        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .background(color = if (isDarkMode) Color.Cyan else Color.White, shape = CircleShape)
                .border(width = 1.dp, color = if (isDarkMode) Color.Transparent else Color.Gray, shape = CircleShape)
        )
    }
}