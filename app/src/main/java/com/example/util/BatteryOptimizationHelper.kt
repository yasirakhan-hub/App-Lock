package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Provides battery saver, Doze mode detection, and OEM-specific background
 * reliability guidance to prevent aggressive task killers from terminating
 * app-lock monitoring at low battery (~5%).
 */
object BatteryOptimizationHelper {

    enum class OemBrand {
        SAMSUNG,
        XIAOMI,
        ONEPLUS,
        OPPO,
        VIVO,
        REALME,
        MOTOROLA,
        PIXEL_OR_AOSP,
        OTHER
    }

    data class OemGuidance(
        val brand: OemBrand,
        val brandName: String,
        val instructions: String,
        val requiresManualAction: Boolean
    )

    /**
     * Identifies the device manufacturer.
     */
    fun getDeviceBrand(): OemBrand {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        return when {
            manufacturer.contains("samsung") || brand.contains("samsung") -> OemBrand.SAMSUNG
            manufacturer.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> OemBrand.XIAOMI
            manufacturer.contains("oneplus") -> OemBrand.ONEPLUS
            manufacturer.contains("oppo") -> OemBrand.OPPO
            manufacturer.contains("vivo") || brand.contains("iqoo") -> OemBrand.VIVO
            manufacturer.contains("realme") -> OemBrand.REALME
            manufacturer.contains("motorola") || brand.contains("moto") -> OemBrand.MOTOROLA
            manufacturer.contains("google") -> OemBrand.PIXEL_OR_AOSP
            else -> OemBrand.OTHER
        }
    }

    /**
     * Checks if the device is currently in Battery Saver / Power Save mode.
     */
    fun isBatterySaverActive(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isPowerSaveMode
    }

    /**
     * Checks if the app is currently whitelisted from system battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Returns clear, manufacturer-specific background reliability guidance.
     */
    fun getManufacturerGuidance(context: Context): OemGuidance {
        val isIgnored = isIgnoringBatteryOptimizations(context)
        return when (getDeviceBrand()) {
            OemBrand.SAMSUNG -> OemGuidance(
                brand = OemBrand.SAMSUNG,
                brandName = "Samsung (One UI)",
                instructions = "Go to Settings → Battery → Background usage limits → Never sleeping apps, and add Smart AppLock. Turn off 'Put unused apps to sleep' to prevent protection stopping at low battery.",
                requiresManualAction = !isIgnored
            )
            OemBrand.XIAOMI -> OemGuidance(
                brand = OemBrand.XIAOMI,
                brandName = "Xiaomi / Redmi / POCO (HyperOS / MIUI)",
                instructions = "Go to Settings → Apps → Manage apps → Smart AppLock. Enable 'Autostart' and set Battery saver to 'No restrictions' to keep app protection active under 10% battery.",
                requiresManualAction = !isIgnored
            )
            OemBrand.ONEPLUS -> OemGuidance(
                brand = OemBrand.ONEPLUS,
                brandName = "OnePlus (OxygenOS)",
                instructions = "Go to Settings → Battery → Advanced settings → App battery management → Smart AppLock. Enable 'Allow background activity' and 'Allow auto-launch'.",
                requiresManualAction = !isIgnored
            )
            OemBrand.OPPO, OemBrand.REALME -> OemGuidance(
                brand = OemBrand.OPPO,
                brandName = "OPPO / Realme (ColorOS / Realme UI)",
                instructions = "Go to Settings → Battery → App battery management → Smart AppLock. Turn ON 'Allow background activity' and 'Allow auto-startup'.",
                requiresManualAction = !isIgnored
            )
            OemBrand.VIVO -> OemGuidance(
                brand = OemBrand.VIVO,
                brandName = "Vivo (Funtouch OS)",
                instructions = "Go to Settings → Battery → Background power consumption management → Smart AppLock, and select 'High background power consumption'. Enable Autostart in Permission management.",
                requiresManualAction = !isIgnored
            )
            OemBrand.MOTOROLA -> OemGuidance(
                brand = OemBrand.MOTOROLA,
                brandName = "Motorola (My UX)",
                instructions = "Go to Settings → Battery → Battery Saver. In App battery usage for Smart AppLock, select 'Unrestricted'.",
                requiresManualAction = !isIgnored
            )
            OemBrand.PIXEL_OR_AOSP, OemBrand.OTHER -> OemGuidance(
                brand = OemBrand.PIXEL_OR_AOSP,
                brandName = "Android / Pixel",
                instructions = "Go to Settings → Apps → Smart AppLock → App battery usage, and choose 'Unrestricted' so background monitoring is not paused by Battery Saver.",
                requiresManualAction = !isIgnored
            )
        }
    }

    /**
     * Safely opens standard system battery optimization settings.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    /**
     * Opens system App Info page where user can access battery optimization options.
     */
    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    /**
     * Tries known OEM autostart / battery manager intents safely without crashing on unsupported platforms.
     */
    fun openOemAutostartSettings(context: Context): Boolean {
        val oemIntents = listOf(
            // Xiaomi MIUI / HyperOS
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            // OnePlus OxygenOS
            Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
            // Oppo ColorOS
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            // Vivo FuntouchOS
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            // Huawei EMUI
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            // Samsung Device Care
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"))
        )

        for (intent in oemIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Fallback to next known intent
            }
        }
        // Fallback to standard battery optimization settings
        openBatteryOptimizationSettings(context)
        return false
    }
}
