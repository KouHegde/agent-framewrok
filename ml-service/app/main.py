"""
ML Tool Prediction Service - FastAPI Application

Bootstrap Training Strategy:
1. BOOTSTRAP: Java service uses LLM, stores predictions as training data
2. TRAINING: This service trains DistilBERT from collected samples
3. HYBRID: Model serves predictions, LLM fallback for low confidence
4. ML_PRIMARY: Model serves most predictions, LLM for edge cases

Goal: Achieve 80%+ accuracy to reduce LLM token costs.
"""

import logging
import os

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.models.classifier import load_model
from app.models.embedder import generate_embedding, load_embedder
from app.models.state import model_state
from app.schemas import (
    EmbeddingRequest,
    EmbeddingResponse,
    EvaluationRequest,
    EvaluationResponse,
    HealthResponse,
    PredictionRequest,
    PredictionResponse,
    TrainingRequest,
    TrainingResponse,
    UpdatePhaseRequest,
)
from app.services.database import get_training_status, update_training_status
from app.services.prediction import PredictionService
from app.services.training import TrainingService

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


# =====================================================
# FastAPI Application
# =====================================================

app = FastAPI(
    title="ML Tool Prediction Service",
    description="Bootstrap Training: LLM as teacher -> DistilBERT student",
    version="0.2.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# =====================================================
# API Endpoints
# =====================================================

@app.get("/health", response_model=HealthResponse)
async def health_check():
    """Health check with training status."""
    status = await get_training_status()

    return HealthResponse(
        status="healthy",
        version="0.2.0",
        model_loaded=model_state.is_loaded,
        model_version=model_state.model_version,
        database_connected=model_state.db_connected,
        current_phase=status.get("current_phase", "bootstrap"),
        training_samples=status.get("total_samples", 0),
    )


@app.post("/predict", response_model=PredictionResponse)
async def predict_tools(request: PredictionRequest):
    """
    Predict tools using the trained classifier.

    Returns low confidence if model is not loaded, signaling
    the Java service to fall back to LLM.
    """
    return PredictionService.predict(request)


@app.post("/embed", response_model=EmbeddingResponse)
async def generate_embedding_endpoint(request: EmbeddingRequest):
    """Generate embedding vector for text."""
    logger.info(f"Embedding request: {request.text[:50]}...")

    embedding = generate_embedding(request.text)

    return EmbeddingResponse(
        embedding=embedding,
        model=settings.embedding_model,
        dimensions=len(embedding),
    )


@app.post("/train", response_model=TrainingResponse)
async def trigger_training(request: TrainingRequest):
    """
    Trigger model training from accumulated LLM predictions.

    Training runs in background. Poll /train/status/{run_id} for progress.
    """
    logger.info(f"Training request: {request.training_run_id}")

    # Check if we have enough samples
    status = await get_training_status()
    samples_count = status.get("total_samples", 0)

    if samples_count < request.min_samples:
        return TrainingResponse(
            status="insufficient_data",
            training_run_id=request.training_run_id,
            samples_count=samples_count,
            message=f"Need at least {request.min_samples} samples, have {samples_count}",
        )

    # Start training in background
    # background_tasks.add_task(
    #     TrainingService.run_training_pipeline,
    #     request.training_run_id,
    #     request.epochs,
    #     request.batch_size,
    #     request.eval_split
    # )

    return TrainingResponse(
        status="not_implemented",
        training_run_id=request.training_run_id,
        samples_count=samples_count,
        message="Training pipeline not yet implemented. Samples are being collected.",
    )


@app.post("/evaluate", response_model=EvaluationResponse)
async def evaluate_model(request: EvaluationRequest = None):
    """
    Evaluate the current model on held-out samples.

    Returns metrics and recommended phase based on accuracy.
    """
    if not model_state.is_loaded:
        raise HTTPException(status_code=400, detail="No model loaded")

    # TODO: Implement actual evaluation
    return EvaluationResponse(
        accuracy=0.0,
        precision=0.0,
        recall=0.0,
        f1=0.0,
        samples_evaluated=0,
        model_version=model_state.model_version or "none",
        recommended_phase="bootstrap",
    )


@app.post("/phase")
async def update_phase(request: UpdatePhaseRequest):
    """
    Update the training phase in database.

    Called by Java service after evaluation confirms accuracy thresholds.
    """
    await update_training_status(
        phase=request.phase.value,
        accuracy=request.accuracy,
        model_version=request.model_version,
    )

    return {
        "status": "updated",
        "phase": request.phase.value,
        "accuracy": request.accuracy,
    }


@app.get("/models")
async def list_models():
    """List available model versions."""
    model_dir = settings.model_path
    models = []

    if os.path.exists(model_dir):
        for item in os.listdir(model_dir):
            item_path = os.path.join(model_dir, item)
            if os.path.isdir(item_path):
                models.append({
                    "version": item,
                    "path": item_path,
                    "is_active": item == model_state.model_version,
                })

    return {
        "models": models,
        "active_model": model_state.model_version,
        "model_loaded": model_state.is_loaded,
    }


@app.get("/stats")
async def get_statistics():
    """Get prediction and training statistics."""
    status = await get_training_status()

    return {
        "current_phase": status.get("current_phase", "bootstrap"),
        "total_samples": status.get("total_samples", 0),
        "current_accuracy": status.get("current_accuracy", 0.0),
        "model_loaded": model_state.is_loaded,
        "model_version": model_state.model_version,
        "min_samples_for_training": settings.min_samples_for_training,
    }


# =====================================================
# Startup/Shutdown Events
# =====================================================

@app.on_event("startup")
async def startup_event():
    """Initialize service on startup."""
    logger.info("Starting ML Tool Prediction Service...")
    logger.info(f"Database URL: {settings.database_url[:30]}...")
    logger.info(f"Model path: {settings.model_path}")

    # Try to load existing model
    model_path = os.path.join(settings.model_path, "latest")
    if os.path.exists(model_path):
        logger.info(f"Found model at {model_path}, loading...")
        if load_model(model_path):
            model_state.is_loaded = True
            model_state.model_version = "latest"
            logger.info("Model loaded successfully")
    else:
        logger.info("No trained model found. Running in bootstrap mode.")
        logger.info("System will collect training data from LLM predictions.")

    # Load embedder
    load_embedder()

    logger.info("ML Service started")


@app.on_event("shutdown")
async def shutdown_event():
    """Cleanup on shutdown."""
    logger.info("Shutting down ML Tool Prediction Service...")


# =====================================================
# Run with: uvicorn app.main:app --reload --port 8001
# =====================================================
