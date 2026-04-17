package com.cuarzopolar.companion.commands

import android.content.Context
import android.util.Log

class CommandHandler(context: Context) {

    private val vibrateHandler   = VibrateHandler(context)
    private val soundHandler     = SoundHandler(context)
    private val redScreenHandler = RedScreenHandler(context)

    fun handle(action: String, targetId: String) {
        Log.d("CommandHandler", "command=$action targetId=$targetId")
        when (action) {
            "vibrate"       -> vibrateHandler.vibrate()
            "playSound"     -> soundHandler.playSound()
            "showRedScreen" -> redScreenHandler.showRedScreen()
            else            -> Log.w("CommandHandler", "Unknown action: $action")
        }
    }

    // Direct triggers for rehearsal test buttons
    fun testVibrate()   = vibrateHandler.vibrate()
    fun testSound()     = soundHandler.playSound()
    fun testRedScreen() = redScreenHandler.showRedScreen()
}
