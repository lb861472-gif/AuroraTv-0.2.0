package com.auroratv.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * One-time "make AuroraTV my Home app" helper.
 *
 * Why pressing Home never showed a chooser: since Android 10 the default Home app is a "role", and
 * installing another launcher does NOT trigger a chooser - the current holder (the TCL/Google TV launcher)
 * keeps the role until the user changes it. So we ask Android for the role explicitly.
 */
object HomeRole {
    const val REQ = 4242

    fun isHome(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val rm = ctx.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
            } catch (e: Exception) {
                // fall through to the resolver check
            }
        }
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == ctx.packageName
    }

    /** True if a system screen was opened. False if this TV offers no UI for it. */
    fun request(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val rm = activity.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ)
                    return true
                }
            } catch (e: Exception) {
                // try the settings screen next
            }
        }
        try {
            activity.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            return true
        } catch (e: Exception) {
            return false
        }
    }

    const val ADB_HELP =
        "This TV did not open a screen for choosing the Home app.\n\n" +
            "Optional one-time step from your PC (turn on Network debugging on the TV first):\n" +
            "adb connect TV_IP:5555\n" +
            "adb shell cmd package set-home-activity com.auroratv.launcher/.HomeAlias\n\n" +
            "This is untested on your TV. It does not remove the TCL launcher. To undo it, use " +
            "Settings > Recovery in AuroraTV, or run: adb uninstall com.auroratv.launcher"

    fun requestOrExplain(activity: Activity) {
        if (!request(activity)) {
            GlassDialog.show(activity, "Choose Home app manually", HomeRole.ADB_HELP, "OK", null) { _ -> }
        }
    }
}
