package com.app.firebaseiotapp

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Date

class TurbidityReadingAdapter : RecyclerView.Adapter<TurbidityReadingAdapter.ViewHolder>() {
    private var readings = listOf<TurbidityReading>()

    fun updateReadings(newReadings: List<TurbidityReading>) {
        readings = newReadings.sortedByDescending { it.timestamp.toLongOrNull() ?: 0L }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reading = readings[position]
        holder.bind(reading)
    }

    override fun getItemCount() = readings.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text1: TextView = view.findViewById(android.R.id.text1)
        private val text2: TextView = view.findViewById(android.R.id.text2)

        fun bind(reading: TurbidityReading) {
            text1.text = "Turbidity: ${reading.turbidity} ${reading.unit} (${reading.device_id})"
            val timestamp = reading.timestamp.toLongOrNull()
            if (timestamp != null) {
                text2.text = DateFormat.format("dd/MM/yyyy HH:mm:ss", Date(timestamp))
            } else {
                text2.text = "Time: ${reading.timestamp}"
            }
        }
    }
}
