package com.example.data.model

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Representation of an installed launchable application.
 */
data class AppInfo(
    val packageName: String,
    val name: String,
    val isSystemApp: Boolean,
    val isProtected: Boolean,
    val isCriticalSystemComponent: Boolean = false,
    val iconBitmap: ImageBitmap? = null
)
