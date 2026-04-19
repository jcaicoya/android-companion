package com.cuarzopolar.companion.commands

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.cuarzopolar.companion.RedScreenActivity

private const val RED_CHANNEL_ID = "red_screen_channel"
private const val RED_NOTIF_ID   = 99

class RedScreenHandler(private val context: Context) {

    fun showRedScreen() {
        Log.d("RedScreenHandler", "Launching red screen")
        setRedScreenActive(true)
        val intent = Intent(context, RedScreenActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )

        // On Android 10+ also post a full-screen-intent notification so the activity
        // launches even when the app is in the background.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ensureRedChannel()
            val pi = PendingIntent.getActivity(
                context, RED_NOTIF_ID, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notif = Notification.Builder(context, RED_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("CuarzoPolar")
                .setContentText("Pantalla de ataque activada")
                .setFullScreenIntent(pi, true)
                .setOngoing(true)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(RED_NOTIF_ID, notif)
        }

        context.startActivity(intent)
    }

    fun hideRedScreen() {
        Log.d("RedScreenHandler", "Hiding red screen")
        setRedScreenActive(false)
        // Cancel the full-screen notification if present
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(RED_NOTIF_ID)
        RedScreenActivity.dismiss()
    }

    private fun setRedScreenActive(active: Boolean) {
        context.getSharedPreferences(RedScreenActivity.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(RedScreenActivity.KEY_ACTIVE, active)
            .apply()
    }

    private fun ensureRedChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(RED_CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                RED_CHANNEL_ID, "Red Screen",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { setSound(null, null) }
            nm.createNotificationChannel(ch)
        }
    }
}
