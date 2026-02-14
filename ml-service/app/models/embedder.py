"""
Embedding model operations.

Handles loading the sentence-transformers model and generating
query embeddings for similarity search.
"""

import logging
from typing import List

from app.config import settings
from app.models.state import model_state

logger = logging.getLogger(__name__)


def load_embedder():
    """
    Load the sentence-transformers embedding model.

    Uses the model specified in settings.embedding_model.
    """
    # TODO: Implement actual loading
    # from sentence_transformers import SentenceTransformer
    # model_state.embedder = SentenceTransformer(settings.embedding_model)
    logger.info("Embedder loading not yet implemented")


def generate_embedding(text: str) -> List[float]:
    """
    Generate an embedding vector for the given text.

    Args:
        text: Input text to embed.

    Returns:
        List of floats representing the embedding vector (384 dimensions for MiniLM).
    """
    # TODO: Implement actual embedding
    # return model_state.embedder.encode(text).tolist()
    return [0.0] * 384
