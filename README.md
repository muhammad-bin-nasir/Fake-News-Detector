
TruthLens - AI-Based Fake News Detector

TruthLens is a mobile application designed to detect fake news articles using AI-based text classification models. The app integrates a Kotlin-based front-end with a Python Flask server running a powerful ensemble of transformer models including DistilBERT, RoBERTa, DeBERTa, and others. The backend also provides explainability using SHAP, and allows users to flag incorrect predictions for future model improvement.

Features:
- Text-based news verification via mobile app
- Website/news URL scraping for content analysis
- Ensemble AI models for improved detection accuracy
- SHAP explainability for transparency in predictions
- Feedback mechanism to collect user-flagged data
- Plans for confidence threshold control and OCR support

Technologies Used:
- Frontend: Kotlin, XML, Jetpack Compose (Android)
- Backend: Python, Flask
- AI Models: DistilBERT + Logistic Regression, RoBERTa, DeBERTa-v3, BERT-Tiny-Fake-News, Pulk17
- Explainability: SHAP (KernelExplainer)
- Web Scraping: Newspaper3k (for URL content extraction)
- Communication: REST API (JSON)

Project Structure:
├── app/                            # Android app source code
├── server/                         # Python Flask server
│   ├── models/                     # Saved AI models (pkl files)
│   ├── explainability.py           # SHAP explanations
│   ├── scraper.py                  # Web scraping module
│   ├── server.py                   # Flask API endpoints
│   ├── feedback_collector.py       # Handles user feedback
│   └── requirements.txt            # Python dependencies
└── README.txt                      # Project documentation

How to Use:

1. Clone the repository:
git clone https://github.com/muhammad-bin-nasir/Fake-News-Detector.git
cd Fake-News-Detector/server

2. Set up and activate a virtual environment (optional but recommended):
python -m venv venv
venv\Scripts\activate   # On Windows
source venv/bin/activate  # On Linux/Mac

3. Install server dependencies:
pip install -r requirements.txt

4. Run the Flask server:
python server.py

5. In Android Studio:
- Open the /app directory as an Android project.
- Connect an Android device or emulator.
- Build and run the app.

6. Using the App:
- Enter a news text OR paste a URL to verify its authenticity.
- View prediction result (Fake/Real) along with confidence and SHAP-based explanation.
- Optionally, flag incorrect predictions to help improve the model.

7. To add more AI models or update existing ones:
- Save the trained model in /models
- Modify server.py to load and include the new model in the ensemble logic.

8. Future Features (Planned):
- OCR (Handwriting Recognition) for verifying printed news
- GUI threshold control slider for confidence levels
- Continuous learning pipeline using flagged feedback

License:
This project is licensed under the MIT License.

Acknowledgements:
- OpenAI (for DistilBERT, RoBERTa)
- HuggingFace Transformers
- SHAP Explainability Library
- Newspaper3k (for scraping)
