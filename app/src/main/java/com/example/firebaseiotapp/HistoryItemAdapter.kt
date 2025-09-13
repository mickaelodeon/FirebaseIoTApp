package com.example.firebaseiotapp

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.app.firebaseiotapp.databinding.ItemHistoryBinding
import java.util.Date

class HistoryItemAdapter : RecyclerView.Adapter<HistoryItemAdapter.ViewHolder>() {
    private var items = listOf<HistoryItem>()

    fun updateItems(newItems: List<HistoryItem>) {
        items = newItems.sortedByDescending { it.timestamp }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text1: TextView = view.findViewById(android.R.id.text1)
        private val text2: TextView = view.findViewById(android.R.id.text2)

        fun bind(item: HistoryItem) {
            text1.text = "Value: ${item.value} (${item.source})"
            text2.text = DateFormat.format("dd/MM/yyyy HH:mm:ss", Date(item.timestamp))
        }
    }
}