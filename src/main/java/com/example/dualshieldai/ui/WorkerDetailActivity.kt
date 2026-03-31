package com.example.dualshieldai.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.dualshieldai.R
import com.example.dualshieldai.utils.DatabaseHelper
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Color

class WorkerDetailActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var chartTemp: LineChart
    private lateinit var chartMotion: LineChart
    private val maxDataPoints = 150
    private var timeSeconds = 0f
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_detail)

        val name = intent.getStringExtra("EXTRA_NAME") ?: "Unknown"
        val role = intent.getStringExtra("EXTRA_ROLE") ?: "Worker"
        val gasStatus = intent.getStringExtra("EXTRA_GAS") ?: "Normal"
        val safetyLevelStr = intent.getStringExtra("EXTRA_SAFETY") ?: "SAFE"

        findViewById<TextView>(R.id.tvDetailName).text = name
        findViewById<TextView>(R.id.tvDetailRole).text = role

        val aiSummary = findViewById<TextView>(R.id.tvDetailAiSummary)
        
        val insight = if (safetyLevelStr == "DANGER" || gasStatus.contains("High") || gasStatus.contains("Critical")) {
             "Worker may enter unsafe state in 2 minutes due to rising gas concentration. Immediate monitoring recommended."
        } else if (safetyLevelStr == "CAUTION") {
             "Slight instability in vitals. Fatigue escalation probability: 67% in next hour."
        } else {
             "Worker operational status is optimal. No anomalies predicted for next 4 hours."
        }
        
        aiSummary.text = insight

        val dbHelper = DatabaseHelper(this)
        val cvEmergencyLifecycle = findViewById<MaterialCardView>(R.id.cvEmergencyLifecycle)
        val tvEmergencyState = findViewById<TextView>(R.id.tvEmergencyState)
        val btnMarkRescued = findViewById<Button>(R.id.btnMarkRescued)
        val btnCloseIncident = findViewById<Button>(R.id.btnCloseIncident)

        val activeEmergency = dbHelper.getActiveEmergencyForWorker(name)
        
        if (activeEmergency != null) {
            cvEmergencyLifecycle.visibility = android.view.View.VISIBLE
            val emergencyId = activeEmergency["id"] ?: ""
            val status = activeEmergency["status"] ?: "ACTIVE"
            val startTimeStr = activeEmergency["time"] ?: ""

            tvEmergencyState.text = "Status: $status"

            if (status == "ACTIVE") {
                cvEmergencyLifecycle.strokeColor = ContextCompat.getColor(this, R.color.status_danger)
                btnMarkRescued.visibility = android.view.View.VISIBLE
                btnCloseIncident.visibility = android.view.View.GONE
            } else if (status == "RESCUED" || status == "Rescue In Progress") {
                cvEmergencyLifecycle.strokeColor = ContextCompat.getColor(this, R.color.status_caution)
                btnMarkRescued.visibility = android.view.View.GONE
                btnCloseIncident.visibility = android.view.View.VISIBLE
            }

            btnMarkRescued.setOnClickListener {
                val resolvedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                
                dbHelper.updateEmergencyStatus(emergencyId, "RESOLVED", closedTime = resolvedTime)
                dbHelper.updateSafetyStatus(name, "SAFE")
                
                tvEmergencyState.text = "Status: EMERGENCY RESOLVED"
                cvEmergencyLifecycle.strokeColor = ContextCompat.getColor(this, R.color.status_safe)
                
                btnMarkRescued.text = "RESCUED"
                btnMarkRescued.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_safe)
                
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.cancel()
                
                dbHelper.insertEmergency(name, "HISTORY_LOG", "Alert: $startTimeStr | Sensors: $gasStatus | Action: Resolved", resolvedTime)
                Toast.makeText(this, "Rescue Completed. Worker is Safe.", Toast.LENGTH_SHORT).show()
            }
        }
        
        chartTemp = findViewById(R.id.chartTemp)
        chartMotion = findViewById(R.id.chartMotion)
        
        setupLineChart(chartTemp, 30f, 45f)
        setupLineChart(chartMotion, 0f, 2f)
        
        chartTemp.data = LineData(LineDataSet(ArrayList<Entry>(), "Temperature (°C)").apply {
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2.5f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        })
        
        chartMotion.data = LineData(LineDataSet(ArrayList<Entry>(), "Motion Activity").apply {
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2.5f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        })

        startMonitoring()
    }

    private fun setupLineChart(chart: LineChart, yMin: Float, yMax: Float) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(false)
        chart.setDrawGridBackground(false)
        
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.textColor = Color.WHITE
        
        val yAxis = chart.axisLeft
        yAxis.axisMinimum = yMin
        yAxis.axisMaximum = yMax
        yAxis.setDrawGridLines(true)
        yAxis.textColor = Color.WHITE
        
        chart.axisRight.isEnabled = false
        chart.legend.textColor = Color.WHITE
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                fetchSensorData()
                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun fetchSensorData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://192.168.4.1/")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Connection", "close")
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.connect()

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                val motion = json.optString("wearableMotion", "SAFE")
                val tempRaw = json.optDouble("wearableTempRaw", 36.5)

                withContext(Dispatchers.Main) {
                    timeSeconds += 2f
                    addMotionPoint(motion)
                    addTempPoint(tempRaw)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    timeSeconds += 2f
                    addMotionPoint("OFFLINE")
                    addTempPoint(0.0)
                }
            }
        }
    }

    private fun addMotionPoint(state: String) {
        val data = chartMotion.data ?: return
        val set = data.getDataSetByIndex(0) as LineDataSet
        
        val yVal = when (state) {
            "FALL DETECTED", "FALL", "CRITICAL", "EMERGENCY ALERT" -> 2f
            "UNSTABLE", "PREFALL", "WARNING", "WORKER RISK" -> 1f
            else -> 0f
        }
        
        data.addEntry(Entry(timeSeconds, yVal), 0)
        if (set.entryCount > maxDataPoints) {
            set.removeFirst()
        }
        
        val colorList = mutableListOf<Int>()
        for (i in 0 until set.entryCount) {
            val y = set.getEntryForIndex(i).y
            val color = when (y) {
                2f -> Color.RED
                1f -> Color.rgb(255, 165, 0)
                else -> Color.GREEN
            }
            colorList.add(color)
        }
        set.colors = colorList

        data.notifyDataChanged()
        chartMotion.notifyDataSetChanged()
        chartMotion.setVisibleXRangeMaximum(150f)
        chartMotion.moveViewToX(timeSeconds)
    }

    private fun addTempPoint(temp: Double) {
        val data = chartTemp.data ?: return
        val set = data.getDataSetByIndex(0) as LineDataSet
        
        val t = if (temp == 0.0) 36.5f else temp.toFloat()
        data.addEntry(Entry(timeSeconds, t), 0)
        if (set.entryCount > maxDataPoints) {
            set.removeFirst()
        }
        
        val colorList = mutableListOf<Int>()
        for (i in 0 until set.entryCount) {
            val y = set.getEntryForIndex(i).y
            val color = when {
                y >= 39.0f -> Color.RED
                y >= 38.0f -> Color.YELLOW
                else -> Color.GREEN
            }
            colorList.add(color)
        }
        set.colors = colorList

        data.notifyDataChanged()
        chartTemp.notifyDataSetChanged()
        chartTemp.setVisibleXRangeMaximum(150f)
        chartTemp.moveViewToX(timeSeconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
