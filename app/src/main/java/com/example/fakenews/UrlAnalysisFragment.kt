package com.example.fakenews

import android.animation.ValueAnimator
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
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
import com.example.simplefakenews.databinding.FragmentUrlAnalysisBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.abs

class UrlAnalysisFragment : Fragment() {

    private var _binding: FragmentUrlAnalysisBinding? = null
    private val binding get() = _binding!!

    private var lastAnalyzedUrl: String = ""
    private var lastExtractedTextValue: String = ""
    private var lastPredictionResult: String = ""
    private var lastConfidenceValue: Int = 0
    private var currentThresholdValue: Float = 0.5f

    private lateinit var dbHelper: DatabaseHelper
    private var currentAnalysisId: Long? = null
    private var currentShapValues: ShapExplanation? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUrlAnalysisBinding.inflate(inflater, container, false)
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
        binding.btnAnalyzeUrl.setOnClickListener { analyzeUrl() }
        binding.btnClearUrl.setOnClickListener { clearResults() }
        binding.btnUrlExplain.setOnClickListener { explainPrediction() }
        binding.btnUrlFeedback.setOnClickListener { showFeedbackDialog() }
    }

    private fun setupFeedbackListeners() {
        binding.btnUrlCloseFeedback.setOnClickListener { hideFeedbackDialog() }
        binding.rgUrlCorrection.setOnCheckedChangeListener { _, checkedId ->
            binding.btnUrlSubmitFeedback.isEnabled = checkedId != -1
        }
        binding.btnUrlSubmitFeedback.setOnClickListener { submitFeedback() }
    }

    private fun setupTextWatcher() {
        binding.etNewsUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val url = s?.toString()?.trim() ?: ""
                binding.btnAnalyzeUrl.isEnabled = url.isNotEmpty() && isValidUrl(url)
            }
        })
    }

    private fun setupThresholdSeekBar() {
        binding.seekBarUrlThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentThresholdValue = 0.2f + (progress / 100.0f)
                val percentage = (currentThresholdValue * 100).toInt()
                binding.tvUrlThresholdValue.text = "$percentage%"
                updateThresholdExplanation(percentage)
                if (lastAnalyzedUrl.isNotEmpty()) {
                    showResults(lastPredictionResult, lastConfidenceValue, currentThresholdValue)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        binding.seekBarUrlThreshold.progress = 30
        updateThresholdExplanation(50)
    }

    private fun updateThresholdExplanation(percentage: Int) {
        val explanation = when {
            percentage < 35 -> "Lenient: Model needs only $percentage% confidence to classify as real news"
            percentage < 55 -> "Balanced: Model needs $percentage% confidence to classify as real news"
            else -> "Strict: Model needs $percentage% confidence to classify as real news"
        }
        binding.tvUrlThresholdExplanation.text = explanation
    }

    private fun isValidUrl(url: String): Boolean =
        Patterns.WEB_URL.matcher(url).matches() && (url.startsWith("http://") || url.startsWith("https://"))

    private fun analyzeUrl() {
        val newsUrl = binding.etNewsUrl.text.toString().trim()
        if (newsUrl.isEmpty() || !isValidUrl(newsUrl)) {
            showError(if (newsUrl.isEmpty()) "Please enter a URL" else "Please enter a valid URL (must start with http:// or https://)")
            return
        }

        lastAnalyzedUrl = newsUrl
        showLoading(true, "Fetching and analyzing URL...")
        hideAllCards()
        currentShapValues = null

        lifecycleScope.launch {
            try {
                binding.statusCard.visibility = View.VISIBLE
                binding.tvUrlStatus.text = "🔄 Fetching content from URL..."
                val scrapeRequest = UrlScrapeRequest(newsUrl)
                val scrapeResponse = FakeNewsApiService.instance.scrapeUrl(scrapeRequest)

                if (scrapeResponse.text.isNotEmpty()) {
                    lastExtractedTextValue = scrapeResponse.text
                    binding.tvExtractedText.text = "Title: ${scrapeResponse.title}\nPreview: ${scrapeResponse.text.take(200)}..."
                    binding.tvExtractedText.visibility = View.VISIBLE
                    binding.tvUrlStatus.text = "🔄 Analyzing extracted content..."

                    val analysisRequest = NewsRequest(scrapeResponse.text)
                    val analysisResponse = FakeNewsApiService.instance.predictNews(analysisRequest)

                    lastPredictionResult = analysisResponse.prediction
                    lastConfidenceValue = analysisResponse.confidence
                    showResults(analysisResponse.prediction, analysisResponse.confidence, currentThresholdValue)

                    val analysisRecord = AnalysisRecord(
                        inputType = "url",
                        inputText = newsUrl,
                        extractedText = scrapeResponse.text,
                        prediction = analysisResponse.prediction,
                        confidence = analysisResponse.confidence,
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
                    analysisResponse.message?.let { showSuccess(it) }
                } else {
                    showError("Could not extract text from URL. ${scrapeResponse.message ?: ""}")
                    binding.tvUrlStatus.text = "❌ Failed to extract content."
                }
            } catch (e: Exception) {
                handleConnectionError(e) // This will also update tvUrlStatus
            } finally {
                showLoading(false)
            }
        }
    }

    private fun explainPrediction() {
        if (lastExtractedTextValue.isEmpty()) {
            showError("Please analyze a URL first")
            return
        }
        showLoading(true, "Generating explanation...")
        binding.urlExplanationCard.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val request = NewsRequest(lastExtractedTextValue)
                val response = FakeNewsApiService.instance.explainNews(request)
                currentShapValues = response.explanation
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
        if (lastExtractedTextValue.isEmpty() || lastPredictionResult.isEmpty()) {
            showError("Please analyze a URL first")
            return
        }
        binding.urlFeedbackCard.visibility = View.VISIBLE
        binding.tvUrlCurrentPrediction.text = "Model predicted: ${lastPredictionResult.uppercase()} ($lastConfidenceValue% confidence)"
        binding.rgUrlCorrection.clearCheck()
        binding.btnUrlSubmitFeedback.isEnabled = false
        binding.tvUrlFeedbackThanks.visibility = View.GONE
        binding.urlFeedbackCard.requestFocus()
    }

    private fun hideFeedbackDialog() {
        binding.urlFeedbackCard.visibility = View.GONE
    }

    private fun submitFeedback() {
        val selectedRadioButtonId = binding.rgUrlCorrection.checkedRadioButtonId
        if (selectedRadioButtonId == -1) {
            showError("Please select the correct classification")
            return
        }
        val correctedLabel = when (selectedRadioButtonId) {
            binding.rbUrlReal.id -> "REAL"
            binding.rbUrlFake.id -> "FAKE"
            else -> return
        }

        showLoading(true, "Submitting feedback...")
        lifecycleScope.launch {
            try {
                val feedbackRequest = com.example.fakenews.FeedbackRequest(
                    text = lastExtractedTextValue, // Use extracted text for URL feedback
                    prediction = lastPredictionResult,
                    confidence = lastConfidenceValue,
                    label = correctedLabel
                )
                FakeNewsApiService.instance.submitFeedback(feedbackRequest)

                currentAnalysisId?.let { id ->
                    val record = withContext(Dispatchers.IO) { dbHelper.getAnalysis(id) }
                    record?.let {
                        it.userFeedback = correctedLabel
                        it.feedbackTimestamp = System.currentTimeMillis()
                        withContext(Dispatchers.IO) { dbHelper.updateAnalysis(it) }
                    }
                }
                binding.tvUrlFeedbackThanks.visibility = View.VISIBLE
                binding.btnUrlSubmitFeedback.isEnabled = false
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
        binding.urlResultsCard.visibility = View.VISIBLE
        binding.statusCard.visibility = View.GONE // Hide status card once results are shown
        val resultText = if (prediction.equals("FAKE", ignoreCase = true)) "⚠️ FAKE NEWS DETECTED" else "✅ APPEARS LEGITIMATE"
        binding.tvUrlResult.text = resultText
        binding.tvUrlConfidence.text = "Model Confidence: $confidence%"
        binding.tvUrlThresholdUsed.text = "Threshold used: ${(thresholdUsed * 100).toInt()}%"
        val textColor = if (prediction.equals("FAKE", ignoreCase = true)) android.R.color.holo_red_dark else android.R.color.holo_green_dark
        binding.tvUrlResult.setTextColor(ContextCompat.getColor(requireContext(), textColor))
    }

    private fun showExplanation(explanation: ShapExplanation) {
        binding.urlExplanationCard.visibility = View.VISIBLE
        binding.tvUrlEmbeddingsValue.text = formatContribution(explanation.embeddings_contribution)
        binding.tvUrlSentimentValue.text = formatContribution(explanation.sentiment_contribution)
        binding.tvUrlClickbaitValue.text = formatContribution(explanation.clickbait_contribution)
        updateContributionColor(binding.tvUrlEmbeddingsValue, explanation.embeddings_contribution)
        updateContributionColor(binding.tvUrlSentimentValue, explanation.sentiment_contribution)
        updateContributionColor(binding.tvUrlClickbaitValue, explanation.clickbait_contribution)
        animateProgressBar(binding.progressUrlEmbeddings, explanation.embeddings_contribution)
        animateProgressBar(binding.progressUrlSentiment, explanation.sentiment_contribution)
        animateProgressBar(binding.progressUrlClickbait, explanation.clickbait_contribution)
        binding.tvUrlExplanationSummary.text = generateExplanationSummary(explanation)
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
        binding.urlLoadingLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvUrlLoadingText.text = message
        val enableControls = !show
        binding.btnAnalyzeUrl.isEnabled = enableControls
        binding.btnUrlExplain.isEnabled = enableControls && lastExtractedTextValue.isNotEmpty()
        binding.btnUrlFeedback.isEnabled = enableControls && lastExtractedTextValue.isNotEmpty() && lastPredictionResult.isNotEmpty()
    }

    private fun hideAllCards() {
        binding.statusCard.visibility = View.GONE
        binding.urlResultsCard.visibility = View.GONE
        binding.urlExplanationCard.visibility = View.GONE
        binding.urlFeedbackCard.visibility = View.GONE
        binding.tvExtractedText.visibility = View.GONE
    }

    private fun clearResults() {
        binding.etNewsUrl.text?.clear()
        hideAllCards()
        lastAnalyzedUrl = ""
        lastExtractedTextValue = ""
        lastPredictionResult = ""
        lastConfidenceValue = 0
        currentAnalysisId = null
        currentShapValues = null
    }

    private fun handleConnectionError(e: Exception) {
        binding.statusCard.visibility = View.VISIBLE // Ensure status card is visible for error
        val errorMessage = when (e) {
            is ConnectException -> "Cannot connect to server. Check connection settings."
            is UnknownHostException -> "Server not found. Verify IP address in settings."
            is SocketTimeoutException -> "Connection timeout. Server might be slow."
            is HttpException -> "Server error: ${e.code()}"
            else -> "Network error: ${e.localizedMessage}"
        }
        binding.tvUrlStatus.text = "❌ $errorMessage" // Show error in status card
        binding.tvExtractedText.visibility = View.GONE
        showError(errorMessage) // Also show a toast
    }

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
