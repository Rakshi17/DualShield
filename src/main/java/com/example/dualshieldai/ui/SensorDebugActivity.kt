package com.example.dualshieldai.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.dualshieldai.R
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class SensorDebugActivity : AppCompatActivity() {

    private lateinit var tvRawAccel: TextView
    private lateinit var tvRawTemp: TextView
    private lateinit var tvRawGas: TextView
    private lateinit var tvRawStructure: TextView
    private lateinit var tvRawJson: TextView

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_debug)

        tvRawAccel = findViewById(R.id.tvRawAccel)
        tvRawTemp = findViewById(R.id.tvRawTemp)
        tvRawGas = findViewById(R.id.tvRawGas)
        tvRawStructure = findViewById(R.id.tvRawStructure)
        tvRawJson = findViewById(R.id.tvRawJson)

        startMonitoring()
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
                System.setProperty("http.keepAlive", "false")
                val url = URL("http://192.168.4.1/")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Connection", "close")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.connect()

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val motionStr = json.optString("wearableMotion", "SAFE")
                val tempStr = json.optString("wearableTemp", "NORMAL")
                val structAccel = json.optDouble("structureAccel", 0.0)
                val gasStr = json.optString("gas", "SAFE")
                val structStr = json.optString("structureState", "SAFE")
                val finalStr = json.optString("final", "SYSTEM SAFE")

                withContext(Dispatchers.Main) {
                    tvRawAccel.text = "Wearable Motion:\n$motionStr"
                    tvRawTemp.text = "Wearable Temp:\n$tempStr"
                    tvRawGas.text = "Gas State:\n$gasStr"
                    tvRawStructure.text = "Struct State:\n$structStr\nStruct Accel:\n$structAccel"
                    tvRawJson.text = response
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvRawJson.text = "Error connecting: ${e.message}"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
