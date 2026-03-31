package com.example.dualshieldai.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.dualshieldai.model.Worker

object EmergencyManager {
    private val _emergencyTriggered = MutableLiveData<Boolean>(false)
    val emergencyTriggered: LiveData<Boolean> get() = _emergencyTriggered
    
    // In a real app, this would be a list or a map of worker IDs
    var currentEmergencyWorker: Worker? = null

    fun triggerEmergency(worker: Worker) {
        _emergencyTriggered.postValue(true)
        currentEmergencyWorker = worker
    }

    fun clearEmergency() {
        _emergencyTriggered.postValue(false)
        currentEmergencyWorker = null
    }
}
