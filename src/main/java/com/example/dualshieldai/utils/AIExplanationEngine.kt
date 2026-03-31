package com.example.dualshieldai.utils

object AIExplanationEngine {
    fun generateInsight(tempStatus: String, motionStatus: String, gasStatus: String): String {
        return when {
            tempStatus == "High" && gasStatus == "Gas Critical" -> "IMMEDIATE EVACUATION: High heat and critical gas levels detected."
            tempStatus == "High" -> "Temperature rising steadily. Consider cooling breaks."
            motionStatus == "Fall Detected" -> "Impact detected. Analyzing movement for confirming fall."
            motionStatus == "Sudden Movement" -> "Sudden motion instability detected. Watch your step."
            gasStatus == "Gas Critical" -> "CRITICAL: Gas leak likely in your sector. Evacuate."
            gasStatus == "Gas Rising" -> "Gas levels increasing in your zone. Monitor sensors closely."
            tempStatus == "Elevated" -> "Temperature is slightly above normal range."
            else -> "Environment stable. Continue working safely."
        }
    }
}
