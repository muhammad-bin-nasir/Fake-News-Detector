package com.example.fakenews

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.fakenews.db.AnalysisRecord // Ensure this is your data class
import com.example.simplefakenews.R // Ensure this points to your R file
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class AnalysisHistoryAdapter(private var records: List<AnalysisRecord>) :
    RecyclerView.Adapter<AnalysisHistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analysis_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.bind(record)
    }

    override fun getItemCount(): Int = records.size

    fun updateData(newRecords: List<AnalysisRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvId: TextView = itemView.findViewById(R.id.tvRecordId)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvRecordTimestamp)
        private val tvInputType: TextView = itemView.findViewById(R.id.tvRecordInputType)
        private val tvInputText: TextView = itemView.findViewById(R.id.tvRecordInputText)
        private val tvExtractedText: TextView = itemView.findViewById(R.id.tvRecordExtractedText)
        private val tvPrediction: TextView = itemView.findViewById(R.id.tvRecordPrediction)
        private val tvConfidence: TextView = itemView.findViewById(R.id.tvRecordConfidence)
        private val tvFeedback: TextView = itemView.findViewById(R.id.tvRecordFeedback)
        private val tvShapValues: TextView = itemView.findViewById(R.id.tvRecordShapValues)

        fun bind(record: AnalysisRecord) {
            tvId.text = "ID: ${record.id}"
            tvTimestamp.text = dateFormat.format(Date(record.timestamp))
            tvInputType.text = "Type: ${record.inputType.uppercase()}"
            tvInputText.text = "Input: ${record.inputText}"

            if (record.extractedText.isNullOrEmpty()) {
                tvExtractedText.visibility = View.GONE
            } else {
                tvExtractedText.visibility = View.VISIBLE
                tvExtractedText.text = "Extracted: ${record.extractedText}"
            }

            val predictionColor = if (record.prediction.equals("FAKE", ignoreCase = true)) {
                ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark)
            } else {
                ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark)
            }
            tvPrediction.text = "Prediction: ${record.prediction.uppercase()}"
            tvPrediction.setTextColor(predictionColor)

            tvConfidence.text = "(Conf: ${record.confidence}%, Thr: ${(record.thresholdUsed * 100).roundToInt()}%)"


            if (record.userFeedback.isNullOrEmpty()) {
                tvFeedback.visibility = View.GONE
            } else {
                tvFeedback.visibility = View.VISIBLE
                val feedbackColor = if (record.userFeedback.equals(record.prediction, ignoreCase = true)) {
                    ContextCompat.getColor(itemView.context, R.color.success_green) // Or your theme's success color
                } else {
                    ContextCompat.getColor(itemView.context, R.color.warning_orange) // Or your theme's warning/error color
                }
                tvFeedback.text = "Feedback: ${record.userFeedback!!.uppercase()} ${if (!record.userFeedback.equals(record.prediction, ignoreCase = true)) "(Corrected)" else "(Confirmed)" }"
                tvFeedback.setTextColor(feedbackColor)
            }

            val shapParts = mutableListOf<String>()
            record.shapEmbeddings?.let { shapParts.add("E=%.2f".format(it)) }
            record.shapSentiment?.let { shapParts.add("S=%.2f".format(it)) }
            record.shapClickbait?.let { shapParts.add("C=%.2f".format(it)) }

            if (shapParts.isNotEmpty()) {
                tvShapValues.visibility = View.VISIBLE
                tvShapValues.text = "SHAP: ${shapParts.joinToString(", ")}"
            } else {
                tvShapValues.visibility = View.GONE
            }
        }
    }
}
