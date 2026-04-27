package com.example.alarmapp

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var layoutSetup: View
    private lateinit var layoutRinging: View
    private lateinit var tvAlarmStatus: TextView
    private lateinit var btnCancelAlarm: Button

    // Receiver to show the STOP circle when the Service starts
    private val alarmStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            showRingingView()
        }
    }

    private var previewPlayer: android.media.MediaPlayer? = null
    private lateinit var tvFileName: TextView
    private lateinit var btnPlayPreview: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Setup Views
        prefs = getSharedPreferences("AlarmPrefs", Context.MODE_PRIVATE)
        layoutSetup = findViewById(R.id.layoutSetup)
        layoutRinging = findViewById(R.id.layoutRinging)
        tvAlarmStatus = findViewById(R.id.tvAlarmStatus)
        btnCancelAlarm = findViewById(R.id.btnCancelAlarm)

        // New variables for the preview feature
        tvFileName = findViewById(R.id.tvFileName)
        btnPlayPreview = findViewById(R.id.btnPlayPreview)

        val timePicker = findViewById<TimePicker>(R.id.timePicker)
        val btnSetAlarm = findViewById<Button>(R.id.btnSetAlarm)
        val btnCircleStop = findViewById<Button>(R.id.btnCircleStop)
        val btnPickFile = findViewById<FloatingActionButton>(R.id.btnPickFile)

        // 2. Android 13+ Permissions & Broadcast Registration
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        registerReceiver(alarmStatusReceiver, IntentFilter("ALARM_TRIGGERED"), Context.RECEIVER_NOT_EXPORTED)

        // 3. Ringtone Picker (Updated for System Alarms)
        val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                // The Ringtone Picker returns the URI in the data intent
                val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)

                if (uri != null) {
                    prefs.edit().putString("saved_uri", uri.toString()).apply()

                    // Note: System URIs usually don't need takePersistableUriPermission,
                    // but keeping it doesn't hurt if you also pick local files.
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: SecurityException) {
                        // Ignore if the system won't let you "persist" a system-internal URI
                    }

                    tvFileName.text = getFileName(uri)
                    tvFileName.alpha = 0f
                    tvFileName.translationX = 20f
                    Toast.makeText(this, "Alarm Sound Saved", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnPickFile.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                // Show the currently selected one in the list
                val currentUri = prefs.getString("saved_uri", null)
                if (currentUri != null) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
                }
            }
            pickFileLauncher.launch(intent)
        }

        // 4. Set Alarm
        btnSetAlarm.setOnClickListener {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, timePicker.hour)
                set(Calendar.MINUTE, timePicker.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(Calendar.DAY_OF_YEAR, 1)

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, AlarmReceiver::class.java).apply {
                putExtra("RINGTONE_URI", prefs.getString("saved_uri", null))
            }

            val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)

            val timeString = "Alarm set for: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)}"
            tvAlarmStatus.text = timeString
            btnCancelAlarm.visibility = View.VISIBLE

            prefs.edit().putString("alarm_status_text", timeString).apply()
        }

        // 5. Cancel Alarm
        btnCancelAlarm.setOnClickListener {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            alarmManager.cancel(pendingIntent)
            showSetupView()
            Toast.makeText(this, "Alarm Cancelled", Toast.LENGTH_SHORT).show()
            prefs.edit().remove("alarm_status_text").apply()
            tvAlarmStatus.text = "No alarm set"
            btnCancelAlarm.visibility = View.GONE
        }

        // 6. Stop Ringing (Long Press Logic)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val longPressRunnable = Runnable {
            stopService(Intent(this, AlarmService::class.java))
            showSetupView()
            Toast.makeText(this, "Alarm Stopped", Toast.LENGTH_SHORT).show()
            prefs.edit().remove("alarm_status_text").apply()
            tvAlarmStatus.text = "No alarm set"
            btnCancelAlarm.visibility = View.GONE
        }

        @SuppressLint("ClickableViewAccessibility")
        btnCircleStop.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.performClick()
                    handler.postDelayed(longPressRunnable, 3000)
                    v.alpha = 0.5f
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    v.alpha = 1.0f
                }
            }
            true
        }

        // 7. Preview Play/Stop Logic
        btnPlayPreview.setOnClickListener {
            if (previewPlayer?.isPlaying == true) {
                stopPreview()
            } else {
                val currentUri = prefs.getString("saved_uri", null)

                if (currentUri == null) {
                    Toast.makeText(this, "URI is NULL - Select file again", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                try {
                    val uri = Uri.parse(currentUri)

                    // Step A: Create Player
                    previewPlayer = MediaPlayer()

                    // Step B: Set Data Source (Explicitly using applicationContext)
                    previewPlayer?.setDataSource(applicationContext, uri)

                    // Step C: Set Attributes (Matching the Alarm path)
                    previewPlayer?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )

                    // Step D: Prepare and Start
                    previewPlayer?.prepare()
                    previewPlayer?.start()

                    // Step E: UI Feedback
                    tvFileName.animate().alpha(1f).translationX(0f).setDuration(400).start()
                    btnPlayPreview.setImageResource(R.drawable.ic_stop_preview)
                    tvFileName.postDelayed({
                        // Only start if the player is still active (in case they stopped it quickly)
                        if (previewPlayer?.isPlaying == true) {
                            tvFileName.isSelected = true
                        }
                    }, 2000)
                    btnPlayPreview.setImageResource(R.drawable.ic_stop_preview)

                    previewPlayer?.setOnCompletionListener { stopPreview() }

                } catch (e: Exception) {
                    // This will catch permissions, file-not-found, or codec errors
                    Log.e("AlarmApp", "Detailed Error: ", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()

                    previewPlayer?.release()
                    previewPlayer = null
                }
            }
        }
        val savedUriString = prefs.getString("saved_uri", null)
        if (savedUriString != null) {
            val savedUri = Uri.parse(savedUriString)
            tvFileName.text = getFileName(savedUri)
            // Keep it hidden (alpha 0) so the "play" animation still works
            tvFileName.alpha = 0f
            tvFileName.translationX = 20f
        }
        val savedStatus = prefs.getString("alarm_status_text", null)
        if (savedStatus != null) {
            tvAlarmStatus.text = savedStatus
            btnCancelAlarm.visibility = View.VISIBLE
        } else {
            tvAlarmStatus.text = "No alarm set"
            btnCancelAlarm.visibility = View.GONE
        }
    }

    private fun showRingingView() {
        layoutSetup.visibility = View.GONE
        layoutRinging.visibility = View.VISIBLE

        // Randomize position (Bias 0.1 to 0.9 keeps it away from edges)
        val btnCircleStop = findViewById<Button>(R.id.btnCircleStop)
        val params = btnCircleStop.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams

        params.horizontalBias = (10..90).random() / 100f
        params.verticalBias = (10..90).random() / 100f

        btnCircleStop.layoutParams = params
    }

    private fun showSetupView() {
        layoutSetup.visibility = View.VISIBLE
        layoutRinging.visibility = View.GONE
        tvAlarmStatus.text = "No alarm set"
        btnCancelAlarm.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPreview() // Clean up preview if app is closed
        unregisterReceiver(alarmStatusReceiver)
    }

    private fun getFileName(uri: Uri): String {
        var name = "Unknown File"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) name = cursor.getString(nameIndex)
        }
        return name
    }

    private fun stopPreview() {
        tvFileName.isSelected = false
        previewPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.reset()    // Clear the data source
            it.release()  // Release the hardware resources
        }
        previewPlayer = null

        btnPlayPreview.setImageResource(R.drawable.ic_play)
        tvFileName.animate().alpha(0f).translationX(20f).setDuration(400).start()
    }
}