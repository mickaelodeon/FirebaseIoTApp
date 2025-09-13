package com.app.firebaseiotapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.firebaseiotapp.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: TurbidityReadingAdapter
    private var latestListener: ValueEventListener? = null
    private var readingsListener: ValueEventListener? = null
    private var deviceStatusListener: ValueEventListener? = null
    private var alertsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeFirebase()
        setupRecyclerView()
        setupClickListeners()
    }

    private fun initializeFirebase() {
        auth = FirebaseAuth.getInstance()
        database = Firebase.database

        // Try anonymous authentication first
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                updateConnectionStatus("Connected to Firebase", true)
                startListening()
            } else {
                updateConnectionStatus("Authentication failed", false)
                Log.e("TurbidityApp", "Authentication failed", task.exception)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = TurbidityReadingAdapter()
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnUpdateValue.setOnClickListener {
            testTurbidityValue()
        }

        binding.btnStartListening.setOnClickListener {
            startListening()
        }

        binding.btnStopListening.setOnClickListener {
            stopListening()
        }
    }

    private fun startListening() {
        stopListening() // Clean up any existing listeners

        // Listen to the latest turbidity reading
        latestListener = database.getReference("latest").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val turbidity = snapshot.child("turbidity").getValue(Double::class.java) ?: 0.0
                    updateCurrentValue(turbidity)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("TurbidityApp", "Latest data listener cancelled", error.toException())
                }
            }
        )

        // Listen to all turbidity readings for history
        readingsListener = database.getReference("readings").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val readings = mutableListOf<TurbidityReading>()
                    snapshot.children.forEach { child ->
                        val turbidity = child.child("turbidity").getValue(Double::class.java) ?: 0.0
                        val timestamp = child.child("timestamp").getValue(String::class.java) ?: ""
                        val unit = child.child("unit").getValue(String::class.java) ?: "NTU"
                        val deviceId = child.child("device_id").getValue(String::class.java) ?: ""

                        readings.add(TurbidityReading(turbidity, timestamp, unit, deviceId))
                    }
                    adapter.updateReadings(readings)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("TurbidityApp", "Readings listener cancelled", error.toException())
                }
            }
        )

        // Listen to device status
        deviceStatusListener = database.getReference("device_status").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.child("status").getValue(String::class.java) ?: ""
                    val wifiRssi = snapshot.child("wifi_rssi").getValue(Int::class.java) ?: 0
                    val freeHeap = snapshot.child("free_heap").getValue(Long::class.java) ?: 0

                    updateDeviceStatus(status, wifiRssi, freeHeap)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("TurbidityApp", "Device status listener cancelled", error.toException())
                }
            }
        )

        // Listen to alerts
        alertsListener = database.getReference("alerts").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.forEach { child ->
                        val type = child.child("type").getValue(String::class.java) ?: ""
                        val value = child.child("value").getValue(Double::class.java) ?: 0.0
                        val message = child.child("message").getValue(String::class.java) ?: ""

                        if (type.isNotEmpty()) {
                            showAlert(type, value, message)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("TurbidityApp", "Alerts listener cancelled", error.toException())
                }
            }
        )

        updateConnectionStatus("Listening for turbidity data...", true)
    }

    private fun stopListening() {
        latestListener?.let { database.getReference("latest").removeEventListener(it) }
        readingsListener?.let { database.getReference("readings").removeEventListener(it) }
        deviceStatusListener?.let { database.getReference("device_status").removeEventListener(it) }
        alertsListener?.let { database.getReference("alerts").removeEventListener(it) }

        latestListener = null
        readingsListener = null
        deviceStatusListener = null
        alertsListener = null
        updateConnectionStatus("Stopped listening", false)
    }

    private fun testTurbidityValue() {
        val newValueStr = binding.etNewValue.text.toString()
        if (newValueStr.isBlank()) {
            Toast.makeText(this, "Please enter a turbidity value", Toast.LENGTH_SHORT).show()
            return
        }

        val newValue = newValueStr.toDoubleOrNull()
        if (newValue == null) {
            Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timestamp = System.currentTimeMillis().toString()

                // Update latest reading
                val latestData = mapOf(
                    "turbidity" to newValue
                )
                database.getReference("latest").setValue(latestData).await()

                // Add new reading to readings
                val readingData = mapOf(
                    "turbidity" to newValue,
                    "timestamp" to timestamp,
                    "unit" to "NTU",
                    "device_id" to "Android_App"
                )
                database.getReference("readings").child(timestamp).setValue(readingData).await()

                // Check if value is high and create alert
                if (newValue > 1000.0) {
                    val alertData = mapOf(
                        "type" to "high_turbidity",
                        "value" to newValue,
                        "message" to "High turbidity detected from Android app: $newValue NTU"
                    )
                    database.getReference("alerts").child(timestamp).setValue(alertData).await()
                }

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Turbidity value updated successfully", Toast.LENGTH_SHORT).show()
                    binding.etNewValue.text.clear()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("TurbidityApp", "Update failed", e)
                }
            }
        }
    }

    private fun updateCurrentValue(turbidity: Double) {
        binding.tvCurrentValue.text = "Current Turbidity: $turbidity NTU"
        binding.tvLastUpdate.text = "Last Update: ${getCurrentTime()}"
    }

    private fun updateDeviceStatus(status: String, wifiRssi: Int, freeHeap: Long) {
        val deviceInfo = "Device: $status | WiFi: ${wifiRssi}dBm | Heap: ${freeHeap/1024}KB"
        // You can display this in a TextView or log it
        Log.i("TurbidityApp", "Device Status: $deviceInfo")
    }

    private fun showAlert(type: String, value: Double, message: String) {
        Toast.makeText(this, "ALERT: $message", Toast.LENGTH_LONG).show()
        Log.w("TurbidityApp", "Alert - Type: $type, Value: $value, Message: $message")
    }

    private fun updateConnectionStatus(message: String, isConnected: Boolean) {
        binding.tvConnectionStatus.text = message
        binding.tvConnectionStatus.setTextColor(
            if (isConnected) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_red_dark)
        )
    }

    private fun getCurrentTime(): String {
        return android.text.format.DateFormat.format("dd/MM/yyyy HH:mm:ss", Date()).toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }
}