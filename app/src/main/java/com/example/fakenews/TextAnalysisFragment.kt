package com.example.fakenews

import android.animation.ValueAnimator
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fakenews.db.AnalysisRecord // Import AnalysisRecord
import com.example.fakenews.db.DatabaseHelper // Import DatabaseHelper
import com.example.fakenews.FakeNewsApiService
import com.example.simplefakenews.databinding.FragmentTextAnalysisBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.abs

class TextAnalysisFragment : Fragment() {

    private var _binding: FragmentTextAnalysisBinding? = null
    private val binding get() = _binding!!

    private var lastAnalyzedText: String = ""
    private var lastPredictionResult: String = "" // Storing "REAL" or "FAKE"
    private var lastConfidenceValue: Int = 0
    private var currentThresholdValue: Float = 0.5f

    private lateinit var dbHelper: DatabaseHelper
    private var currentAnalysisId: Long? = null
    private var currentShapValues: ShapExplanation? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextAnalysisBinding.inflate(inflater, container, false)
        dbHelper = DatabaseHelper(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        setupTextWatcher()
        setupThresholdSeekBar()
        setupFeedbackListeners()
    }

    private fun setupClickListeners() {
        binding.btnAnalyze.setOnClickListener {
            analyzeNews()
        }
        binding.btnClear.setOnClickListener {
            clearResults()
        }
        binding.btnExplain.setOnClickListener {
            explainPrediction()
        }
        binding.btnFeedback.setOnClickListener {
            showFeedbackDialog()
        }
    }

    private fun setupFeedbackListeners() {
        binding.btnCloseFeedback.setOnClickListener {
            hideFeedbackDialog()
        }
        binding.rgCorrection.setOnCheckedChangeListener { _, checkedId ->
            binding.btnSubmitFeedback.isEnabled = checkedId != -1
        }
        binding.btnSubmitFeedback.setOnClickListener {
            submitFeedback()
        }
    }

    private fun setupTextWatcher() {
        binding.etNewsText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val length = s?.length ?: 0
                binding.tvCharCount.text = "$length characters"
                binding.btnAnalyze.isEnabled = length >= 10
            }
        })
    }

    private fun setupThresholdSeekBar() {
        binding.seekBarThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentThresholdValue = 0.2f + (progress / 100.0f)
                val percentage = (currentThresholdValue * 100).toInt()
                binding.tvThresholdValue.text = "$percentage%"
                updateThresholdExplanation(percentage)
                // Optionally re-evaluate displayed result if already analyzed
                if (lastAnalyzedText.isNotEmpty()) {
                    showResults(lastPredictionResult, lastConfidenceValue, currentThresholdValue)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        binding.seekBarThreshold.progress = 30 // Default 50%
        updateThresholdExplanation(50)
    }

    private fun updateThresholdExplanation(percentage: Int) {
        val explanation = when {
            percentage < 35 -> "Lenient: Model needs only $percentage% confidence to classify as real news"
            percentage < 55 -> "Balanced: Model needs $percentage% confidence to classify as real news"
            else -> "Strict: Model needs $percentage% confidence to classify as real news"
        }
        binding.tvThresholdExplanation.text = explanation
    }


    private fun analyzeNews() {
        val newsText = binding.etNewsText.text.toString().trim()
        if (newsText.isEmpty() || newsText.length < 10) {
            showError(if (newsText.isEmpty()) "Please enter some news text" else "Please enter at least 10 characters")
            return
        }

        lastAnalyzedText = newsText
        showLoading(true, "Analyzing text...")
        binding.resultsCard.visibility = View.GONE
        binding.explanationCard.visibility = View.GONE
        binding.feedbackCard.visibility = View.GONE
        currentShapValues = null // Reset SHAP for new analysis

        lifecycleScope.launch {
            try {
                val request = NewsRequest(newsText)
                val response = FakeNewsApiService.instance.predictNews(request)

                lastPredictionResult = response.prediction
                lastConfidenceValue = response.confidence
                showResults(response.prediction, response.confidence, currentThresholdValue)

                val analysisRecord = AnalysisRecord(
                    inputType = "text",
                    inputText = newsText,
                    extractedText = null,
                    prediction = response.prediction,
                    confidence = response.confidence,
                    thresholdUsed = currentThresholdValue,
                    timestamp = System.currentTimeMillis(),
                    userFeedback = null,
                    feedbackTimestamp = null,
                    shapEmbeddings = null,
                    shapSentiment = null,
                    shapClickbait = null
                )
                currentAnalysisId = withContext(Dispatchers.IO) {
                    dbHelper.addAnalysis(analysisRecord)
                }

                response.message?.let { showSuccess(it) }
            } catch (e: Exception) {
                handleConnectionError(e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun explainPrediction() {
        if (lastAnalyzedText.isEmpty()) {
            showError("Please analyze some text first")
            return
        }
        showLoading(true, "Generating explanation...")
        binding.explanationCard.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val request = NewsRequest(lastAnalyzedText)
                val response = FakeNewsApiService.instance.explainNews(request)
                currentShapValues = response.explanation // Store for saving
                showExplanation(response.explanation)

                currentAnalysisId?.let { id ->
                    val record = withContext(Dispatchers.IO) { dbHelper.getAnalysis(id) }
                    record?.let {
                        it.shapEmbeddings = response.explanation.embeddings_contribution
                        it.shapSentiment = response.explanation.sentiment_contribution
                        it.shapClickbait = response.explanation.clickbait_contribution
                        withContext(Dispatchers.IO) { dbHelper.updateAnalysis(it) }
                    }
                }
                showSuccess("Explanation generated successfully!")
            } catch (e: Exception) {
                handleExplanationError(e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showFeedbackDialog() {
        if (lastAnalyzedText.isEmpty() || lastPredictionResult.isEmpty()) {
            showError("Please analyze some text first")
            return
        }
        binding.feedbackCard.visibility = View.VISIBLE
        binding.tvCurrentPrediction.text = "Model predicted: ${lastPredictionResult.uppercase()} ($lastConfidenceValue% confidence)"
        binding.rgCorrection.clearCheck()
        binding.btnSubmitFeedback.isEnabled = false
        binding.tvFeedbackThanks.visibility = View.GONE
        binding.feedbackCard.requestFocus()
    }

    private fun hideFeedbackDialog() {
        binding.feedbackCard.visibility = View.GONE
    }

    private fun submitFeedback() {
        val selectedRadioButtonId = binding.rgCorrection.checkedRadioButtonId
        if (selectedRadioButtonId == -1) {
            showError("Please select the correct classification")
            return
        }
        val correctedLabel = when (selectedRadioButtonId) {
            binding.rbReal.id -> "REAL"
            binding.rbFake.id -> "FAKE"
            else -> return
        }

        showLoading(true, "Submitting feedback...")
        lifecycleScope.launch {
            try {
                // Assuming FeedbackRequest structure matches your API
                val feedbackRequest = com.example.fakenews.FeedbackRequest( // Use fully qualified name if ambiguous
                    text = lastAnalyzedText,
                    prediction = lastPredictionResult,
                    confidence = lastConfidenceValue,
                    label = correctedLabel
                )
                FakeNewsApiService.instance.submitFeedback(feedbackRequest) // API call

                currentAnalysisId?.let { id ->
                    val record = withContext(Dispatchers.IO) { dbHelper.getAnalysis(id) }
                    record?.let {
                        it.userFeedback = correctedLabel
                        it.feedbackTimestamp = System.currentTimeMillis()
                        // Optionally, update prediction if feedback changes it
                        // it.prediction = correctedLabel
                        withContext(Dispatchers.IO) { dbHelper.updateAnalysis(it) }
                    }
                }
                binding.tvFeedbackThanks.visibility = View.VISIBLE
                binding.btnSubmitFeedback.isEnabled = false
                showSuccess("Thank you for your feedback!")
                binding.root.postDelayed({ hideFeedbackDialog() }, 3000)
            } catch (e: Exception) {
                handleFeedbackError(e)
            } finally {
                showLoading(false)
            }
        }
    }


    private fun showResults(prediction: String, confidence: Int, thresholdUsed: Float) {
        binding.resultsCard.visibility = View.VISIBLE
        val resultText = if (prediction.equals("FAKE", ignoreCase = true)) {
            "⚠️ FAKE NEWS DETECTED"
        } else {
            "✅ APPEARS LEGITIMATE"
        }
        binding.tvResult.text = resultText
        binding.tvConfidence.text = "Model Confidence: $confidence%"
        binding.tvThresholdUsed.text = "Threshold used: ${(thresholdUsed * 100).toInt()}%"
        val textColor = if (prediction.equals("FAKE", ignoreCase = true)) {
            ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
        } else {
            ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
        }
        binding.tvResult.setTextColor(textColor)
    }

    private fun showExplanation(explanation: ShapExplanation) {
        binding.explanationCard.visibility = View.VISIBLE
        binding.tvEmbeddingsValue.text = formatContribution(explanation.embeddings_contribution)
        binding.tvSentimentValue.text = formatContribution(explanation.sentiment_contribution)
        binding.tvClickbaitValue.text = formatContribution(explanation.clickbait_contribution)
        updateContributionColor(binding.tvEmbeddingsValue, explanation.embeddings_contribution)
        updateContributionColor(binding.tvSentimentValue, explanation.sentiment_contribution)
        updateContributionColor(binding.tvClickbaitValue, explanation.clickbait_contribution)
        animateProgressBar(binding.progressEmbeddings, explanation.embeddings_contribution)
        animateProgressBar(binding.progressSentiment, explanation.sentiment_contribution)
        animateProgressBar(binding.progressClickbait, explanation.clickbait_contribution)
        binding.tvExplanationSummary.text = generateExplanationSummary(explanation)
    }

    private fun formatContribution(value: Double): String = if (value >= 0) "+%.3f".format(value) else "%.3f".format(value)

    private fun updateContributionColor(textView: android.widget.TextView, value: Double) {
        val colorRes = if (value >= 0) android.R.color.holo_green_dark else android.R.color.holo_red_dark
        textView.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun animateProgressBar(progressView: View, contribution: Double) {
        progressView.post {
            val parentWidth = (progressView.parent as View).width
            val normalizedValue = (abs(contribution) * 100).coerceAtMost(100.0)
            val targetWidth = (parentWidth * (normalizedValue / 100.0)).toInt()
            ValueAnimator.ofInt(0, targetWidth).apply {
                duration = 1000
                addUpdateListener { animation ->
                    progressView.layoutParams.width = animation.animatedValue as Int
                    progressView.requestLayout()
                }
                val colorRes = if (contribution >= 0) android.R.color.holo_green_light else android.R.color.holo_red_light
                progressView.setBackgroundColor(ContextCompat.getColor(requireContext(), colorRes))
                start()
            }
        }
    }

    private fun generateExplanationSummary(explanation: ShapExplanation): String {
        val contributions = mapOf(
            "content analysis" to explanation.embeddings_contribution,
            "sentiment" to explanation.sentiment_contribution,
            "clickbait detection" to explanation.clickbait_contribution
        )
        val maxEntry = contributions.maxByOrNull { abs(it.value) }
        return maxEntry?.let {
            val influence = if (it.value >= 0) "towards real" else "towards fake"
            "The model's decision was primarily influenced by ${it.key} $influence."
        } ?: "Explanation summary is unavailable."
    }

    private fun showLoading(show: Boolean, message: String = "Processing...") {
        binding.loadingLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvLoadingText.text = message
        val enableControls = !show
        binding.btnAnalyze.isEnabled = enableControls
        binding.btnExplain.isEnabled = enableControls && lastAnalyzedText.isNotEmpty()
        binding.btnFeedback.isEnabled = enableControls && lastAnalyzedText.isNotEmpty() && lastPredictionResult.isNotEmpty()
    }

    private fun clearResults() {
        binding.etNewsText.text?.clear()
        binding.resultsCard.visibility = View.GONE
        binding.explanationCard.visibility = View.GONE
        binding.feedbackCard.visibility = View.GONE
        binding.tvCharCount.text = "0 characters"
        lastAnalyzedText = ""
        lastPredictionResult = ""
        lastConfidenceValue = 0
        currentAnalysisId = null
        currentShapValues = null
    }

    private fun handleConnectionError(e: Exception) = showError(
        when (e) {
            is ConnectException -> "Cannot connect to server. Check connection settings."
            is UnknownHostException -> "Server not found. Verify IP address in settings."
            is SocketTimeoutException -> "Connection timeout. Server might be slow."
            is HttpException -> "Server error: ${e.code()}"
            else -> "Network error: ${e.localizedMessage}"
        }
    )

    private fun handleExplanationError(e: Exception) = showError(
        when (e) {
            is ConnectException -> "Cannot connect to explanation service."
            is HttpException -> "Explanation service error: ${e.code()}"
            else -> "Could not generate explanation: ${e.localizedMessage}"
        }
    )

    private fun handleFeedbackError(e: Exception) = showError(
        when (e) {
            is ConnectException -> "Cannot connect to feedback service."
            is HttpException -> "Feedback service error: ${e.code()}"
            else -> "Could not submit feedback: ${e.localizedMessage}"
        }
    )

    private fun showError(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    private fun showSuccess(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
