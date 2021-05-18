package com.frybits.harmony.view

import android.graphics.Color
import android.util.Log
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.frybits.harmony.test.LogEvent

private val LOG_DIFF_CALLBACK = object : DiffUtil.ItemCallback<LogEvent>() {
    override fun areItemsTheSame(oldItem: LogEvent, newItem: LogEvent): Boolean {
        return oldItem.uuid == newItem.uuid
    }

    override fun areContentsTheSame(oldItem: LogEvent, newItem: LogEvent): Boolean {
        return oldItem.priority == newItem.priority
                && oldItem.tag == newItem.tag
                && oldItem.message == newItem.message
    }
}

class LogListAdapter : ListAdapter<LogEvent, LogViewHolder>(LOG_DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        return LogViewHolder(TextView(parent.context))
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bindTo(getItem(position))
    }
}

class LogViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {

    private val defaultColor = textView.currentTextColor

    fun bindTo(logItem: LogEvent) {
        textView.text = "${logItem.tag}: ${logItem.message}"
        if (logItem.priority == Log.ERROR) {
            textView.setTextColor(Color.RED)
        } else {
            textView.setTextColor(defaultColor)
        }
    }
}
