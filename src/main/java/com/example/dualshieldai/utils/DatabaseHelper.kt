package com.example.dualshieldai.utils

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.dualshieldai.model.Worker
import com.example.dualshieldai.model.SafetyLevel

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "DualShieldAI.db"
        const val DATABASE_VERSION = 8

        const val TABLE_USERS = "users"
        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"
        const val COLUMN_PHONE = "phone"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_PASSWORD = "password"
        const val COLUMN_ROLE = "role"
        const val COLUMN_SHIFT_STATUS = "shiftStatus"
        const val COLUMN_SHIFT_START = "shiftStartTime"
        const val COLUMN_SAFETY_STATUS = "safetyStatus" // New: tracks SAFE, CAUTION, DANGER

        const val TABLE_EMERGENCIES = "emergencies"
        const val COLUMN_EMERGENCY_ID = "id"
        const val COLUMN_WORKER_NAME = "workerName"
        const val COLUMN_TYPE = "type"
        const val COLUMN_STATUS = "status"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_RESOLVED_TIME = "resolvedTime"
        const val COLUMN_CLOSED_TIME = "closedTime"
        const val COLUMN_RESPONSE_DURATION = "responseDuration"

        // New Tables for Real-time and Attendance
        const val TABLE_WORKER_STATUS = "worker_status"
        const val COLUMN_WS_ID = "id"
        const val COLUMN_WS_NAME = "worker_name"
        const val COLUMN_WS_MOTION = "motion"
        const val COLUMN_WS_TEMP = "temperature"
        const val COLUMN_WS_GAS = "gas"
        const val COLUMN_WS_STRUCT = "structure"
        const val COLUMN_WS_RISK = "risk"
        const val COLUMN_WS_SHIFT = "shift_status"
        const val COLUMN_WS_SHIFT_START = "shift_start"
        const val COLUMN_WS_SHIFT_END = "shift_end"
        const val COLUMN_WS_LAST_UPDATE = "last_update"

        const val TABLE_ATTENDANCE = "attendance"
        const val COLUMN_ATT_ID = "id"
        const val COLUMN_ATT_NAME = "worker_name"
        const val COLUMN_ATT_DATE = "date"
        const val COLUMN_ATT_LOGIN = "login_time"
        const val COLUMN_ATT_LOGOUT = "logout_time"
        const val COLUMN_ATT_DURATION = "total_duration"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT,
                $COLUMN_PHONE TEXT,
                $COLUMN_EMAIL TEXT UNIQUE,
                $COLUMN_PASSWORD TEXT,
                $COLUMN_ROLE TEXT,
                $COLUMN_SHIFT_STATUS TEXT DEFAULT 'Inactive',
                $COLUMN_SHIFT_START TEXT DEFAULT '--:--',
                $COLUMN_SAFETY_STATUS TEXT DEFAULT 'SAFE'
            )
        """.trimIndent()

        val createEmergenciesTable = """
            CREATE TABLE $TABLE_EMERGENCIES (
                $COLUMN_EMERGENCY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_WORKER_NAME TEXT,
                $COLUMN_TYPE TEXT,
                $COLUMN_STATUS TEXT,
                $COLUMN_TIMESTAMP TEXT,
                $COLUMN_RESOLVED_TIME TEXT,
                $COLUMN_CLOSED_TIME TEXT,
                $COLUMN_RESPONSE_DURATION TEXT
            )
        """.trimIndent()

        val createWorkerStatusTable = """
            CREATE TABLE $TABLE_WORKER_STATUS (
                $COLUMN_WS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_WS_NAME TEXT UNIQUE,
                $COLUMN_WS_MOTION TEXT,
                $COLUMN_WS_TEMP TEXT,
                $COLUMN_WS_GAS TEXT,
                $COLUMN_WS_STRUCT TEXT,
                $COLUMN_WS_RISK TEXT,
                $COLUMN_WS_SHIFT TEXT,
                $COLUMN_WS_SHIFT_START TEXT,
                $COLUMN_WS_SHIFT_END TEXT,
                $COLUMN_WS_LAST_UPDATE TEXT
            )
        """.trimIndent()

        val createAttendanceTable = """
            CREATE TABLE $TABLE_ATTENDANCE (
                $COLUMN_ATT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_ATT_NAME TEXT,
                $COLUMN_ATT_DATE TEXT,
                $COLUMN_ATT_LOGIN TEXT,
                $COLUMN_ATT_LOGOUT TEXT,
                $COLUMN_ATT_DURATION TEXT
            )
        """.trimIndent()

        db.execSQL(createUsersTable)
        db.execSQL(createEmergenciesTable)
        db.execSQL(createWorkerStatusTable)
        db.execSQL(createAttendanceTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EMERGENCIES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WORKER_STATUS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ATTENDANCE")
        onCreate(db)
    }

    // USER Functions
    fun insertUser(name: String, phone: String, email: String, pass: String, role: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, name)
            put(COLUMN_PHONE, phone)
            put(COLUMN_EMAIL, email)
            put(COLUMN_PASSWORD, pass)
            put(COLUMN_ROLE, role)
        }
        val result = db.insert(TABLE_USERS, null, values)
        return result != -1L
    }

    fun checkUser(email: String, pass: String): String? {
        try {
            val db = this.readableDatabase
            val cursor = db.rawQuery("SELECT $COLUMN_ROLE FROM $TABLE_USERS WHERE $COLUMN_EMAIL=? AND $COLUMN_PASSWORD=?", arrayOf(email.trim(), pass.trim()))
            var role: String? = null
            if (cursor.moveToFirst()) {
                role = cursor.getString(0)
            }
            cursor.close()
            return role
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    fun getUserName(email: String): String {
        try {
            val db = this.readableDatabase
            val cursor = db.rawQuery("SELECT $COLUMN_NAME FROM $TABLE_USERS WHERE $COLUMN_EMAIL=?", arrayOf(email.trim()))
            var name = "Unknown"
            if (cursor.moveToFirst()) {
                name = cursor.getString(0)
            }
            cursor.close()
            return name
        } catch (e: Exception) {
            return "Unknown"
        }
    }

    fun updateShiftStatus(email: String, status: String, startTime: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SHIFT_STATUS, status)
            put(COLUMN_SHIFT_START, startTime)
        }
        db.update(TABLE_USERS, values, "$COLUMN_EMAIL=?", arrayOf(email.trim()))
    }

    fun updateSafetyStatus(name: String, safetyStatus: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SAFETY_STATUS, safetyStatus)
        }
        db.update(TABLE_USERS, values, "$COLUMN_NAME=?", arrayOf(name.trim()))
    }

    fun getRegisteredWorkers(): List<Worker> {
        val workers = ArrayList<Worker>()
        try {
            val db = this.readableDatabase
            val cursor = db.rawQuery("SELECT $COLUMN_NAME FROM $TABLE_USERS WHERE $COLUMN_ROLE='Worker' COLLATE NOCASE", null)
            if (cursor.moveToFirst()) {
                do {
                    val name = cursor.getString(0)
                    workers.add(
                        Worker(
                            name = name,
                            role = "Worker",
                            tempStatus = "Device Disconnected",
                            motionStatus = "Device Disconnected",
                            gasStatus = "Normal",
                            safetyLevel = SafetyLevel.SAFE,
                            trend = "Stable",
                            shiftStatus = "NOT STARTED",
                            shiftStartTime = "--:--",
                            shiftEndTime = "--:--",
                            shiftDuration = "00:00:00",
                            attendanceStatus = "NO"
                        )
                    )
                } while (cursor.moveToNext())
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return workers
    }

    fun getAllWorkers(): List<Worker> {
        val workers = ArrayList<Worker>()
        try {
            val db = this.readableDatabase
            // Modified to join WORKER_STATUS for live data
            val cursor = db.rawQuery("SELECT * FROM $TABLE_WORKER_STATUS", null)
            
            if (cursor.moveToFirst()) {
                do {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WS_NAME))
                    val shiftStatus = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WS_SHIFT)) ?: "Inactive"
                    val shiftStart = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WS_SHIFT_START)) ?: "--:--"
                    val shiftEnd = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WS_SHIFT_END)) ?: "--:--"
                    val safetyString = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WS_RISK)) ?: "SAFE"
                    
                    val safetyLevel = when(safetyString) {
                        "DANGER", "EMERGENCY ALERT" -> SafetyLevel.DANGER
                        "CAUTION" -> SafetyLevel.CAUTION
                        else -> SafetyLevel.SAFE
                    }
                    
                    val worker = Worker(
                        name = name,
                        role = "Worker",
                        tempStatus = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WS_TEMP)) ?: "Normal",
                        motionStatus = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WS_MOTION)) ?: "Stable",
                        gasStatus = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WS_GAS)) ?: "Normal",
                        safetyLevel = safetyLevel,
                        trend = "Stable",
                        shiftStatus = shiftStatus,
                        shiftStartTime = shiftStart,
                        shiftEndTime = shiftEnd
                    )
                    workers.add(worker)
                } while (cursor.moveToNext())
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return workers
    }
    
    fun getActiveWorkerCount(): Int {
        try {
            val db = this.readableDatabase
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_USERS WHERE $COLUMN_ROLE='Worker' AND $COLUMN_SHIFT_STATUS='Active'", null)
            var count = 0
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0)
            }
            cursor.close()
            return count
        } catch (e: Exception) {
            return 0
        }
    }
    
    fun getTotalShiftCount(): Int {
         return getActiveWorkerCount() 
    }

    // EMERGENCY Functions
    fun insertEmergency(workerName: String, type: String, status: String, time: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_WORKER_NAME, workerName)
            put(COLUMN_TYPE, type)
            put(COLUMN_STATUS, status)
            put(COLUMN_TIMESTAMP, time)
        }
        db.insert(TABLE_EMERGENCIES, null, values)
    }

    fun updateEmergencyStatus(id: String, status: String, resolvedTime: String? = null, closedTime: String? = null, duration: String? = null) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_STATUS, status)
            if (resolvedTime != null) put(COLUMN_RESOLVED_TIME, resolvedTime)
            if (closedTime != null) put(COLUMN_CLOSED_TIME, closedTime)
            if (duration != null) put(COLUMN_RESPONSE_DURATION, duration)
        }
        db.update(TABLE_EMERGENCIES, values, "$COLUMN_EMERGENCY_ID=?", arrayOf(id))
    }

    fun getActiveEmergencyForWorker(workerName: String): Map<String, String>? {
        try {
            val db = this.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM $TABLE_EMERGENCIES WHERE $COLUMN_WORKER_NAME=? AND $COLUMN_STATUS IN ('ACTIVE', 'RESCUED', 'Rescue In Progress') ORDER BY $COLUMN_EMERGENCY_ID DESC LIMIT 1", arrayOf(workerName))
            var result: Map<String, String>? = null
            if (cursor.moveToFirst()) {
                 result = mapOf(
                     "id" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMERGENCY_ID)),
                     "name" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WORKER_NAME)),
                     "type" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE)),
                     "status" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS)),
                     "time" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                 )
            }
            cursor.close()
            return result
        } catch (e: Exception) {
            return null
        }
    }

    fun getAnyActiveEmergency(): Map<String, String>? {
        try {
            val db = this.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM $TABLE_EMERGENCIES WHERE $COLUMN_STATUS = 'ACTIVE' ORDER BY $COLUMN_EMERGENCY_ID DESC LIMIT 1", null)
            var result: Map<String, String>? = null
            if (cursor.moveToFirst()) {
                 result = mapOf(
                     "id" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMERGENCY_ID)),
                     "name" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WORKER_NAME)),
                     "type" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE)),
                     "status" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS)),
                     "time" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                 )
            }
            cursor.close()
            return result
        } catch (e: Exception) {
            return null
        }
    }

    fun getLatestEmergency(): Map<String, String>? {
        try {
            val db = this.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM $TABLE_EMERGENCIES ORDER BY $COLUMN_EMERGENCY_ID DESC LIMIT 1", null)
            var result: Map<String, String>? = null
            if (cursor.moveToFirst()) {
                 result = mapOf(
                     "id" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMERGENCY_ID)),
                     "name" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WORKER_NAME)),
                     "type" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE)),
                     "status" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS)),
                     "time" to cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                 )
            }
            cursor.close()
            return result
        } catch (e: Exception) {
            return null
        }
    }

    fun deleteEmergency(id: String) {
        val db = this.writableDatabase
        db.delete(TABLE_EMERGENCIES, "$COLUMN_EMERGENCY_ID=?", arrayOf(id))
    }

    // REAL-TIME WORKER STATUS Functions
    fun updateWorkerStatus(name: String, motion: String, temp: String, gas: String, struct: String, risk: String, shift: String, shiftStartTime: String = "--:--", shiftEndTime: String = "--:--", time: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_WS_NAME, name)
            put(COLUMN_WS_MOTION, motion)
            put(COLUMN_WS_TEMP, temp)
            put(COLUMN_WS_GAS, gas)
            put(COLUMN_WS_STRUCT, struct)
            put(COLUMN_WS_RISK, risk)
            put(COLUMN_WS_SHIFT, shift)
            put(COLUMN_WS_SHIFT_START, shiftStartTime)
            put(COLUMN_WS_SHIFT_END, shiftEndTime)
            put(COLUMN_WS_LAST_UPDATE, time)
        }
        // Insert or replace based on UNIQUE constraint
        db.insertWithOnConflict(TABLE_WORKER_STATUS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
    
    // ATTENDANCE Functions
    fun logAttendanceStart(name: String, date: String, loginTime: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ATT_NAME, name)
            put(COLUMN_ATT_DATE, date)
            put(COLUMN_ATT_LOGIN, loginTime)
            put(COLUMN_ATT_LOGOUT, "")
            put(COLUMN_ATT_DURATION, "")
        }
        db.insert(TABLE_ATTENDANCE, null, values)
    }

    fun logAttendanceEnd(name: String, date: String, logoutTime: String, duration: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ATT_LOGOUT, logoutTime)
            put(COLUMN_ATT_DURATION, duration)
        }
        db.update(TABLE_ATTENDANCE, values, "$COLUMN_ATT_NAME=? AND $COLUMN_ATT_DATE=?", arrayOf(name, date))
    }
}
