package com.auroratv.launcher.assistant

import android.app.Activity

/**
 * Extension point for a future voice/AI assistant.
 *
 * The first build ships only [NoAssistant]: no microphone, no network, no cloud, no cost.
 * Later, a real implementation can be assigned to [Assistants.provider] (for example from a
 * separate module or product flavor) without touching the launcher UI code.
 */
interface AssistantProvider {
    /** True when this provider can actually handle voice requests. */
    val isAvailable: Boolean

    /** Called when the remote's voice/search key is pressed. Return true if the key was handled. */
    fun onVoiceKey(activity: Activity): Boolean
}

object NoAssistant : AssistantProvider {
    override val isAvailable: Boolean = false
    override fun onVoiceKey(activity: Activity): Boolean = false
}

object Assistants {
    @Volatile
    var provider: AssistantProvider = NoAssistant
}
