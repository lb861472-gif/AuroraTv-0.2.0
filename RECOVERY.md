# Getting back to the TCL launcher

The TCL/Google TV launcher is never removed, disabled or modified.

**From AuroraTV:** Settings (chip top right) > Recovery > "Go to the original TV home screen" (one visit) or
"Stop AuroraTV being a Home choice" (permanent until you switch it back on in Navigation & Home app).

**If AuroraTV crashes or the screen is stuck:**
1. Press Home again; wait a few seconds. Android usually falls back to the TCL launcher after repeated crashes.
2. Settings (the TV's own quick-settings panel on the Settings button) > All settings > Apps > AuroraTV > Uninstall.
3. From a PC: `adb connect TV_IP:5555` then `adb uninstall com.auroratv.launcher`.

**If you used the adb `set-home-activity` step:** uninstalling AuroraTV returns Home to the TCL launcher.
