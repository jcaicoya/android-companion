package com.cuarzopolar.companion.commands

import android.content.Context
import android.util.Log

class CommandHandler(context: Context) {

    private val vibrateHandler   = VibrateHandler(context)
    private val soundHandler     = SoundHandler(context)
    private val redScreenHandler = RedScreenHandler(context)

    private var onStartMic: (() -> Unit)? = null
    private var onStopMic:  (() -> Unit)? = null
    private var onTakePhoto: (() -> Unit)? = null

    fun setMicCallbacks(start: () -> Unit, stop: () -> Unit) {
        onStartMic = start
        onStopMic  = stop
    }

    fun setPhotoCallback(cb: () -> Unit) { onTakePhoto = cb }

    fun handle(action: String, targetId: String) {
        Log.d("CommandHandler", "command=$action targetId=$targetId")
        when (action) {
            "vibrate"       -> vibrateHandler.vibrate()
            "playSound"     -> soundHandler.playSound()
            "showRedScreen" -> redScreenHandler.showRedScreen()
            "hideRedScreen" -> redScreenHandler.hideRedScreen()
            "startMic"      -> onStartMic?.invoke()
            "stopMic"       -> onStopMic?.invoke()
            "takePhoto"     -> onTakePhoto?.invoke()
            else            -> Log.w("CommandHandler", "Unknown action: $action")
        }
    }
}
