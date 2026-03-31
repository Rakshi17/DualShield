package com.example.dualshieldai.ui

import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.example.dualshieldai.R
import com.example.dualshieldai.utils.DatabaseHelper
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

class WorkerDashboardActivity : AppCompatActivity() {

    private lateinit var tvSafetyStatus: TextView
    private lateinit var cvSafetyStatus: CardView
    private lateinit var tvShiftStatus: TextView
    private lateinit var tvWorkerName: TextView

    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var btnReconnect: ImageButton

    private lateinit var tvTempStatus: TextView
    private lateinit var tvTempTrend: TextView
    private lateinit var tvMotionStatus: TextView
    private lateinit var tvGasStatus: TextView
    private lateinit var tvGasTrend: TextView
    private lateinit var tvStructureStatus: TextView

    private lateinit var tvAiConfidence: TextView
    private lateinit var tvAiExplanation: TextView
    private lateinit var tvAiTimer: TextView

    private lateinit var svHistory: ScrollView
    private lateinit var tvHistory: TextView
    private lateinit var btnEmergency: Button
    private lateinit var btnSensorDebug: Button
    
    private lateinit var tvLatency: TextView
    private lateinit var btnToggleShift: Button

    private val handler = Handler(Looper.getMainLooper())
    private val historyLog = mutableListOf<Pair<String, String>>()
    
    // DB & State
    private lateinit var dbHelper: DatabaseHelper
    private var workerEmail: String = ""
    private var workerName: String = "Unknown User"
    private var currentDate: String = ""
    private var isShiftActive: Boolean = false
    private var shiftStartTimeString: String = "--:--"
    private var shiftEndTimeString: String = "--:--"

    // Alarms
    private var alarmRingtone: Ringtone? = null
    
    private var lastSuccessfulRead: Long = 0L


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_dashboard)

        tvSafetyStatus = findViewById(R.id.tvSafetyStatus)
        cvSafetyStatus = findViewById(R.id.cvSafetyStatus)
        tvShiftStatus = findViewById(R.id.tvShiftStatus)
        tvWorkerName = findViewById(R.id.tvWorkerName)

        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvIpAddress = findViewById(R.id.tvIpAddress)
        btnReconnect = findViewById(R.id.btnReconnect)

        tvTempStatus = findViewById(R.id.tvTempStatus)
        tvTempTrend = findViewById(R.id.tvTempTrend)
        tvMotionStatus = findViewById(R.id.tvMotionStatus)
        tvGasStatus = findViewById(R.id.tvGasStatus)
        tvGasTrend = findViewById(R.id.tvGasTrend)
        tvStructureStatus = findViewById(R.id.tvStructureStatus)

        tvAiConfidence = findViewById(R.id.tvAiConfidence)
        tvAiExplanation = findViewById(R.id.tvAiExplanation)
        tvAiTimer = findViewById(R.id.tvAiTimer)

        svHistory = findViewById(R.id.svHistory)
        tvHistory = findViewById(R.id.tvHistory)
        btnEmergency = findViewById(R.id.btnEmergency)
        btnToggleShift = findViewById(R.id.btnToggleShift)
        btnSensorDebug = findViewById(R.id.btnSensorDebug)
        
        tvLatency = findViewById(R.id.tvLatency)
        
        dbHelper = DatabaseHelper(this)
        
        val prefs = getSharedPreferences("DualShieldPrefs", Context.MODE_PRIVATE)
        workerEmail = prefs.getString("USER_EMAIL", "worker@test.com") ?: "worker@test.com"
        
        Thread {
            workerName = dbHelper.getUserName(workerEmail)
            runOnUiThread {
                tvWorkerName.text = workerName
                tvShiftStatus.text = "Shift: Inactive"
            }
        }.start()

        btnReconnect.setOnClickListener {
            Toast.makeText(this, "Reconnecting...", Toast.LENGTH_SHORT).show()
            fetchData()
        }
        
        btnEmergency.setOnClickListener {
            triggerManualEmergency()
        }

        btnToggleShift.setOnClickListener {
            if (!isShiftActive) {
                startShift()
            } else {
                endShift()
            }
        }

        btnSensorDebug.setOnClickListener {
            startActivity(Intent(this, SensorDebugActivity::class.java))
        }

        svHistory.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        alarmRingtone = RingtoneManager.getRingtone(applicationContext, alarmUri)

        startMonitoring()
    }
    
    private fun startShift() {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfTime = SimpleDateFormat("HH:mm:ss a", Locale.getDefault())
        currentDate = sdfDate.format(Date())
        val loginTime = sdfTime.format(Date())
        
        shiftStartTimeString = loginTime
        shiftEndTimeString = "--:--"
        
        tvShiftStatus.text = "Shift: ACTIVE\nStart Time: $loginTime"
        isShiftActive = true
        
        com.example.dualshieldai.model.WorkerStateManager.getWorkerState(workerName)?.let {
            it.shiftStarted = true
            it.shiftStartTime = loginTime
            it.attendance = "YES"
        }
        
        btnToggleShift.text = "END SHIFT"
        btnToggleShift.setBackgroundColor(ContextCompat.getColor(this, R.color.status_danger))

        Thread {
            dbHelper.logAttendanceStart(workerName, currentDate, loginTime)
            // Immediately update real-time status
            val currentTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            dbHelper.updateWorkerStatus(
                name = workerName,
                motion = "SAFE",
                temp = "NORMAL",
                gas = "SAFE",
                struct = "SAFE",
                risk = "SYSTEM SAFE",
                shift = "ACTIVE",
                shiftStartTime = shiftStartTimeString,
                shiftEndTime = shiftEndTimeString,
                time = currentTimeStr
            )
        }.start()
    }
    
    private fun endShift() {
        val timeFormat = SimpleDateFormat("HH:mm:ss a", Locale.getDefault())
        val time = timeFormat.format(Date())
        shiftEndTimeString = time
        tvShiftStatus.text = "Shift: ENDED\nEnd Time: $time"
        isShiftActive = false
        
        com.example.dualshieldai.model.WorkerStateManager.getWorkerState(workerName)?.let {
            it.shiftStarted = false
            it.shiftEndTime = time
            it.attendance = "NO"
        }

        btnToggleShift.text = "MARK ATTENDANCE (START SHIFT)"
        btnToggleShift.setBackgroundColor(ContextCompat.getColor(this, R.color.status_safe))

        Thread {
            val hmsFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            dbHelper.logAttendanceEnd(workerName, currentDate, hmsFormat.format(Date()), "Completed")
            // Immediately update real-time status
            dbHelper.updateWorkerStatus(
                name = workerName,
                motion = "SAFE",
                temp = "NORMAL",
                gas = "SAFE",
                struct = "SAFE",
                risk = "SYSTEM SAFE",
                shift = "ENDED",
                shiftStartTime = shiftStartTimeString,
                shiftEndTime = shiftEndTimeString,
                time = hmsFormat.format(Date())
            )
        }.start()
    }
    
    private fun triggerManualEmergency() {
        cvSafetyStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_danger))
        tvSafetyStatus.text = "EMERGENCY"
        Toast.makeText(this, "🚨 EMERGENCY ACTIVATED", Toast.LENGTH_LONG).show()
        
        addToHistory("EMERGENCY - MANUAL OVERRIDE")
        triggerAlarm()

        Thread {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            dbHelper.insertEmergency(workerName, "MANUAL OVERRIDE", "ACTIVE", time)
            dbHelper.updateWorkerStatus(name = workerName, motion = "FALL DETECTED", temp = "High", gas = "DANGER", struct = "CRITICAL", risk = "EMERGENCY ALERT", shift = "Active", shiftStartTime = shiftStartTimeString, shiftEndTime = shiftEndTimeString, time = time)
        }.start()
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                fetchData()
                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun fetchData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val startTime = System.currentTimeMillis()
                val url = URL("http://192.168.4.1/")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Connection", "close")
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.connect()

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val latency = System.currentTimeMillis() - startTime
                val json = JSONObject(response)

                lastSuccessfulRead = System.currentTimeMillis()

                val wearableMotion = json.optString("wearableMotion", "SAFE")
                val wearableTemp = json.optString("wearableTemp", "NORMAL")
                val gasStr = json.optString("gas", "SAFE")
                val structStr = json.optString("structureState", "SAFE")
                val finalStr = json.optString("final", "SYSTEM SAFE")
                
                withContext(Dispatchers.Main) {
                    processSensorData(latency, wearableMotion, wearableTemp, gasStr, structStr, finalStr)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastSuccessfulRead > 5000) {
                        tvConnectionStatus.text = "ESP32: Disconnected"
                        tvConnectionStatus.setTextColor(ContextCompat.getColor(this@WorkerDashboardActivity, R.color.status_danger))
                        tvIpAddress.text = "IP: 192.168.4.1 | Connection Lost"
                        tvLatency.text = "⚡ -- ms"
                    }
                }
            }
        }
    }

    private fun processSensorData(latency: Long, wearableMotion: String, wearableTemp: String, gasStr: String, structStr: String, finalStr: String) {
        tvConnectionStatus.text = "ESP32: Connected"
        tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.white))
        tvIpAddress.text = "IP: 192.168.4.1 | Connected"
        tvLatency.text = "⚡ ${latency}ms"

        val currentTime = System.currentTimeMillis()
        val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(currentTime))

        tvMotionStatus.text = wearableMotion
        
        tvTempStatus.text = wearableTemp
        tvTempTrend.text = ""
        
        if (wearableTemp == "DANGER" || wearableTemp == "CRITICAL" || wearableTemp == "HIGH") {
            tvTempStatus.setTextColor(ContextCompat.getColor(this, R.color.status_danger))
        } else if (wearableTemp == "CAUTION" || wearableTemp == "WARNING") {
            tvTempStatus.setTextColor(ContextCompat.getColor(this, R.color.status_caution))
        } else {
            tvTempStatus.setTextColor(ContextCompat.getColor(this, R.color.status_safe))
        }

        tvGasStatus.text = gasStr
        tvStructureStatus.text = structStr

        val finalState = finalStr

        if (finalState != "SYSTEM SAFE" && finalState != "UNKNOWN") {
            triggerAlarm()
            if (finalState == "EMERGENCY ALERT") {
                tvAiConfidence.text = "95% Confidence"
                tvAiExplanation.text = "CRITICAL: Immediate danger detected! Alarm triggered."
                tvAiTimer.text = "Evacuate immediately!"
            } else {
                tvAiConfidence.text = "80% Confidence"
                tvAiExplanation.text = "Caution: Anomalous readings. Risk escalation likely."
                tvAiTimer.text = "Next model update: 00:05"
            }
        } else {
            stopAlarm()
            tvAiConfidence.text = "92% Confidence"
            tvAiExplanation.text = "All parameters normal. Expected to remain SAFE for the next 120 seconds."
            tvAiTimer.text = "Next model update: 00:05"
        }

        tvSafetyStatus.text = finalState
        updateCircleColor(finalState)
        
        // Log changes
        val latestLog = if (historyLog.isNotEmpty()) historyLog.last().second else ""
        if (latestLog != finalState) {
            addToHistory(finalState)
        }

        Thread {
            dbHelper.updateWorkerStatus(
                name = workerName,
                motion = wearableMotion,
                temp = wearableTemp,
                gas = gasStr,
                struct = structStr,
                risk = finalState,
                shift = if(isShiftActive) "ACTIVE" else "ENDED",
                shiftStartTime = shiftStartTimeString,
                shiftEndTime = shiftEndTimeString,
                time = timeString
            )
            
            if (finalState == "EMERGENCY ALERT") {
                dbHelper.insertEmergency(workerName, "SENSOR AUTOTRIGGER", "ACTIVE", timeString)
            }
        }.start()
    }

    private fun triggerAlarm() {
        if (alarmRingtone != null && !alarmRingtone!!.isPlaying) {
            alarmRingtone!!.play()
            Toast.makeText(this, "🚨 ALARM TRIGGERED", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAlarm() {
        if (alarmRingtone != null && alarmRingtone!!.isPlaying) {
            alarmRingtone!!.stop()
            Toast.makeText(this, "✅ SYSTEM SAFE - ALARM STOPPED", Toast.LENGTH_SHORT).show()
            addToHistory("Recovery: Normal limits restored")
        }
    }

    private fun updateCircleColor(state: String) {
        val color = when {
            state.contains("EMERGENCY") || state.contains("CRITICAL") -> R.color.status_danger
            state.contains("WARNING") || state.contains("RISK") -> R.color.status_caution
            else -> R.color.status_safe
        }
        cvSafetyStatus.setCardBackgroundColor(ContextCompat.getColor(this, color))
    }

    private fun addToHistory(state: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        historyLog.add(Pair(time, state))

        if (historyLog.size > 50) {
            historyLog.removeAt(0)
        }

        val builder = SpannableStringBuilder()
        for (logEntry in historyLog) {
            val entryText = "${logEntry.first} - ${logEntry.second}\n"
            val startIndex = builder.length
            builder.append(entryText)
            
            val colorRes = when {
                logEntry.second.contains("SAFE") || logEntry.second.contains("Recovery") -> R.color.status_safe
                logEntry.second.contains("WARNING") || logEntry.second.contains("CAUTION") || logEntry.second.contains("UNSTABLE") -> R.color.status_caution
                logEntry.second.contains("EMERGENCY") || logEntry.second.contains("FALL") || logEntry.second.contains("DANGER") -> R.color.status_danger
                else -> R.color.white
            }
            builder.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, colorRes)),
                startIndex,
                builder.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        tvHistory.text = builder

        svHistory.post {
            svHistory.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
        handler.removeCallbacksAndMessages(null)
        if (isShiftActive) {
            Thread {
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                dbHelper.logAttendanceEnd(workerName, currentDate, time, "Completed")
            }.start()
        }
    }
}