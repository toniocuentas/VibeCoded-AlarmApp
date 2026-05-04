package com.example.alarmapp

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class AlarmService : Service() {
    companion object {
        var isRunning = false
    }

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intentTriggered = Intent("ALARM_TRIGGERED")
        intentTriggered.setPackage(packageName)
        sendBroadcast(intentTriggered)
        val channelId = "ALARM_SERVICE_CHANNEL"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, "Alarm", NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("FROM_NOTIFICATION", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Alarm")
            .setContentText("Ringing...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
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
        isRunning = false
        mediaPlayer?.stop()
        mediaPlayer?.release()

        val prefs = getSharedPreferences("AlarmPrefs", MODE_PRIVATE)
        val isRepeating = prefs.getBoolean("repeat_daily", false)

        if (isRepeating) {
            val hour = prefs.getInt("alarm_hour", -1)
            val minute = prefs.getInt("alarm_minute", -1)

            if (hour != -1 && minute != -1) {
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, 1) // Always schedule for tomorrow
                }

                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(this, AlarmReceiver::class.java).apply {
                    putExtra("RINGTONE_URI", prefs.getString("saved_uri", null))
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )

                val timeString = "Alarm set for: ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(calendar.time)}"
                prefs.edit().putString("alarm_status_text", timeString).apply()
            }
        } else {
            // If NOT repeating, clear the status text
            prefs.edit().remove("alarm_status_text").apply()
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}