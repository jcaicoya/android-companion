package com.cuarzopolar.companion

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class RedScreenActivity : AppCompatActivity() {

    private val relaunchHandler = Handler(Looper.getMainLooper())
    private var allowFinish = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        markActive(true)
        configureWindow()

        setContentView(R.layout.activity_red_screen)

        // Register this instance so RedScreenHandler can dismiss it remotely (T11, T14)
        current = this
        tryStartLockTaskIfDeviceOwner()
    }

    override fun onResume() {
        super.onResume()
        configureWindow()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) configureWindow()
    }

    override fun onBackPressed() {
        // Red screen is controlled remotely through hideRedScreen.
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        scheduleRelaunch()
    }

    override fun onStop() {
        super.onStop()
        if (!allowFinish && isActive()) scheduleRelaunch()
    }

    override fun finish() {
        if (allowFinish) {
            super.finish()
        } else {
            configureWindow()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        relaunchHandler.removeCallbacksAndMessages(null)
        if (current == this) current = null
    }

    private fun configureWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    private fun scheduleRelaunch() {
        relaunchHandler.removeCallbacksAndMessages(null)
        relaunchHandler.postDelayed({
            if (isActive()) {
                val intent = Intent(this, RedScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(intent)
            }
        }, 250)
    }

    private fun tryStartLockTaskIfDeviceOwner() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(packageName)) return

        val admin = ComponentName(this, CompanionDeviceAdminReceiver::class.java)
        runCatching {
            dpm.setLockTaskPackages(admin, arrayOf(packageName))
            startLockTask()
        }
    }

    private fun isActive(): Boolean =
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    private fun markActive(active: Boolean) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, active)
            .apply()
    }

    companion object {
        const val PREFS = "companion_prefs"
        const val KEY_ACTIVE = "red_screen_active"

        private var current: RedScreenActivity? = null

        /** Called by RedScreenHandler when a hideRedScreen command arrives. */
        fun dismiss() {
            current?.run {
                markActive(false)
                allowFinish = true
                runCatching { stopLockTask() }
                finish()
            }
        }
    }
}
