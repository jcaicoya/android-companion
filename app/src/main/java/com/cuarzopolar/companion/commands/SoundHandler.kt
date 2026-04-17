package com.cuarzopolar.companion.commands

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class SoundHandler(private val context: Context) {

    private var ringtone: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopSound() }

    fun playSound() {
        Log.d("SoundHandler", "Playing ringtone (alarm stream)")
        stopSound()

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(context, uri)

        // Use ALARM audio attributes so it plays even in silent / DND mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        } else {
            @Suppress("DEPRECATION")
            ringtone?.streamType = android.media.AudioManager.STREAM_ALARM
        }

        ringtone?.play()
        handler.postDelayed(stopRunnable, 4000)
    }

    fun stopSound() {
        handler.removeCallbacks(stopRunnable)
        ringtone?.stop()
        ringtone = null
    }
}
