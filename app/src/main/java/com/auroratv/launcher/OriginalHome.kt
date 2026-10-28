package com.auroratv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Opens the TV's original (stock) Home screen, whatever launcher the manufacturer installed. */
object OriginalHome {
    fun launch(ctx: Context) {
        val base = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val list = try {
            ctx.packageManager.queryIntentActivities(base, 0)
        } catch (e: Exception) {
            emptyList()
        }
        for (ri in list) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == ctx.packageName) continue
            val i = Intent(base)
                .setComponent(ComponentName(ai.packageName, ai.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(i)
                return
            } catch (e: Exception) {
                // try the next candidate
            }
        }
        Toast.makeText(ctx, "Couldn't find the original TV home screen", Toast.LENGTH_LONG).show()
    }
}
