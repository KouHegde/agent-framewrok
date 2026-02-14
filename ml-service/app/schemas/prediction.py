"""
Pydantic request/response schemas for the ML Tool Prediction Service.
"""

from enum import Enum
from typing import Dict, List, Optional

from pydantic import BaseModel, Field


class TrainingPhase(str, Enum):
    BOOTSTRAP = "bootstrap"
    TRAINING = "training"
    HYBRID = "hybrid"
    ML_PRIMARY = "ml_primary"


class PredictionRequest(BaseModel):
    """Request for tool prediction."""
    query: str = Field(..., description="User query to analyze")
    context: Optional[str] = Field(None, description="Additional context")
    user_id: Optional[str] = Field(None, description="User ID")
    tenant_id: Optional[str] = Field(None, description="Tenant ID")
    session_id: Optional[str] = Field(None, description="Session ID")
    max_tools: int = Field(3, description="Maximum tools to return")
    min_confidence: float = Field(0.5, description="Minimum confidence threshold")
    include_reasoning: bool = Field(True, description="Include reasoning")


class PredictedTool(BaseModel):
    """A single predicted tool."""
    name: str
    category: Optional[str] = None
    description: Optional[str] = None
    confidence: float
    reason: Optional[str] = None
    success_rate: Optional[float] = None
    required_inputs: List[str] = []
    capabilities: List[str] = []


class PredictionResponse(BaseModel):
    """Response from tool prediction."""
    prediction_id: str
    query: str
    predicted_tools: List[PredictedTool]
    confidence: float
    prediction_method: str
    reasoning: Optional[str] = None
    prediction_time_ms: int
    model_version: Optional[str] = None


class EmbeddingRequest(BaseModel):
    """Request for embedding generation."""
    text: str = Field(..., description="Text to embed")


class EmbeddingResponse(BaseModel):
    """Response from embedding generation."""
    embedding: List[float]
    model: str
    dimensions: int


class TrainingRequest(BaseModel):
    """Request to trigger training."""
    training_run_id: str
    min_samples: int = Field(100, description="Minimum samples required")
    epochs: int = Field(3, description="Number of training epochs")
    batch_size: int = Field(32, description="Training batch size")
    eval_split: float = Field(0.2, description="Evaluation split ratio")


class TrainingResponse(BaseModel):
    """Response from training request."""
    status: str
    training_run_id: str
    samples_count: int
    message: Optional[str] = None
    metrics: Optional[Dict[str, float]] = None


class EvaluationRequest(BaseModel):
    """Request to evaluate model."""
    model_version: Optional[str] = None


class EvaluationResponse(BaseModel):
    """Response from model evaluation."""
    accuracy: float
    precision: float
    recall: float
    f1: float
    samples_evaluated: int
    model_version: str
    recommended_phase: str


class HealthResponse(BaseModel):
    """Health check response."""
    status: str
    version: str
    model_loaded: bool
    model_version: Optional[str]
    database_connected: bool
    current_phase: str
    training_samples: int


class UpdatePhaseRequest(BaseModel):
    """Request to update training phase."""
    phase: TrainingPhase
    accuracy: Optional[float] = None
    model_version: Optional[str] = None
