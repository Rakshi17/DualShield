package com.example.dualshieldai.model

data class WorkerState(
    var shiftStarted: Boolean = false,
    var attendance: String = "NO",
    var shiftStartTime: String? = "--:--",
    var shiftEndTime: String? = "--:--",
    var lastSensorTime: Long = 0L,
    var tempStatus: String = "Device Disconnected",
    var motionStatus: String = "Device Disconnected",
    var safetyLevel: SafetyLevel = SafetyLevel.SAFE
)

object WorkerStateManager {
    private val workers = mutableMapOf<String, WorkerState>()
    
    fun getWorkerState(name: String): WorkerState {
        val key = name.lowercase()
        if (!workers.containsKey(key)) {
            workers[key] = WorkerState()
        }
        return workers[key]!!
    }
}
