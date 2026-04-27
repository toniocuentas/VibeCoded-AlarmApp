package com.example.alarmapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sendBroadcast(Intent("ALARM_TRIGGERED"))
        val channelId = "ALARM_SERVICE_CHANNEL"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, "Alarm", NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Alarm")
            .setContentText("Ringing...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        val uriString = intent?.getStringExtra("RINGTONE_URI")
        val ringtoneUri = if (uriString != null) Uri.parse(uriString)
        else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}