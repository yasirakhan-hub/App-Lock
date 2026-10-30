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

import java.util.concurrent.ConcurrentHashMap

/**
 * Discovers installed launchable apps and converts their metadata and icons
 * for display in Compose UI.
 */
class AppDiscoveryRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    companion object {
        // Process-wide thread-safe cache for app icons to eliminate redundant Binder IPC and bitmap decodes
        // across screens (Dashboard, AppSelection, LockActivity).
        private val sharedIconCache = ConcurrentHashMap<String, ImageBitmap>()

        fun clearSharedIconCache() {
            sharedIconCache.clear()
        }
    }

    /**
     * Fast query to count distinct launchable apps without loading labels, icons, or bitmaps.
     * Prevents loading 50+ Bitmaps into memory on the home Dashboard.
     */
    suspend fun getInstalledLaunchableAppsCount(): Int = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = try {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        } catch (_: Exception) {
            try {
                packageManager.queryIntentActivities(intent, 0)
            } catch (_: Exception) {
                emptyList()
            }
        }
        val selfPkg = context.packageName
        val seen = HashSet<String>(resolveInfoList.size)
        for (info in resolveInfoList) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg != selfPkg) {
                seen.add(pkg)
            }
        }
        seen.size
    }

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

                // Check sharedIconCache first to avoid redundant PackageManager Binder IPC and drawable decoding
                val cached = sharedIconCache[pkgName]
                val iconBitmap = if (cached != null) {
                    cached
                } else {
                    getAppIcon(pkgName, resolveInfo.loadIcon(packageManager))
                }

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
        val cached = sharedIconCache[packageName]
        if (cached != null) {
            return cached
        }
        val bitmap = drawableToBitmap(drawable) ?: return null
        val imageBitmap = bitmap.asImageBitmap()
        sharedIconCache[packageName] = imageBitmap
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
        val cached = sharedIconCache[packageName]
        if (cached != null) {
            return cached
        }
        return try {
            val drawable = packageManager.getApplicationIcon(packageName)
            val bmp = drawableToBitmap(drawable) ?: return null
            val imgBmp = bmp.asImageBitmap()
            sharedIconCache[packageName] = imgBmp
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
