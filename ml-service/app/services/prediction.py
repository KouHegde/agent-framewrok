"""
Prediction service for tool selection.

Orchestrates model inference and builds prediction responses.
"""

import logging
import time
import uuid
from typing import Optional

from app.models.classifier import predict_with_model
from app.models.state import model_state
from app.schemas import PredictedTool, PredictionRequest, PredictionResponse

logger = logging.getLogger(__name__)


class PredictionService:
    """Service for generating tool predictions from user queries."""

    @staticmethod
    def predict(request: PredictionRequest) -> PredictionResponse:
        """
        Predict tools for the given request.

        If the model is not loaded, returns a low-confidence response
        to signal the Java service to fall back to LLM.

        Args:
            request: The prediction request with query and parameters.

        Returns:
            PredictionResponse with predicted tools and confidence scores.
        """
        start_time = time.time()
        logger.info(f"Prediction request: {request.query[:50]}...")

        prediction_id = str(uuid.uuid4())

        # If model not loaded, return low confidence to trigger LLM fallback
        if not model_state.is_loaded:
            prediction_time_ms = int((time.time() - start_time) * 1000)
            return PredictionResponse(
                prediction_id=prediction_id,
                query=request.query,
                predicted_tools=[],
                confidence=0.0,
                prediction_method="ml_classifier",
                reasoning=(
                    "Model not yet trained. System is in bootstrap phase "
                    "collecting training data from LLM."
                ),
                prediction_time_ms=prediction_time_ms,
                model_version=None,
            )

        # Run inference
        predictions = predict_with_model(request.query, request.max_tools)

        if not predictions:
            prediction_time_ms = int((time.time() - start_time) * 1000)
            return PredictionResponse(
                prediction_id=prediction_id,
                query=request.query,
                predicted_tools=[],
                confidence=0.0,
                prediction_method="ml_classifier",
                reasoning="Model returned no predictions",
                prediction_time_ms=prediction_time_ms,
                model_version=model_state.model_version,
            )

        # Build response with tools above confidence threshold
        predicted_tools = []
        for tool_name, confidence in predictions:
            if confidence >= request.min_confidence:
                predicted_tools.append(PredictedTool(
                    name=tool_name,
                    confidence=confidence,
                    reason=f"ML classifier confidence: {confidence:.2%}",
                ))

        overall_confidence = predictions[0][1] if predictions else 0.0
        prediction_time_ms = int((time.time() - start_time) * 1000)

        return PredictionResponse(
            prediction_id=prediction_id,
            query=request.query,
            predicted_tools=predicted_tools,
            confidence=overall_confidence,
            prediction_method="ml_classifier",
            reasoning=(
                f"DistilBERT classifier prediction with "
                f"{len(predicted_tools)} tools above threshold"
            ),
            prediction_time_ms=prediction_time_ms,
            model_version=model_state.model_version,
        )
