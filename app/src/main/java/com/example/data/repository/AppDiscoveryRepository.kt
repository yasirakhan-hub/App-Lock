package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Discovers installed launchable apps and converts their metadata and icons
 * for display in Compose UI.
 */
class AppDiscoveryRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val iconCache = mutableMapOf<String, ImageBitmap?>()

    /**
     * Loads list of launchable apps.
     */
    suspend fun getInstalledLaunchableApps(protectedPackages: Set<String>): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfoList = try {
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            } catch (e: Exception) {
                packageManager.queryIntentActivities(intent, 0)
            }

            // Also find current home launcher package
            val launcherPackage = try {
                val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val homeResolve = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                homeResolve?.activityInfo?.packageName
            } catch (_: Exception) {
                null
            }

            val seenPackages = mutableSetOf<String>()
            val apps = mutableListOf<AppInfo>()

            for (resolveInfo in resolveInfoList) {
                val pkgName = resolveInfo.activityInfo.packageName

                // Skip our own app
                if (pkgName == context.packageName) continue

                // Skip duplicates
                if (seenPackages.contains(pkgName)) continue
                seenPackages.add(pkgName)

                val label = try {
                    resolveInfo.loadLabel(packageManager).toString()
                } catch (e: Exception) {
                    pkgName
                }

                val appInfo = resolveInfo.activityInfo.applicationInfo
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                val isCritical = pkgName == launcherPackage ||
                        pkgName == "com.android.settings" ||
                        pkgName.contains("dialer", ignoreCase = true)

                val iconBitmap = getAppIcon(pkgName, resolveInfo.loadIcon(packageManager))

                apps.add(
                    AppInfo(
                        packageName = pkgName,
                        name = label,
                        isSystemApp = isSystem,
                        isProtected = protectedPackages.contains(pkgName),
                        isCriticalSystemComponent = isCritical,
                        iconBitmap = iconBitmap
                    )
                )
            }

            // Sort: protected apps first, then non-system, then alphabetical
            apps.sortedWith(
                compareByDescending<AppInfo> { it.isProtected }
                    .thenBy { it.isSystemApp }
                    .thenBy { it.name.lowercase() }
            )
        }

    /**
     * Retrieves or converts app icon to Compose ImageBitmap.
     */
    fun getAppIcon(packageName: String, drawable: Drawable?): ImageBitmap? {
        if (iconCache.containsKey(packageName)) {
            return iconCache[packageName]
        }
        val bitmap = drawableToBitmap(drawable)
        val imageBitmap = bitmap?.asImageBitmap()
        iconCache[packageName] = imageBitmap
        return imageBitmap
    }

    /**
     * Finds App label by package name.
     */
    fun getAppName(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    /**
     * Gets app icon for a package directly.
     */
    fun getAppIcon(packageName: String): ImageBitmap? {
        if (iconCache.containsKey(packageName)) {
            return iconCache[packageName]
        }
        return try {
            val drawable = packageManager.getApplicationIcon(packageName)
            val bmp = drawableToBitmap(drawable)
            val imgBmp = bmp?.asImageBitmap()
            iconCache[packageName] = imgBmp
            imgBmp
        } catch (e: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
