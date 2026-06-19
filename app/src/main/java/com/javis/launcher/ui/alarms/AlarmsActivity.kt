package com.javis.launcher.ui.alarms

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.javis.launcher.R
import java.util.Calendar

class AlarmsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarms)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val tvInfo = findViewById<TextView>(R.id.tv_alarm_info)
        tvInfo.text = "Alarms are managed by your device's clock app.\nJAVIS can create new alarms for you."

        val fab = findViewById<FloatingActionButton>(R.id.fab_add_alarm)
        fab.setOnClickListener { showTimePicker() }
    }

    private fun showTimePicker() {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            setAlarm(hour, minute, "JAVIS Alarm")
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
    }

    private fun setAlarm(hour: Int, minute: Int, label: String) {
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            val amPm = if (hour < 12) "AM" else "PM"
            val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            Toast.makeText(this, "Alarm set for $h:${minute.toString().padStart(2,'0')} $amPm", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not set alarm: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
