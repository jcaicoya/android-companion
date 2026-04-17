package com.cuarzopolar.companion.commands

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.util.Log

class SoundHandler(private val context: Context) {

    private var ringtone: Ringtone? = null

    fun playSound() {
        Log.d("SoundHandler", "Playing ringtone")
        stopSound()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(context, uri)
        ringtone?.play()

        // Stop after 4 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            stopSound()
        }, 4000)
    }

    fun stopSound() {
        ringtone?.stop()
        ringtone = null
    }
}
