# ML Tool Prediction Service

Python microservice for ML-based MCP tool prediction using classifier fine-tuning.

## Bootstrap Training Strategy

This service implements a **"Teacher-Student"** approach to progressively reduce LLM costs:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Bootstrap Training Flow                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   PHASE 1: BOOTSTRAP (No trained model)                                     │
│   ══════════════════════════════════════                                    │
│   • Java service uses keyword + LLM for predictions                         │
│   • Every LLM prediction is stored as training data                         │
│   • ML service returns low confidence → triggers LLM fallback               │
│   • Goal: Collect 100+ training samples                                     │
│                                                                             │
│   PHASE 2: TRAINING (Samples collected)                                     │
│   ════════════════════════════════════                                      │
│   • Trigger training via POST /train                                        │
│   • Fine-tune DistilBERT on collected samples                               │
│   • Evaluate accuracy on held-out set                                       │
│   • If accuracy ≥ 80%, transition to HYBRID                                 │
│                                                                             │
│   PHASE 3: HYBRID (80-90% accuracy)                                         │
│   ════════════════════════════════                                          │
│   • ML predictions used when confidence ≥ threshold                         │
│   • Low confidence predictions fall back to LLM                             │
│   • LLM predictions still collected for continuous improvement              │
│   • Significant token savings start here                                    │
│                                                                             │
│   PHASE 4: ML_PRIMARY (>90% accuracy)                                       │
│   ══════════════════════════════════                                        │
│   • ML serves most predictions                                              │
│   • LLM only for edge cases (very low confidence)                           │
│   • Maximum token savings achieved                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Overview

This service provides intelligent tool prediction using a fine-tuned classifier model.
It integrates with the Java agent-framework service to provide:

1. **Tool Prediction**: Given a user query, predict the most relevant MCP tools
2. **Model Training**: Train DistilBERT classifier from LLM predictions
3. **Embedding Generation**: Generate query embeddings for similarity search
4. **Phase Management**: Track and transition between training phases

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     ML Service (Python)                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────────┐   │
│  │   FastAPI    │    │   Trainer    │    │    Embedder      │   │
│  │   /predict   │    │   /train     │    │  /embed          │   │
│  └──────┬───────┘    └──────┬───────┘    └────────┬─────────┘   │
│         │                    │                     │             │
│         v                    v                     v             │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    Model Manager                          │   │
│  │  - Load/Save ONNX models                                  │   │
│  │  - Tokenization                                           │   │
│  │  - Inference                                              │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                   │
│                              v                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    PostgreSQL + pgvector                  │   │
│  │  - Training samples                                       │   │
│  │  - Tool embeddings                                        │   │
│  │  - Model versions                                         │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## API Endpoints

### POST /predict
Predict tools for a query.

**Request:**
```json
{
  "query": "Create a Jira ticket for the bug in login page",
  "max_tools": 3,
  "min_confidence": 0.5
}
```

**Response:**
```json
{
  "prediction_id": "uuid",
  "predicted_tools": [
    {
      "name": "mcp_jira_call_jira_rest_api",
      "confidence": 0.95,
      "reason": "High similarity to Jira ticket creation queries"
    }
  ],
  "confidence": 0.95,
  "prediction_method": "ml_classifier",
  "reasoning": "Classifier identified Jira-related intent with high confidence"
}
```

### POST /embed
Generate embedding for a query.

**Request:**
```json
{
  "text": "Create a Jira ticket"
}
```

**Response:**
```json
{
  "embedding": [0.123, -0.456, ...],
  "model": "sentence-transformers/all-MiniLM-L6-v2"
}
```

### POST /train
Trigger model training.

**Request:**
```json
{
  "training_run_id": "run-001",
  "min_samples": 100,
  "epochs": 3
}
```

**Response:**
```json
{
  "status": "started",
  "training_run_id": "run-001",
  "samples_count": 150
}
```

### GET /health
Health check endpoint.

## Setup

### Prerequisites
- Python 3.10+
- PostgreSQL with pgvector extension

### Installation

```bash
cd ml-service
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
```

### Configuration

Set environment variables:
```bash
export DATABASE_URL=postgresql://agent_user:agent_pass@localhost:5432/agent_framework
export MODEL_PATH=/app/models
export MODEL_NAME=tool-selector-v1
export PORT=8001
```

### Running

```bash
# Development
uvicorn app.main:app --reload --port 8001

# Production
uvicorn app.main:app --host 0.0.0.0 --port 8001 --workers 4
```

### Docker

```bash
docker build -t agent-framework-ml .
docker run -p 8001:8001 -e DATABASE_URL=... agent-framework-ml
```

## Model Training

### Training Pipeline

1. **Data Collection**: Training samples are collected from user feedback via the Java service
2. **Data Preprocessing**: Queries are tokenized and tools are multi-hot encoded
3. **Model Training**: Fine-tune DistilBERT classifier
4. **Evaluation**: Calculate precision, recall, F1 for each tool
5. **Deployment**: Export to ONNX and reload

### Training Script

```bash
python scripts/train_model.py --epochs 3 --batch-size 32
```

## Model Architecture

The classifier is based on DistilBERT with a multi-label classification head:

```
Input Text
    ↓
DistilBERT Encoder (frozen or fine-tuned)
    ↓
Pooling Layer (CLS token)
    ↓
Dense Layer (768 → 256)
    ↓
Dropout (0.3)
    ↓
Output Layer (256 → num_tools)
    ↓
Sigmoid Activation
    ↓
Tool Probabilities
```

## File Structure

```
ml-service/
├── app/
│   ├── __init__.py
│   ├── main.py              # FastAPI application
│   ├── models/
│   │   ├── __init__.py
│   │   ├── classifier.py    # Tool classifier
│   │   └── embedder.py      # Embedding generator
│   ├── services/
│   │   ├── __init__.py
│   │   ├── prediction.py    # Prediction service
│   │   └── training.py      # Training service
│   └── schemas/
│       ├── __init__.py
│       └── prediction.py    # Pydantic schemas
├── scripts/
│   ├── train_model.py       # Training script
│   └── export_onnx.py       # ONNX export
├── models/                  # Saved models directory
├── tests/
├── Dockerfile
├── requirements.txt
└── README.md
```

## Requirements

See `requirements.txt`:
- fastapi
- uvicorn
- torch
- transformers
- sentence-transformers
- psycopg2-binary
- pgvector
- pydantic
- numpy
- scikit-learn

## TODO

- [ ] Implement FastAPI application structure
- [ ] Add DistilBERT classifier model
- [ ] Add sentence-transformers embedder
- [ ] Implement training pipeline
- [ ] Add ONNX export functionality
- [ ] Add model versioning
- [ ] Add metrics and monitoring
- [ ] Add unit tests
- [ ] Add integration tests with Java service
