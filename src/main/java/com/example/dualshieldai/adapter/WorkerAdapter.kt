package com.example.dualshieldai.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dualshieldai.R
import com.example.dualshieldai.model.SafetyLevel
import com.example.dualshieldai.model.Worker

class WorkerAdapter(
    private val workers: MutableList<Worker>,
    private val onWorkerClick: (Worker) -> Unit
) : RecyclerView.Adapter<WorkerAdapter.WorkerViewHolder>() {

    class WorkerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.tvWorkerName)
        val role: TextView = itemView.findViewById(R.id.tvWorkerRole)
        val status: TextView = itemView.findViewById(R.id.tvWorkerStatus)
        val trend: TextView = itemView.findViewById(R.id.tvWorkerTrend)
        val shiftStatus: TextView = itemView.findViewById(R.id.tvWorkerShiftStatus)
        val startTime: TextView = itemView.findViewById(R.id.tvWorkerStartTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_worker, parent, false)
        return WorkerViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkerViewHolder, position: Int) {
        val worker = workers[position]
        holder.name.text = worker.name
        holder.role.text = worker.role
        holder.status.text = worker.safetyLevel.name
        holder.trend.text = worker.trend
        
        holder.shiftStatus.text = "Shift: ${worker.shiftStatus}"
        if (worker.shiftStatus == "Active") {
             holder.shiftStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.status_safe))
             holder.startTime.visibility = View.VISIBLE
             holder.startTime.text = "Started: ${worker.shiftStartTime}"
        } else {
             holder.shiftStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
             holder.startTime.visibility = View.GONE
        }

        val colorRes = when (worker.safetyLevel) {
            SafetyLevel.SAFE -> R.color.status_safe
            SafetyLevel.CAUTION -> R.color.status_caution
            SafetyLevel.DANGER -> R.color.status_danger
        }
        holder.status.setTextColor(ContextCompat.getColor(holder.itemView.context, colorRes))
        
        holder.itemView.setOnClickListener {
            onWorkerClick(worker)
        }
    }

    override fun getItemCount() = workers.size

    fun updateData(newWorkers: List<Worker>) {
        (workers as ArrayList).clear()
        workers.addAll(newWorkers)
        notifyDataSetChanged()
    }
}
