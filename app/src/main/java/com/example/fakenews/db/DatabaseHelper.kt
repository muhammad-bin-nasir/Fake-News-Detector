package com.example.fakenews.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class AnalysisRecord(
    val id: Long = -1,
    val inputType: String,
    val inputText: String,
    val extractedText: String?,
    val prediction: String,
    val confidence: Int,
    val thresholdUsed: Float,
    val timestamp: Long,
    var userFeedback: String?,
    var feedbackTimestamp: Long?,
    var shapEmbeddings: Double?,
    var shapSentiment: Double?,
    var shapClickbait: Double?
)

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "FakeNewsHistory.db"

        // Table Name
        private const val TABLE_ANALYSIS_HISTORY = "analysis_history"

        // Table Columns
        private const val KEY_ID = "id"
        private const val KEY_INPUT_TYPE = "inputType" // "text" or "url"
        private const val KEY_INPUT_TEXT = "inputText"
        private const val KEY_EXTRACTED_TEXT = "extractedText"
        private const val KEY_PREDICTION = "prediction" // "REAL" or "FAKE"
        private const val KEY_CONFIDENCE = "confidence" // Integer percentage
        private const val KEY_THRESHOLD_USED = "thresholdUsed" // Float
        private const val KEY_TIMESTAMP = "timestamp" // Long
        private const val KEY_USER_FEEDBACK = "userFeedback" // "real", "fake", or null
        private const val KEY_FEEDBACK_TIMESTAMP = "feedbackTimestamp" // Long or null
        private const val KEY_SHAP_EMBEDDINGS = "shapEmbeddings" // Double or null
        private const val KEY_SHAP_SENTIMENT = "shapSentiment" // Double or null
        private const val KEY_SHAP_CLICKBAIT = "shapClickbait" // Double or null

        private const val CREATE_TABLE_ANALYSIS_HISTORY = """
            CREATE TABLE $TABLE_ANALYSIS_HISTORY (
                $KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_INPUT_TYPE TEXT,
                $KEY_INPUT_TEXT TEXT,
                $KEY_EXTRACTED_TEXT TEXT,
                $KEY_PREDICTION TEXT,
                $KEY_CONFIDENCE INTEGER,
                $KEY_THRESHOLD_USED REAL,
                $KEY_TIMESTAMP INTEGER,
                $KEY_USER_FEEDBACK TEXT,
                $KEY_FEEDBACK_TIMESTAMP INTEGER,
                $KEY_SHAP_EMBEDDINGS REAL,
                $KEY_SHAP_SENTIMENT REAL,
                $KEY_SHAP_CLICKBAIT REAL
            )
        """
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(CREATE_TABLE_ANALYSIS_HISTORY)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Drop older table if existed
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_ANALYSIS_HISTORY")
        // Create tables again
        onCreate(db)
    }

    // Insert a new analysis record
    fun addAnalysis(record: AnalysisRecord): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_INPUT_TYPE, record.inputType)
            put(KEY_INPUT_TEXT, record.inputText)
            put(KEY_EXTRACTED_TEXT, record.extractedText)
            put(KEY_PREDICTION, record.prediction)
            put(KEY_CONFIDENCE, record.confidence)
            put(KEY_THRESHOLD_USED, record.thresholdUsed)
            put(KEY_TIMESTAMP, record.timestamp)
            // Nullable fields
            record.userFeedback?.let { put(KEY_USER_FEEDBACK, it) }
            record.feedbackTimestamp?.let { put(KEY_FEEDBACK_TIMESTAMP, it) }
            record.shapEmbeddings?.let { put(KEY_SHAP_EMBEDDINGS, it) }
            record.shapSentiment?.let { put(KEY_SHAP_SENTIMENT, it) }
            record.shapClickbait?.let { put(KEY_SHAP_CLICKBAIT, it) }
        }
        val id = db.insert(TABLE_ANALYSIS_HISTORY, null, values)
        db.close()
        return id // Returns the ID of the newly inserted row
    }

    // Get a single analysis record by ID
    fun getAnalysis(id: Long): AnalysisRecord? {
        val db = this.readableDatabase
        var record: AnalysisRecord? = null
        val cursor: Cursor? = db.query(
            TABLE_ANALYSIS_HISTORY,
            null, // All columns
            "$KEY_ID=?",
            arrayOf(id.toString()),
            null, null, null, null
        )

        cursor?.use { c ->
            if (c.moveToFirst()) {
                record = cursorToAnalysisRecord(c)
            }
        }
        db.close()
        return record
    }

    // Update an existing analysis record (e.g., to add feedback or SHAP)
    fun updateAnalysis(record: AnalysisRecord): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_USER_FEEDBACK, record.userFeedback)
            put(KEY_FEEDBACK_TIMESTAMP, record.feedbackTimestamp)
            put(KEY_SHAP_EMBEDDINGS, record.shapEmbeddings)
            put(KEY_SHAP_SENTIMENT, record.shapSentiment)
            put(KEY_SHAP_CLICKBAIT, record.shapClickbait)
            // Add other fields if they can be updated
            put(KEY_PREDICTION, record.prediction) // In case feedback changes the effective prediction
            put(KEY_CONFIDENCE, record.confidence)
        }
        val rowsAffected = db.update(
            TABLE_ANALYSIS_HISTORY,
            values,
            "$KEY_ID = ?",
            arrayOf(record.id.toString())
        )
        db.close()
        return rowsAffected
    }


    // Get all analysis records
    fun getAllAnalyses(): List<AnalysisRecord> {
        val records = mutableListOf<AnalysisRecord>()
        val selectQuery = "SELECT * FROM $TABLE_ANALYSIS_HISTORY ORDER BY $KEY_TIMESTAMP DESC"
        val db = this.readableDatabase
        val cursor: Cursor? = db.rawQuery(selectQuery, null)

        cursor?.use { c ->
            if (c.moveToFirst()) {
                do {
                    records.add(cursorToAnalysisRecord(c))
                } while (c.moveToNext())
            }
        }
        db.close()
        return records
    }

    // Clear all history
    fun clearHistory() {
        val db = this.writableDatabase
        db.delete(TABLE_ANALYSIS_HISTORY, null, null)
        db.close()
    }

    private fun cursorToAnalysisRecord(cursor: Cursor): AnalysisRecord {
        return AnalysisRecord(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ID)),
            inputType = cursor.getString(cursor.getColumnIndexOrThrow(KEY_INPUT_TYPE)),
            inputText = cursor.getString(cursor.getColumnIndexOrThrow(KEY_INPUT_TEXT)),
            extractedText = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(KEY_EXTRACTED_TEXT)),
            prediction = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PREDICTION)),
            confidence = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_CONFIDENCE)),
            thresholdUsed = cursor.getFloat(cursor.getColumnIndexOrThrow(KEY_THRESHOLD_USED)),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP)),
            userFeedback = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(KEY_USER_FEEDBACK)),
            feedbackTimestamp = cursor.getLongOrNull(cursor.getColumnIndexOrThrow(KEY_FEEDBACK_TIMESTAMP)),
            shapEmbeddings = cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow(KEY_SHAP_EMBEDDINGS)),
            shapSentiment = cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow(KEY_SHAP_SENTIMENT)),
            shapClickbait = cursor.getDoubleOrNull(cursor.getColumnIndexOrThrow(KEY_SHAP_CLICKBAIT))
        )
    }

    // Helper extension function to get nullable Long from cursor
    private fun Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (this.isNull(columnIndex)) null else this.getLong(columnIndex)
    }
    // Helper extension function to get nullable Double from cursor
    private fun Cursor.getDoubleOrNull(columnIndex: Int): Double? {
        return if (this.isNull(columnIndex)) null else this.getDouble(columnIndex)
    }
    // Helper extension function to get nullable String from cursor
    private fun Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (this.isNull(columnIndex)) null else this.getString(columnIndex)
    }
}
