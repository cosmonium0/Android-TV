package com.example.tvhome.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val banner: ImageBitmap?,
    val accent: Color,
    val launchIntent: Intent,
    val pinned: Boolean,
    val hidden: Boolean,
)

/**
 * Finds launchable apps and stores the user's layout (order, pinned, hidden).
 * Layout lives in SharedPreferences so it survives reboots.
 */
class AppRepository(private val context: Context) {

    private val pm = context.packageManager
    private val prefs = context.getSharedPreferences("layout", Context.MODE_PRIVATE)

    private val leanbackCategory = "android.intent.category.LEANBACK_LAUNCHER"

    fun load(): List<AppInfo> {
        val found = LinkedHashMap<String, ResolveInfo>()
        // Leanback (TV) entries first, then fall back to phone-style launcher entries
        // so apps without a TV activity still appear.
        for (category in listOf(leanbackCategory, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            @Suppress("DEPRECATION")
            for (ri in pm.queryIntentActivities(query, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName) continue
                if (!found.containsKey(pkg)) found[pkg] = ri
            }
        }

        val isFirstRun = !prefs.contains("pinned")
        val saved = readList("order")
        val pinnedSet = prefs.getStringSet("pinned", emptySet()) ?: emptySet()
        val hiddenSet = prefs.getStringSet("hidden", emptySet()) ?: emptySet()

        val byPackage = found.keys.toList()
        val ordered = buildList {
            addAll(saved.filter { it in found })
            addAll(
                byPackage.filter { it !in saved }
                    .sortedBy { found.getValue(it).loadLabel(pm).toString().lowercase() }
            )
        }
        if (isFirstRun) {
            prefs.edit().putStringSet("pinned", ordered.take(6).toSet()).apply()
        }
        val pinned = if (isFirstRun) ordered.take(6).toSet() else pinnedSet

        return ordered.map { pkg ->
            val ri = found.getValue(pkg)
            val iconBmp = ri.loadIcon(pm).toBitmap(192, 192, Bitmap.Config.ARGB_8888)
            val bannerDrawable = ri.activityInfo.loadBanner(pm)
                ?: ri.activityInfo.applicationInfo.loadBanner(pm)
            val bannerBmp = bannerDrawable?.toBitmap(480, 270, Bitmap.Config.ARGB_8888)
            val category = if (bannerDrawable != null || isLeanback(ri)) leanbackCategory
            else Intent.CATEGORY_LAUNCHER
            AppInfo(
                packageName = pkg,
                label = ri.loadLabel(pm).toString(),
                icon = iconBmp.asImageBitmap(),
                banner = bannerBmp?.asImageBitmap(),
                accent = averageColor(bannerBmp ?: iconBmp),
                launchIntent = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(ComponentName(pkg, ri.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                pinned = pkg in pinned,
                hidden = pkg in hiddenSet,
            )
        }
    }

    private fun isLeanback(ri: ResolveInfo): Boolean {
        val query = Intent(Intent.ACTION_MAIN).addCategory(leanbackCategory).setPackage(ri.activityInfo.packageName)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(query, 0).isNotEmpty()
    }

    fun togglePin(pkg: String) {
        val set = (prefs.getStringSet("pinned", emptySet()) ?: emptySet()).toMutableSet()
        if (!set.remove(pkg)) set.add(pkg)
        prefs.edit().putStringSet("pinned", set).apply()
    }

    fun toggleHidden(pkg: String) {
        val set = (prefs.getStringSet("hidden", emptySet()) ?: emptySet()).toMutableSet()
        if (!set.remove(pkg)) set.add(pkg)
        prefs.edit().putStringSet("hidden", set).apply()
    }

    /**
     * Moves [pkg] one slot within the row it is shown in.
     * [rowPackages] is the visible row, in order; we swap with the neighbour in the global order.
     */
    fun move(pkg: String, delta: Int, rowPackages: List<String>, allOrdered: List<String>) {
        val idx = rowPackages.indexOf(pkg)
        val target = rowPackages.getOrNull(idx + delta) ?: return
        val order = allOrdered.toMutableList()
        val a = order.indexOf(pkg)
        val b = order.indexOf(target)
        if (a < 0 || b < 0) return
        order[a] = target
        order[b] = pkg
        prefs.edit().putString("order", order.joinToString("\n")).apply()
    }

    private fun readList(key: String): List<String> =
        prefs.getString(key, null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun averageColor(bmp: Bitmap): Color {
        val px = Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0)
        return Color(px).copy(alpha = 1f)
    }
}
