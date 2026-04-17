package com.cuarzopolar.companion.commands

import android.util.Log

class CommandHandler {
    fun handle(action: String, targetId: String) {
        // Phase 2: implement vibrate, playSound, showRedScreen
        Log.d("CommandHandler", "command=$action targetId=$targetId")
    }
}
