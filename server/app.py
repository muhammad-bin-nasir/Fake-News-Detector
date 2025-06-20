import os
import socket

from flask import Flask, request, jsonify
from flask_cors import CORS

import torch
from transformers import AutoTokenizer, AutoModel
from textblob import TextBlob
import joblib
import numpy as np
import shap
from transformers import AutoTokenizer, AutoModelForSequenceClassification
from transformers import DistilBertTokenizerFast, DistilBertForSequenceClassification
import torch
import torch.nn.functional as F
from bs4 import BeautifulSoup
import requests
import re
import logging

# -----------------------------------------------------------------------------
# Load ML artifacts
# -----------------------------------------------------------------------------
MODEL_DIR = os.path.dirname(os.path.abspath(__file__))

# Logistic Regression classifier
LOGREG_PATH = os.path.join(MODEL_DIR, "logistic_model.pkl")
logreg_model = joblib.load(LOGREG_PATH)

# StandardScaler for 770-dimensional feature vector
SCALER_PATH = os.path.join(MODEL_DIR, "bert_scaler.pkl")
scaler = joblib.load(SCALER_PATH)

# DistilBERT tokenizer & model (CPU-only)
TOKENIZER_NAME = "distilbert-base-uncased"
tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_NAME)
bert_model = AutoModel.from_pretrained(TOKENIZER_NAME)
bert_model.eval()
bert_model.to("cpu")

hf_tokenizer = AutoTokenizer.from_pretrained("XSY/albert-base-v2-fakenews-discriminator")
hf_model = AutoModelForSequenceClassification.from_pretrained("XSY/albert-base-v2-fakenews-discriminator")

# Pretrained model for fake news detection
PRETRAINED_MODEL_NAME = "jy46604790/Fake-News-Bert-Detect"

pretrained_tokenizer = AutoTokenizer.from_pretrained(PRETRAINED_MODEL_NAME)
pretrained_model = AutoModelForSequenceClassification.from_pretrained(PRETRAINED_MODEL_NAME)
pretrained_model.eval()
pretrained_model.to("cpu")

#roberta pretrained

roberta_tokenizer = AutoTokenizer.from_pretrained("hamzab/roberta-fake-news-classification")
roberta_model = AutoModelForSequenceClassification.from_pretrained("hamzab/roberta-fake-news-classification")
roberta_device = 'cuda' if torch.cuda.is_available() else 'cpu'
roberta_model.to(roberta_device)
roberta_model.eval()

# Pulka

pulk17_tokenizer = AutoTokenizer.from_pretrained("Pulk17/Fake-News-Detection")
pulk17_model = AutoModelForSequenceClassification.from_pretrained("Pulk17/Fake-News-Detection")
pulk17_device = 'cuda' if torch.cuda.is_available() else 'cpu'
pulk17_model.to(pulk17_device)
pulk17_model.eval()

# dhruv

#dhruv_tokenizer = AutoTokenizer.from_pretrained("dhruv-bert-fake-news-classifier")
#dhruv_model = AutoModelForSequenceClassification.from_pretrained("dhruv-bert-fake-news-classifier")
#dhruv_device = 'cuda' if torch.cuda.is_available() else 'cpu'
#dhruv_model.to(dhruv_device)
#dhruv_model.eval()

# Load DeBERTa-v3-base fine-tuned 
deberta_tokenizer = AutoTokenizer.from_pretrained("microsoft/deberta-v3-base", use_fast=False)
deberta_model = AutoModelForSequenceClassification.from_pretrained("microsoft/deberta-v3-base")



# Clickbait phrase list (simple heuristic)
CLICKBAIT_PHRASES = [
    "you won't believe", "shocking", "doctors hate", "miracle cure",
    "this one weird trick", "everything you know is wrong", "the truth about",
    "what happens next", "top 10", "unbelievable"
]


# -----------------------------------------------------------------------------
# Feature engineering helpers
# -----------------------------------------------------------------------------
@torch.no_grad()
def _extract_embeddings(text: str) -> np.ndarray:
    """Return mean-pooled DistilBERT embeddings (768-D)."""
    inputs = tokenizer(text, return_tensors="pt", max_length=512,
                       padding="max_length", truncation=True)
    outputs = bert_model(**inputs)
    embeddings = outputs.last_hidden_state.mean(dim=1).squeeze().numpy()
    return embeddings  # shape: (768,)

def predict_roberta(text: str) -> float:
    # If you want, split into title/content:
    lines = text.split('\n', 1)
    title = lines[0] if lines else ""
    content = lines[1] if len(lines) > 1 else ""
    input_str = "<title>" + title + "<content>" + content + "<end>"
    
    inputs = roberta_tokenizer(input_str, return_tensors="pt", truncation=True, padding=True, max_length=512)
    inputs = {k: v.to(roberta_device) for k, v in inputs.items()}
    
    with torch.no_grad():
        outputs = roberta_model(**inputs)
    probs = torch.nn.functional.softmax(outputs.logits, dim=1)[0]
    return probs[1].item()  # Probability of "REAL"


def predict_pulk17(text: str) -> float:
    inputs = pulk17_tokenizer(text, return_tensors="pt", truncation=True, padding=True, max_length=512)
    inputs = {k: v.to(pulk17_device) for k, v in inputs.items()}
    with torch.no_grad():
        outputs = pulk17_model(**inputs)
    probs = F.softmax(outputs.logits, dim=1)[0]
    return probs[1].item()

def predict_deberta(text):
    inputs = deberta_tokenizer(text, return_tensors="pt", truncation=True, padding=True)
    with torch.no_grad():
        outputs = deberta_model(**inputs)
        logits = outputs.logits
        probs = torch.softmax(logits, dim=1).cpu().numpy()[0]
    
    real_prob = probs[1]  # Assuming label 1 = REAL, label 0 = FAKE
    return real_prob


#def predict_dhruv(text: str) -> float:
#    inputs = dhruv_tokenizer(text, return_tensors="pt", truncation=True, padding=True, max_length=512)
#    inputs = {k: v.to(dhruv_device) for k, v in inputs.items()}
#    with torch.no_grad():
#        outputs = dhruv_model(**inputs)
#    probs = F.softmax(outputs.logits, dim=1)[0]
#    return probs[1].item()

def pretrained_model_proba(text):
    inputs = hf_tokenizer(text, return_tensors="pt", truncation=True, max_length=512)
    with torch.no_grad():
        outputs = hf_model(**inputs)
        probs = torch.nn.functional.softmax(outputs.logits, dim=-1)
    # Label 1 = Real, Label 0 = Fake
    return probs[0][1].item()  # Return probability of REAL



def _sentiment_score(text: str) -> float:
    return TextBlob(text).sentiment.polarity

def _clickbait_flag(text: str) -> int:
    lower = text.lower()
    return int(any(phrase in lower for phrase in CLICKBAIT_PHRASES))

def extract_features(text: str) -> np.ndarray:
    text_clean = text.lower()
    emb = _extract_embeddings(text_clean)
    sent = _sentiment_score(text_clean)
    cb = _clickbait_flag(text_clean)

    emb_scaled = scaler.transform(emb.reshape(1, -1))
    features = np.hstack([emb_scaled.flatten(), [sent], [cb]])
    return features.reshape(1, -1)

# -----------------------------------------------------------------------------
# Prediction wrapper
# -----------------------------------------------------------------------------
def predict_fake_news(text: str,
                      weight_ours=0.2,
                      weight_pretrained=0.2,
                      weight_roberta=0.2,
                      weight_pulk17=0.2,
                      weight_deberta=0.2):
    feats = extract_features(text)

    ours_real = logreg_model.predict_proba(feats)[0][1]      
    pretrained_real = pretrained_model_proba(text)             
    roberta_real = predict_roberta(text)                        
    pulk17_real = predict_pulk17(text)                          
    deberta_real = predict_deberta(text)

    # 🔴 Short-circuit: If any model is 100% confident for REAL, return immediately
    if (ours_real >= 0.995 or pretrained_real >= 0.995 or roberta_real >= 0.995 or pulk17_real >= 0.995 or deberta_real >= 0.995):
        prediction = "real"
        confidence = 100
        print("[PREDICT] One or more models predicted REAL with >=99.5% confidence. Forcing REAL prediction.")
        return prediction, confidence, feats, 1.0


    # 🟢 Otherwise, do normal weighted average
    combined_real = (
        weight_ours * ours_real +
        weight_pretrained * pretrained_real +
        weight_roberta * roberta_real +
        weight_pulk17 * pulk17_real +
        weight_deberta * deberta_real
    )

    prediction = "real" if combined_real >= 0.5 else "fake"
    confidence = int(max(combined_real, 1 - combined_real) * 100)

    # Logging
    print(f"[PREDICT] Ours: {ours_real:.3f}, Pretrained: {pretrained_real:.3f}, RoBERTA: {roberta_real:.3f}, Pulk17: {pulk17_real:.3f}, DeBERTa: {deberta_real:.3f}")
    print(f"[PREDICT] Combined REAL: {combined_real:.3f} → Prediction: {prediction.upper()} (Confidence: {confidence}%)")

    return prediction, confidence, feats, combined_real




# -----------------------------------------------------------------------------
# Flask application
# -----------------------------------------------------------------------------

logging.basicConfig(level=logging.INFO)

app = Flask(__name__)
CORS(app)

@app.route("/health", methods=["GET"])
def health():
    server_ip = socket.gethostbyname(socket.gethostname())
    return jsonify({
        "status": "Server is running!",
        "server_ip": server_ip,
        "message": "✅ Connection successful!",
        "models_loaded": {
            "distilbert": True,
            "logistic": logreg_model is not None,
            "scaler": scaler is not None
        }
    })

@app.route("/test", methods=["GET", "POST"])
def test():
    if request.method == "GET":
        return jsonify({
            "message": "✅ GET test successful!",
            "method": "GET",
            "server_status": "running"
        })

    data = request.get_json(force=True, silent=True) or {}
    return jsonify({
        "message": "✅ POST test successful!",
        "method": "POST",
        "received_data": data,
        "server_status": "running"
    })

@app.route("/predict", methods=["POST"])
def predict():
    data = request.get_json(force=True, silent=True) or {}
    text = (data.get("text") or "").strip()

    if not text:
        return jsonify({
            "prediction": "error",
            "confidence": 0,
            "message": "Text field is required"
        }), 400
    if len(text) < 10:
        return jsonify({
            "prediction": "error",
            "confidence": 0,
            "message": "Text must be at least 10 characters long"
        }), 400

    try:
        pred, conf = predict_fake_news(text)[:2]
        return jsonify({
            "prediction": pred,
            "confidence": conf,
            "message": f"✅ Analysis complete: {pred.upper()} news detected"
        })
    except Exception as exc:
        import traceback
        traceback.print_exc()
        return jsonify({
            "prediction": "error",
            "confidence": 0,
            "message": f"Analysis failed: {str(exc)}"
        }), 500


@app.route("/feedback", methods=["POST"])
def feedback():
    try:
        print("=== FEEDBACK ENDPOINT CALLED ===")
        data = request.get_json(force=True, silent=True) or {}
        
        text = data.get("text", "").strip()
        prediction = data.get("prediction", "").strip()
        confidence = data.get("confidence", 0)
        label = data.get("label", "").strip()
        
        print(f"Feedback received:")
        print(f"  Text length: {len(text)}")
        print(f"  Model prediction: {prediction}")
        print(f"  Model confidence: {confidence}%")
        print(f"  User correction: {label}")
        
        if not text or not prediction or not label:
            return jsonify({
                "message": "❌ Missing required fields",
                "status": "error"
            }), 400
        
        # Here you can save the feedback to a database or file
        # For now, we'll just log it and return success
        
        # Example: Save to a CSV file or database
        import csv
        import os
        from datetime import datetime
        
        feedback_file = os.path.join(MODEL_DIR, "feedback.csv")
        
        # Create file with headers if it doesn't exist
        if not os.path.exists(feedback_file):
            with open(feedback_file, 'w', newline='', encoding='utf-8') as f:
                writer = csv.writer(f)
                writer.writerow(['timestamp', 'text', 'model_prediction', 'confidence', 'user_label'])
        
        # Append feedback
        with open(feedback_file, 'a', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow([
                datetime.now().isoformat(),
                text[:500],  # Truncate long text
                prediction,
                confidence,
                label
            ])
        
        print(f"✅ Feedback saved to {feedback_file}")
        
        return jsonify({
            "message": "✅ Thank you for your feedback! This helps improve our model.",
            "status": "success"
        })
        
    except Exception as e:
        import traceback
        print(f"ERROR in feedback endpoint: {str(e)}")
        traceback.print_exc()
        return jsonify({
            "message": f"❌ Failed to save feedback: {str(e)}",
            "status": "error"
        }), 500


@app.route("/explain", methods=["POST"])
def explain():
    try:
        print("=== EXPLAIN ENDPOINT CALLED ===")
        data = request.get_json(force=True, silent=True) or {}
        text = (data.get("text") or "").strip()
        print(f"Received text length: {len(text)}")

        if not text or len(text) < 10:
            print("ERROR: Invalid input text")
            return jsonify({
                "error": "Invalid input text"
            }), 400

        # Get prediction and features
        pred, conf, features, pred_value = predict_fake_news(text)
        print(f"Prediction: {pred}, Confidence: {conf}")
        
        # Create background
        background = np.zeros((1, features.shape[1]))
        print("Creating SHAP explainer...")

        # Create explainer
        explainer = shap.Explainer(logreg_model.predict_proba, background)
        
        # Set max_evals dynamically
        max_evals = 2 * features.shape[1] + 1
        print("Calculating SHAP values...")
        shap_values = explainer(features, max_evals=max_evals)

        # Class index
        class_idx = 1 if pred == "real" else 0

        # Extract contributions
        embeddings_contribution = float(np.sum(shap_values.values[0, :768, class_idx]))
        sentiment_contribution = float(shap_values.values[0, 768, class_idx])
        clickbait_contribution = float(shap_values.values[0, 769, class_idx])
        base_value = float(shap_values.base_values[0, class_idx])

        print(f"Embeddings contribution: {embeddings_contribution}")
        print(f"Sentiment contribution: {sentiment_contribution}")
        print(f"Clickbait contribution: {clickbait_contribution}")
        
        # Final response
        response = {
            "explanation": {
                "embeddings_contribution": embeddings_contribution,
                "sentiment_contribution": sentiment_contribution,
                "clickbait_contribution": clickbait_contribution,
                "base_value": base_value,
                "prediction_value": float(pred_value)
            },
            "prediction": pred,
            "confidence": conf
        }

        print("Sending explanation response")
        return jsonify(response)

    except Exception as e:
        import traceback
        print(f"ERROR in explain endpoint: {str(e)}")
        traceback.print_exc()
        return jsonify({
            "error": str(e)
        }), 500
    
@app.route('/scrape', methods=['POST'])
def scrape_article():
    try:
        data = request.get_json()
        url = data.get('url', '')

        if not url:
            logging.warning("No URL provided in request.")
            return jsonify({
                "text": "",
                "title": "",
                "status": "error",
                "message": "No URL provided."
            }), 400

        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
                          'AppleWebKit/537.36 (KHTML, like Gecko) '
                          'Chrome/91.0.4472.124 Safari/537.36'
        }

        logging.info(f"Fetching URL: {url}")
        response = requests.get(url, headers=headers, timeout=10)

        if response.status_code != 200:
            logging.error(f"Failed to fetch URL: {url} with status code {response.status_code}")
            return jsonify({
                "text": "",
                "title": "",
                "status": "error",
                "message": f"Failed to fetch URL: HTTP {response.status_code}"
            }), 400

        soup = BeautifulSoup(response.content, 'html.parser')

        # Extract title
        title_tag = soup.find('title')
        title = title_tag.get_text(strip=True) if title_tag else "No Title Found"

        # Remove unwanted elements
        for tag in soup(['script', 'style', 'header', 'footer', 'nav', 'aside', 'noscript', 'iframe']):
            tag.decompose()

        selectors = ['article', '.article-content', '.post-content', '#article', '#content']

        article_text = ""
        for selector in selectors:
            selected = soup.select_one(selector)
            if selected:
                article_text = selected.get_text(separator=' ', strip=True)
                if len(article_text) > 200:
                    break

        if not article_text or len(article_text) < 200:
            logging.info("Using fallback: extracting all <p> tags.")
            paragraphs = soup.find_all('p')
            article_text = ' '.join([p.get_text(separator=' ', strip=True) for p in paragraphs])

        article_text = re.sub(r'\s+', ' ', article_text).strip()

        if not article_text or len(article_text) < 100:
            logging.warning("No substantial article content found.")
            return jsonify({
                "text": "",
                "title": title,
                "status": "error",
                "message": "No article content found."
            }), 400

        logging.info(f"Successfully extracted {len(article_text)} characters from article.")

        return jsonify({
            "text": article_text,
            "title": title,
            "status": "success",
            "message": f"Successfully extracted {len(article_text)} characters"
        }), 200

    except requests.exceptions.RequestException as e:
        logging.error(f"Request error: {str(e)}")
        return jsonify({
            "text": "",
            "title": "",
            "status": "error",
            "message": f"Failed to fetch URL: {str(e)}"
        }), 400
    except Exception as e:
        logging.exception("Unexpected error occurred during scraping.")
        return jsonify({
            "text": "",
            "title": "",
            "status": "error",
            "message": f"Server error: {str(e)}"
        }), 500    



# -----------------------------------------------------------------------------
# Entrypoint
# -----------------------------------------------------------------------------
if __name__ == "__main__":
    ip_address = socket.gethostbyname(socket.gethostname())
    print(f"🔗 Server starting... Visit: http://{ip_address}:5000/")
    app.run(debug=False, host="0.0.0.0", port=5000, threaded=True)