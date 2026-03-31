package com.example.dualshieldai.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object AttendanceManager {
    private val _activeWorkerCount = MutableLiveData<Int>(12) // Initial count
    val activeWorkerCount: LiveData<Int> get() = _activeWorkerCount

    private val _shiftStatus = MutableLiveData<String>("Inactive")
    val shiftStatus: LiveData<String> get() = _shiftStatus

    fun startShift() {
        if (_shiftStatus.value != "Active") {
            _shiftStatus.postValue("Active")
            _activeWorkerCount.postValue((_activeWorkerCount.value ?: 0) + 1)
        }
    }

    fun endShift() {
        if (_shiftStatus.value == "Active") {
            _shiftStatus.postValue("Ended")
            val currentCount = _activeWorkerCount.value ?: 0
            if (currentCount > 0) {
                _activeWorkerCount.postValue(currentCount - 1)
            }
        }
    }
}
