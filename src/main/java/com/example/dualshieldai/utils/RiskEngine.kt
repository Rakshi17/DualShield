package com.example.dualshieldai.utils

import com.example.dualshieldai.model.SafetyLevel
import kotlin.random.Random

class RiskEngine {

    fun calculateStatus(temp: Float, motion: Float, gas: Float): SafetyLevel {
        // Simple logic simulation
        // High gas is immediate danger
        if (gas > 70.0f) return SafetyLevel.DANGER
        
        // High temp + active motion = Caution
        if (temp > 38.0f && motion > 5.0f) return SafetyLevel.CAUTION
        
        // High temp or erratic motion could also be danger/caution depending on thresholds
        if (temp > 40.0f) return SafetyLevel.DANGER
        
        // Default safe
        return SafetyLevel.SAFE
    }

    // Helper to generate simulated values
    fun generateSimulatedValues(): Triple<Float, Float, Float> {
        val temp = Random.nextFloat() * (45.0f - 30.0f) + 30.0f // 30-45 C
        val motion = Random.nextFloat() * 10.0f // 0-10 activity level
        val gas = Random.nextFloat() * 100.0f // 0-100 ppm/level
        return Triple(temp, motion, gas)
    }
}
