"""
Global model state holder.
"""

from typing import List, Optional


class ModelState:
    """Holds the loaded model and related state."""
    def __init__(self):
        self.model = None
        self.tokenizer = None
        self.embedder = None
        self.tool_labels: List[str] = []
        self.model_version: Optional[str] = None
        self.is_loaded: bool = False
        self.db_connected: bool = False


# Singleton instance shared across the application
model_state = ModelState()
