package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.repository.AppLockManager
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    appLockManager: AppLockManager,
    onInitComplete: (isPinConfigured: Boolean) -> Unit
) {
    val prefs = appLockManager.preferences

    LaunchedEffect(Unit) {
        // Perform initial setup loading
        val pinConfigured = prefs.isPinConfigured()
        // Ensure smooth splash screen presentation while setup completes
        delay(1500L)
        onInitComplete(pinConfigured)
    }

    val rainbowBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFF2A85), // Pink/Red
                Color(0xFFFF7A00), // Orange
                Color(0xFFFFE600), // Yellow
                Color(0xFF10B981), // Green
                Color(0xFF00E5FF), // Cyan/Electric Blue
                Color(0xFFB800FF)  // Purple
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Prominent Shield & Padlock Logo
            Image(
                painter = painterResource(id = R.drawable.ic_applock_shield_logo),
                contentDescription = "AppLock Security Logo",
                modifier = Modifier.size(140.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Main App Title
            Text(
                text = "AppLock",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle / Tagline
            Text(
                text = "SECURE YOUR APPS",
                color = Color(0xFF00E5FF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )
        }

        // Developer Branding at the Bottom
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            Text(
                text = "Developed by",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Yasir A Khan",
                style = TextStyle(
                    brush = rainbowBrush,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}
