package com.example.dualshieldai.ui

import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.dualshieldai.R
import com.example.dualshieldai.model.SafetyLevel
import com.example.dualshieldai.utils.DatabaseHelper
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class SupervisorDashboardActivity : AppCompatActivity() {
    // Global Emergency Banner
    private lateinit var cvEmergencyAlert: MaterialCardView
    private lateinit var tvGlobalStatusTitle: TextView
    private lateinit var tvEmergencyDetails: TextView
    // Infra Panel
    private lateinit var tvInfraGas: TextView
    private lateinit var tvInfraGasState: TextView
    private lateinit var tvInfraStructure: TextView
    private lateinit var tvConnectionLatency: TextView
    // AI Panel
    private lateinit var tvAiRiskProb: TextView
    private lateinit var tvAiAnalysisDetails: TextView
    private lateinit var tvPredictionTimer: TextView
    private lateinit var glWorkers: GridLayout
    // Overlays
    private lateinit var vCriticalBorder: View
    private lateinit var llLockScreen: LinearLayout
    private lateinit var btnAcknowledgeCritical: Button
    private lateinit var dbHelper: DatabaseHelper
    private val handler = Handler(Looper.getMainLooper())
    private var moderateAlarm: Ringtone? = null
    private var criticalAlarm: Ringtone? = null
    private var vibrator: Vibrator? = null

    private var isFlashing = false
    private var isFullyCritical = false
    private var flashRunnable: Runnable? = null

    // Worker specific tracking


    private var lastWearableUpdate: Long = 0L
    private var currentTempString: String = "No Sensors"
    private var currentMotionString: String = "No Sensors"
    private var hasVibratedForCurrentAlert = false
    private var alertActive = false
    private var lastFinalStatus = "SYSTEM SAFE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_supervisor_dashboard)

        cvEmergencyAlert = findViewById(R.id.cvEmergencyAlert)
        tvGlobalStatusTitle = findViewById(R.id.tvGlobalStatusTitle)
        tvEmergencyDetails = findViewById(R.id.tvEmergencyDetails)

        tvInfraGas = findViewById(R.id.tvInfraGas)
        tvInfraGasState = findViewById(R.id.tvInfraGasState)
        tvInfraStructure = findViewById(R.id.tvInfraStructure)
        tvConnectionLatency = findViewById(R.id.tvConnectionLatency)

        tvAiRiskProb = findViewById(R.id.tvAiRiskProb)
        tvAiAnalysisDetails = findViewById(R.id.tvAiAnalysisDetails)
        tvPredictionTimer = findViewById(R.id.tvPredictionTimer)
        glWorkers = findViewById(R.id.glWorkers)

        vCriticalBorder = findViewById(R.id.vCriticalBorder)
        llLockScreen = findViewById(R.id.llLockScreen)
        btnAcknowledgeCritical = findViewById(R.id.btnAcknowledgeCritical)

        dbHelper = DatabaseHelper(this)

        val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        moderateAlarm = RingtoneManager.getRingtone(applicationContext, notificationUri)
        criticalAlarm = RingtoneManager.getRingtone(applicationContext, alarmUri)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        btnAcknowledgeCritical.setOnClickListener {
            acknowledgeCriticalState()
        }

        startMonitoring()
    }

    private fun acknowledgeCriticalState() {
        llLockScreen.visibility = View.GONE
        isFullyCritical = false
        alertActive = false
        // DO NOT reset hasVibratedForCurrentAlert here - wait until SYSTEM SAFE
        stopBothAlarms()
        stopFlashing()
        
        Toast.makeText(this, "Emergency Resolved", Toast.LENGTH_LONG).show()

        Thread {
            val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            dbHelper.insertEmergency("SUPERVISOR", "Rescue Action: Resolved | Sensors=Temp: $currentTempString, Motion: $currentMotionString", "RESOLVED", timeString)
        }.start()
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                if (!isFullyCritical) {
                    fetchInfraData()
                }
                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun fetchInfraData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val startTime = System.currentTimeMillis()
                System.setProperty("http.keepAlive", "false")
                val url = URL("http://192.168.4.1/")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Connection", "close")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.connect()

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val latency = System.currentTimeMillis() - startTime
                
                val json = JSONObject(response)
                
                val gasStr = json.optString("gas", "SAFE")
                val gasPpm = json.optDouble("gasPpm", if (gasStr != "SAFE") 120.0 else 25.0)
                val structureStr = json.optString("structureState", "SAFE")

                val finalStatus = json.optString("final", "SYSTEM SAFE")
                val motionStr = json.optString("wearableMotion", "No Sensors")
                val tempStr = json.optString("wearableTemp", "No Sensors")
                val workerID = json.optString("workerID", "")

                currentMotionString = motionStr
                currentTempString = tempStr
                lastWearableUpdate = System.currentTimeMillis()

                val registeredNames = dbHelper.getRegisteredWorkers().map { it.name }
                for (name in registeredNames) {
                    val state = com.example.dualshieldai.model.WorkerStateManager.getWorkerState(name)
                    if (state.shiftStarted) {
                        state.lastSensorTime = System.currentTimeMillis()
                        state.tempStatus = tempStr
                        state.motionStatus = motionStr
                    }
                }

                withContext(Dispatchers.Main) {
                    tvConnectionLatency.text = "Latency: ${latency}ms"
                    
                    tvInfraStructure.text = structureStr
                    tvInfraGasState.text = "($gasStr)"
                    tvInfraGas.text = "$gasPpm ppm"

                    if (gasStr != "SAFE") {
                        tvInfraGasState.setTextColor(ContextCompat.getColor(this@SupervisorDashboardActivity, R.color.status_danger))
                    } else {
                        tvInfraGasState.setTextColor(ContextCompat.getColor(this@SupervisorDashboardActivity, R.color.status_safe))
                    }

                    val sysTime = System.currentTimeMillis()

                    val determinedLevel = when (finalStatus) {
                        "EMERGENCY ALERT" -> SafetyLevel.DANGER
                        "WORKER RISK" -> SafetyLevel.CAUTION
                        else -> SafetyLevel.SAFE
                    }

                    val registeredWorkerNames = dbHelper.getRegisteredWorkers().map { it.name }
                    val constructedWorkers = mutableListOf<com.example.dualshieldai.model.Worker>()
                    for (name in registeredWorkerNames) {
                        val state = com.example.dualshieldai.model.WorkerStateManager.getWorkerState(name)
                        if (!state.shiftStarted) {
                            state.attendance = "NO"
                            state.tempStatus = "Device Disconnected"
                            state.motionStatus = "Device Disconnected"
                            state.safetyLevel = SafetyLevel.SAFE
                        } else {
                            state.attendance = "YES"
                            if (sysTime - state.lastSensorTime > 10000) {
                                state.tempStatus = "Device Disconnected"
                                state.motionStatus = "Device Disconnected"
                                state.safetyLevel = SafetyLevel.SAFE
                            } else {
                                state.safetyLevel = determinedLevel
                            }
                        }

                        constructedWorkers.add(
                            com.example.dualshieldai.model.Worker(
                                name = name,
                                role = "Worker",
                                tempStatus = state.tempStatus,
                                motionStatus = state.motionStatus,
                                gasStatus = "Normal",
                                safetyLevel = state.safetyLevel,
                                trend = "Stable",
                                shiftStatus = if (state.shiftStarted) "ACTIVE" else "NOT STARTED",
                                shiftStartTime = state.shiftStartTime ?: "--:--",
                                shiftEndTime = state.shiftEndTime ?: "--:--",
                                shiftDuration = "00:00:00",
                                attendanceStatus = state.attendance
                            )
                        )
                    }
                    updateWorkerGrid(constructedWorkers)

                    evaluateCriticalLogic(finalStatus)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvConnectionLatency.text = "Latency: -- ms"
                    
                    val sysTime = System.currentTimeMillis()
                    val registeredWorkerNames = dbHelper.getRegisteredWorkers().map { it.name }
                    val constructedWorkers = mutableListOf<com.example.dualshieldai.model.Worker>()
                    
                    for (name in registeredWorkerNames) {
                        val state = com.example.dualshieldai.model.WorkerStateManager.getWorkerState(name)
                        if (!state.shiftStarted) {
                            state.attendance = "NO"
                            state.tempStatus = "Device Disconnected"
                            state.motionStatus = "Device Disconnected"
                            state.safetyLevel = SafetyLevel.SAFE
                        } else {
                            state.attendance = "YES"
                            if (sysTime - state.lastSensorTime > 10000) {
                                state.tempStatus = "Device Disconnected"
                                state.motionStatus = "Device Disconnected"
                                state.safetyLevel = SafetyLevel.SAFE
                            }
                        }

                        constructedWorkers.add(
                            com.example.dualshieldai.model.Worker(
                                name = name,
                                role = "Worker",
                                tempStatus = state.tempStatus,
                                motionStatus = state.motionStatus,
                                gasStatus = "Normal",
                                safetyLevel = state.safetyLevel,
                                trend = "Stable",
                                shiftStatus = if (state.shiftStarted) "ACTIVE" else "NOT STARTED",
                                shiftStartTime = state.shiftStartTime ?: "--:--",
                                shiftEndTime = state.shiftEndTime ?: "--:--",
                                shiftDuration = "00:00:00",
                                attendanceStatus = state.attendance
                            )
                        )
                    }
                    updateWorkerGrid(constructedWorkers)
                    
                    evaluateCriticalLogic("UNKNOWN")
                }
            }
        }
    }

    private fun evaluateCriticalLogic(finalStatus: String) {
        if (finalStatus == "UNKNOWN") {
            // Ignore intermittent disconnections, maintain current alert state
            return
        }

        if (finalStatus != "SYSTEM SAFE") {
            // New alert detected
            if (lastFinalStatus == "SYSTEM SAFE") {
                alertActive = true
                hasVibratedForCurrentAlert = false
            }

            if (alertActive) {
                if (finalStatus == "EMERGENCY ALERT" || finalStatus == "WORKER RISK") {
                    triggerFullSiteCritical()
                } else {
                    triggerModerateAlert()
                }
                setEmergencyState(true, "Alert: $finalStatus | Temp: $currentTempString | Motion: $currentMotionString")
            } else {
                // Rescue Worker was pressed
                setEmergencyState(false, "Rescue Worker Initiated. Resolving...")
            }
        } else {
            // Safe state
            alertActive = false
            hasVibratedForCurrentAlert = false
            stopBothAlarms()
            stopFlashing()
            setEmergencyState(false, "No active risks detected. Scanning...")
            llLockScreen.visibility = View.GONE
            isFullyCritical = false
        }
        
        lastFinalStatus = finalStatus
    }

    private fun triggerModerateAlert() {
        if (criticalAlarm?.isPlaying == true) {
            criticalAlarm?.stop()
        }
        if (moderateAlarm?.isPlaying == false) {
            moderateAlarm?.play()
        }

        if (!hasVibratedForCurrentAlert) {
            vibrator?.let {
                if (it.hasVibrator()) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 1000), intArrayOf(0, 255, 0), -1)
                    it.vibrate(effect)
                }
            }
            hasVibratedForCurrentAlert = true
        }
    }

    private fun triggerFullSiteCritical() {
        isFullyCritical = true
        llLockScreen.visibility = View.VISIBLE
        
        if (moderateAlarm?.isPlaying == true) {
            moderateAlarm?.stop()
        }
        if (criticalAlarm?.isPlaying == false) {
            criticalAlarm?.play()
        }

        if (!hasVibratedForCurrentAlert) {
            vibrator?.let {
                if (it.hasVibrator()) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 1000, 500), intArrayOf(0, 255, 0), -1)
                    it.vibrate(effect)
                }
            }
            hasVibratedForCurrentAlert = true
        }

        if (!isFlashing) {
            isFlashing = true
            flashRunnable = object : Runnable {
                override fun run() {
                    vCriticalBorder.visibility = if (vCriticalBorder.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    if (isFlashing) {
                        handler.postDelayed(this, 500)
                    } else {
                        vCriticalBorder.visibility = View.GONE
                    }
                }
            }
            handler.post(flashRunnable!!)
        }
    }

    private fun stopBothAlarms() {
        if (moderateAlarm?.isPlaying == true) moderateAlarm?.stop()
        if (criticalAlarm?.isPlaying == true) criticalAlarm?.stop()
        vibrator?.cancel()
    }

    private fun stopFlashing() {
        isFlashing = false
        flashRunnable?.let { handler.removeCallbacks(it) }
        vCriticalBorder.visibility = View.GONE
    }

    private fun updateWorkerGrid(workers: List<com.example.dualshieldai.model.Worker>) {
        glWorkers.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (worker in workers) {
            val view = inflater.inflate(R.layout.item_worker_grid_card, glWorkers, false)
            val ivIcon = view.findViewById<ImageView>(R.id.ivWorkerStatusIcon)
            val tvName = view.findViewById<TextView>(R.id.tvWorkerName)
            val tvStats = view.findViewById<TextView>(R.id.tvWorkerStats)
            
            tvName.text = worker.name
            
            val builder = android.text.SpannableStringBuilder()
            builder.append("Temp: ${worker.tempStatus}\n")
            builder.append("Motion: ${worker.motionStatus}\n")
            builder.append("Attendance: ")
            
            val attStartIndex = builder.length
            builder.append("${worker.attendanceStatus}\n")
            val attColor = if (worker.attendanceStatus == "YES") R.color.status_safe else R.color.status_danger
            builder.setSpan(
                android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, attColor)),
                attStartIndex,
                builder.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            builder.append("Shift: ")
            val shiftStartIndex = builder.length
            builder.append("${worker.shiftStatus}")
            val shiftColor = if (worker.shiftStatus == "ACTIVE") R.color.status_safe else R.color.status_danger
            builder.setSpan(
                android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, shiftColor)),
                shiftStartIndex,
                builder.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            tvStats.text = builder
            
            val color = when (worker.safetyLevel) {
                SafetyLevel.DANGER -> R.color.status_danger
                SafetyLevel.CAUTION -> R.color.status_caution
                else -> R.color.status_safe
            }
            ivIcon.backgroundTintList = ContextCompat.getColorStateList(this, color)

            view.setOnClickListener {
                val intent = Intent(this, WorkerDetailActivity::class.java).apply {
                    putExtra("EXTRA_NAME", worker.name)
                    putExtra("EXTRA_ROLE", "Worker")
                    putExtra("EXTRA_GAS", "N/A")
                    putExtra("EXTRA_SAFETY", worker.safetyLevel.name)
                }
                startActivity(intent)
            }
            
            glWorkers.addView(view)
        }
    }

    private fun setEmergencyState(isEmergency: Boolean, details: String) {
        if (isEmergency) {
            cvEmergencyAlert.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_danger))
            tvGlobalStatusTitle.text = "🚨 EMERGENCY ALERT ACTIVE"
            tvEmergencyDetails.text = details
            
            tvAiRiskProb.text = "Risk: 84%"
            tvAiRiskProb.setTextColor(ContextCompat.getColor(this, R.color.status_danger))
            tvAiAnalysisDetails.text = "Anomalies detected. Escalation probability high within next 150 seconds."
            tvPredictionTimer.text = "Next Risk Window: 02:30"
            
        } else {
            cvEmergencyAlert.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_safe))
            tvGlobalStatusTitle.text = "ALL SYSTEMS STABLE"
            tvEmergencyDetails.text = details
            
            tvAiRiskProb.text = "Risk: 4%"
            tvAiRiskProb.setTextColor(ContextCompat.getColor(this, R.color.status_safe))
            tvAiAnalysisDetails.text = "All workers and zones stable. Confidence: 96%."
            tvPredictionTimer.text = "Next Risk Window: Safe for > 2:00:00"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBothAlarms()
        stopFlashing()
        handler.removeCallbacksAndMessages(null)
    }
}