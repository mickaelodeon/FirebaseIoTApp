package com.app.firebaseiotapp
import com.example.firebaseiotapp.HistoryItemAdapter
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
    private lateinit var adapter: HistoryItemAdapter
    private var dataListener: ValueEventListener? = null
    private var historyListener: ValueEventListener? = null
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
                Log.e("FirebaseApp", "Authentication failed", task.exception)
            }
        }
    }
    private fun setupRecyclerView() {
        adapter = HistoryItemAdapter()
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }
    private fun setupClickListeners() {
        binding.btnUpdateValue.setOnClickListener {
            updateData()
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
        // Listen to the main data path (same as ESP32)
        dataListener = database.getReference("test/data").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val value = snapshot.getValue(Int::class.java) ?: 0
                    updateCurrentValue(value)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseApp", "Data listener cancelled", error.toException())
                }
            }
        )
        // Listen to history updates
        historyListener = database.getReference("test/history").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val historyItems = mutableListOf<HistoryItem>()
                    snapshot.children.forEach { child ->
                        val value = child.child("value").getValue(Int::class.java) ?: 0
                        val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0
                        val source = child.child("source").getValue(String::class.java) ?: "Unknown"
                        historyItems.add(HistoryItem(value, timestamp, source))
                    }
                    adapter.updateItems(historyItems)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseApp", "History listener cancelled", error.toException())
                }
            }
        )
        updateConnectionStatus("Listening for updates...", true)
    }
    private fun stopListening() {
        dataListener?.let { database.getReference("test/data").removeEventListener(it) }
        historyListener?.let { database.getReference("test/history").removeEventListener(it) }
        dataListener = null
        historyListener = null
        updateConnectionStatus("Stopped listening", false)
    }
    private fun updateData() {
        val newValueStr = binding.etNewValue.text.toString()
        if (newValueStr.isBlank()) {
            Toast.makeText(this, "Please enter a value", Toast.LENGTH_SHORT).show()
            return
        }
        val newValue = newValueStr.toIntOrNull()
        if (newValue == null) {
            Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update main data value
                database.getReference("test/data").setValue(newValue).await()
                // Add to history
                val historyRef = database.getReference("test/history").push()
                val historyData = mapOf(
                    "value" to newValue,
                    "timestamp" to System.currentTimeMillis(),
                    "source" to "Android App"
                )
                historyRef.setValue(historyData).await()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Value updated successfully",
                        Toast.LENGTH_SHORT).show()
                    binding.etNewValue.text.clear()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Update failed: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                    Log.e("FirebaseApp", "Update failed", e)
                }
            }
        }
    }
    private fun updateCurrentValue(value: Int) {
        binding.tvCurrentValue.text = "Current Value: $value"
        binding.tvLastUpdate.text = "Last Update: ${getCurrentTime()}"
    }
    private fun updateConnectionStatus(message: String, isConnected: Boolean) {
        binding.tvConnectionStatus.text = message
        binding.tvConnectionStatus.setTextColor(
            if (isConnected) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_red_dark)
        )
    }
    private fun getCurrentTime(): String {
        return android.text.format.DateFormat.format("dd/MM/yyyy HH:mm:ss",
            Date()).toString()
    }
    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }
}