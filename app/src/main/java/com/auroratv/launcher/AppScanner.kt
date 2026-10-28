package com.auroratv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

object AppScanner {
    /** Blocking: run off the main thread. Returns launchable apps sorted by name (without AuroraTV itself). */
    fun scan(ctx: Context, includeAll: Boolean): List<AppEntry> {
        val pm = ctx.packageManager
        val found = LinkedHashMap<String, AppEntry>()

        fun collect(category: String) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            val results = try {
                pm.queryIntentActivities(query, 0)
            } catch (e: Exception) {
                emptyList()
            }
            for (ri in results) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == ctx.packageName) continue
                if (found.containsKey(ai.packageName)) continue
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(ComponentName(ai.packageName, ai.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val label = try { ri.loadLabel(pm).toString() } catch (e: Exception) { ai.packageName }
                val icon: Drawable? = try { ri.loadIcon(pm) } catch (e: Exception) { null }
                val banner: Drawable? = try {
                    ai.loadBanner(pm) ?: ai.applicationInfo?.loadBanner(pm)
                } catch (e: Exception) {
                    null
                }
                found[ai.packageName] = AppEntry(label, icon, banner, launch, false, ai.packageName)
            }
        }

        collect(Intent.CATEGORY_LEANBACK_LAUNCHER)
        if (includeAll) collect(Intent.CATEGORY_LAUNCHER)
        return found.values.sortedBy { it.label.lowercase() }
    }
}
