package com.example.alarmapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ringtoneUri = intent.getStringExtra("RINGTONE_URI")
        val serviceIntent = Intent(context, AlarmService::class.java)
        serviceIntent.putExtra("RINGTONE_URI", ringtoneUri)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}