package com.example.robotdogrelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RelayForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "robot_relay_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_PI_IP = "EXTRA_PI_IP"
    }

    private var relayServer: RobotRelayServer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val piIp = intent.getStringExtra(EXTRA_PI_IP) ?: "192.168.43.100"
                startForeground(NOTIFICATION_ID, createNotification(piIp))
                relayServer?.stop()
                relayServer = RobotRelayServer(applicationContext, piIp).apply {
                    start(localPort = 8080)
                }
            }
            ACTION_STOP -> {
                relayServer?.stop()
                relayServer = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotification(piIp: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Robot Dog Relay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Robot Dog 中繼服務運行中")
            .setContentText("目標 Pi: $piIp | 本地埠號: 8080")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        relayServer?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
