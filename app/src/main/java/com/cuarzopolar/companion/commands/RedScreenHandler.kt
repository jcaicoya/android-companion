package com.cuarzopolar.companion.commands

import android.content.Context
import android.content.Intent
import android.util.Log
import com.cuarzopolar.companion.RedScreenActivity

class RedScreenHandler(private val context: Context) {

    fun showRedScreen() {
        Log.d("RedScreenHandler", "Launching red screen")
        val intent = Intent(context, RedScreenActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
