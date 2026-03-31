package com.example.dualshieldai.model

data class Worker(
    val name: String,
    val role: String,
    var tempStatus: String,
    var motionStatus: String,
    var gasStatus: String,
    var safetyLevel: SafetyLevel,
    var trend: String = "Stable", // "Improving", "Worsening", "Stable"
    var shiftStatus: String = "Inactive", // "Active", "Inactive"
    var shiftStartTime: String = "--:--",
    var shiftEndTime: String = "--:--",
    var shiftDuration: String = "00:00:00",
    var attendanceStatus: String = "NO"
)
