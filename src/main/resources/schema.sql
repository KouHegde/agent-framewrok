CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================================================
-- AGENTS TABLE
-- Stores agent definitions with configuration for Python AgentBrain
-- =====================================================
CREATE TABLE IF NOT EXISTS agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Core Info
    name TEXT NOT NULL,                          -- Display name (not unique - multiple agents can have same name)
    description TEXT,                            -- What the agent does
    goal TEXT,                                   -- Agent's purpose/objective
    
    -- Lookup Key (for deduplication)
    allowed_tools TEXT NOT NULL,                 -- Sorted comma-separated tools (UNIQUE - one agent per tool combo)
    
    -- Full Spec (JSON)
    agent_spec TEXT,                             -- Full AgentSpec as JSON
    
    -- Ownership
    created_by TEXT,                             -- Who created the agent
    tenant_id TEXT,                              -- Organization/workspace
    
    -- RAG Configuration (for Python AgentBrain)
    rag_scope TEXT,                              -- Comma-separated: "policy_docs,tickets,audit_logs"
    
    -- Reasoning Configuration
    reasoning_style TEXT DEFAULT 'direct',       -- direct, step-by-step, comparative, analytical
    temperature NUMERIC(3,2) DEFAULT 0.30,       -- 0.00 to 1.00
    
    -- Retriever Configuration
    retriever_type TEXT DEFAULT 'simple',        -- simple, multi-query, ensemble
    retriever_k INTEGER DEFAULT 5,               -- Number of chunks to retrieve
    
    -- Execution Configuration
    execution_mode TEXT DEFAULT 'static',        -- static, dynamic
    permissions TEXT DEFAULT 'read_only',        -- Comma-separated: "read_only" or "read_only,write"
    
    -- Python AgentBrain Response (stored after creation)
    system_prompt TEXT,                          -- Generated system prompt
    max_steps INTEGER DEFAULT 6,                 -- Max execution steps
    brain_agent_id TEXT,                         -- ID returned from Python service
    
    -- Status
    status TEXT DEFAULT 'active',                -- active, inactive, draft
    downstream_status TEXT,                      -- downstream creation status
    downstream_agent_id TEXT,                   -- downstream agent id
    
    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Unique constraint on tools (one agent per tool combination)
    CONSTRAINT unique_allowed_tools UNIQUE (allowed_tools)
);

-- =====================================================
-- AGENTS TABLE MIGRATIONS (for existing tables)
-- =====================================================
ALTER TABLE agents ADD COLUMN IF NOT EXISTS name TEXT;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS goal TEXT;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS allowed_tools TEXT;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS agent_spec TEXT;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS created_by TEXT;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS tenant_id TEXT;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS rag_scope TEXT;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS reasoning_style TEXT DEFAULT 'direct';
ALTER TABLE agents ADD COLUMN IF NOT EXISTS temperature NUMERIC(3,2) DEFAULT 0.30;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS retriever_type TEXT DEFAULT 'simple';
ALTER TABLE agents ADD COLUMN IF NOT EXISTS retriever_k INTEGER DEFAULT 5;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS execution_mode TEXT DEFAULT 'static';
ALTER TABLE agents ADD COLUMN IF NOT EXISTS permissions TEXT DEFAULT 'read_only';
ALTER TABLE agents ADD COLUMN IF NOT EXISTS system_prompt TEXT;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS max_steps INTEGER DEFAULT 6;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS brain_agent_id TEXT;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'active';
ALTER TABLE agents ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE agents ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE agents ADD COLUMN IF NOT EXISTS bot_id TEXT;
ALTER TABLE agents ALTER COLUMN bot_id DROP NOT NULL;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS user_id TEXT;
ALTER TABLE agents ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS downstream_status TEXT;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS downstream_agent_id TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'unique_allowed_tools'
    ) THEN
        ALTER TABLE agents ADD CONSTRAINT unique_allowed_tools UNIQUE (allowed_tools);
    END IF;
END $$;

-- =====================================================
-- AGENT MCP SERVERS TABLE
-- Stores which MCP servers an agent uses
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_mcp_servers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    server_name TEXT NOT NULL,                   -- "jira", "webex", "confluence", "github"
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_mcp_server_unique UNIQUE (agent_id, server_name)
);

-- =====================================================
-- MCP TOOLS TABLE
-- Stores MCP tool registry entries
-- =====================================================
CREATE TABLE IF NOT EXISTS mcp_tools (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    category TEXT,
    capabilities TEXT,                            -- Comma-separated
    required_inputs TEXT,                         -- Comma-separated
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================
-- INDEXES
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_agents_allowed_tools ON agents(allowed_tools);
CREATE INDEX IF NOT EXISTS idx_agents_created_by ON agents(created_by);
CREATE INDEX IF NOT EXISTS idx_agents_tenant_id ON agents(tenant_id);
CREATE INDEX IF NOT EXISTS idx_agents_status ON agents(status);
CREATE INDEX IF NOT EXISTS idx_agent_mcp_servers_agent_id ON agent_mcp_servers(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_mcp_servers_server_name ON agent_mcp_servers(server_name);
CREATE INDEX IF NOT EXISTS idx_mcp_tools_name ON mcp_tools(name);
CREATE INDEX IF NOT EXISTS idx_mcp_tools_category ON mcp_tools(category);

-- =====================================================
-- USERS TABLE
-- Stores user accounts for authentication
-- =====================================================
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    full_name TEXT,
    tenant_id TEXT,
    role TEXT NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT true,
    custom_scopes TEXT,                              -- Optional custom scopes (comma-separated)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Migration: Add custom_scopes column if it doesn't exist
ALTER TABLE users ADD COLUMN IF NOT EXISTS custom_scopes TEXT;

-- =====================================================
-- USERS TABLE INDEXES
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users(tenant_id);

-- =====================================================
-- PGVECTOR EXTENSION
-- Enables vector similarity search for ML-based tool selection
-- =====================================================
CREATE EXTENSION IF NOT EXISTS vector;

-- =====================================================
-- TOOL PREDICTIONS TABLE
-- Stores predictions made by the system for tool selection
-- =====================================================
CREATE TABLE IF NOT EXISTS tool_predictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Prediction Request
    query TEXT NOT NULL,                              -- User query that triggered prediction
    query_embedding vector(384),                      -- Query embedding for similarity search (384 for MiniLM)
    
    -- Prediction Result
    predicted_tools TEXT[],                           -- Array of predicted tool names
    confidence_scores JSONB,                          -- {"tool_name": 0.95, ...}
    prediction_method TEXT NOT NULL,                  -- 'keyword', 'llm', 'cosine', 'ml_classifier', 'hybrid'
    reasoning TEXT,                                   -- Human-readable explanation
    
    -- ML Model Info
    model_version TEXT,                               -- Version of ML model used
    model_name TEXT,                                  -- Name of the model (e.g., 'tool-selector-v1')
    
    -- Context
    user_id TEXT,                                     -- User who made the query
    tenant_id TEXT,                                   -- Organization/workspace
    session_id TEXT,                                  -- Session identifier for grouping
    
    -- Status & Timing
    status TEXT DEFAULT 'pending',                    -- pending, completed, used, discarded
    prediction_time_ms INTEGER,                       -- Time taken for prediction
    
    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================
-- PREDICTION FEEDBACK TABLE
-- Stores user feedback on predictions for learning
-- =====================================================
CREATE TABLE IF NOT EXISTS prediction_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prediction_id UUID NOT NULL REFERENCES tool_predictions(id) ON DELETE CASCADE,
    
    -- Feedback Type
    feedback_type TEXT NOT NULL,                      -- 'correct', 'partial', 'incorrect', 'not_used'
    accuracy_rating INTEGER CHECK (accuracy_rating BETWEEN 1 AND 5),  -- 1-5 star rating
    
    -- Tool-level Feedback
    correct_tools TEXT[],                             -- Tools that were correct
    missing_tools TEXT[],                             -- Tools that should have been selected
    incorrect_tools TEXT[],                           -- Tools that shouldn't have been selected
    
    -- User Feedback
    user_comment TEXT,                                -- Optional user comment
    user_id TEXT,                                     -- Who provided feedback
    
    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================
-- TOOL LEARNING STATS TABLE
-- Aggregated statistics for each tool (for online learning)
-- =====================================================
CREATE TABLE IF NOT EXISTS tool_learning_stats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tool_name TEXT NOT NULL UNIQUE,
    
    -- Prediction Stats
    total_predictions INTEGER DEFAULT 0,              -- Total times this tool was predicted
    correct_predictions INTEGER DEFAULT 0,           -- Times it was marked correct
    incorrect_predictions INTEGER DEFAULT 0,         -- Times it was marked incorrect
    missed_predictions INTEGER DEFAULT 0,            -- Times it should have been predicted but wasn't
    
    -- Computed Metrics
    precision_score NUMERIC(5,4) DEFAULT 0.0,        -- correct / (correct + incorrect)
    recall_score NUMERIC(5,4) DEFAULT 0.0,           -- correct / (correct + missed)
    f1_score NUMERIC(5,4) DEFAULT 0.0,               -- 2 * (precision * recall) / (precision + recall)
    
    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================
-- KEYWORD TOOL WEIGHTS TABLE
-- Learned weights for keyword-tool associations (online learning)
-- =====================================================
CREATE TABLE IF NOT EXISTS keyword_tool_weights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keyword TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    
    -- Learned Weight
    weight NUMERIC(8,6) DEFAULT 1.0,                  -- Association strength (higher = stronger)
    occurrence_count INTEGER DEFAULT 0,               -- How many times this association was seen
    success_count INTEGER DEFAULT 0,                  -- How many times this led to correct prediction
    
    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT keyword_tool_unique UNIQUE (keyword, tool_name)
);

-- =====================================================
-- TRAINING SAMPLES TABLE
-- Stores query-tool pairs for batch classifier training
-- =====================================================
CREATE TABLE IF NOT EXISTS training_samples (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Training Data
    query TEXT NOT NULL,                              -- User query
    query_embedding vector(384),                      -- Query embedding for similarity search
    tools TEXT[] NOT NULL,                            -- Correct tools for this query
    
    -- Source Info
    source TEXT NOT NULL,                             -- 'user_feedback', 'manual', 'synthetic'
    prediction_id UUID REFERENCES tool_predictions(id),  -- Link to original prediction if from feedback
    
    -- Quality Indicators
    confidence_level NUMERIC(3,2) DEFAULT 1.0,        -- How confident are we in this sample (0-1)
    verified BOOLEAN DEFAULT FALSE,                    -- Has this been manually verified
    
    -- Training Usage
    used_in_training BOOLEAN DEFAULT FALSE,           -- Has this been used in a training run
    last_training_run TEXT,                           -- ID of the last training run that used this
    
    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================
-- INDEXES FOR LEARNING TABLES
-- =====================================================

-- Tool Predictions indexes
CREATE INDEX IF NOT EXISTS idx_tool_predictions_query ON tool_predictions(query);
CREATE INDEX IF NOT EXISTS idx_tool_predictions_user_id ON tool_predictions(user_id);
CREATE INDEX IF NOT EXISTS idx_tool_predictions_tenant_id ON tool_predictions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tool_predictions_status ON tool_predictions(status);
CREATE INDEX IF NOT EXISTS idx_tool_predictions_method ON tool_predictions(prediction_method);
CREATE INDEX IF NOT EXISTS idx_tool_predictions_created ON tool_predictions(created_at);
-- Vector similarity index using IVFFlat (faster for large datasets)
CREATE INDEX IF NOT EXISTS idx_tool_predictions_embedding ON tool_predictions 
    USING ivfflat (query_embedding vector_cosine_ops) WITH (lists = 100);

-- Prediction Feedback indexes
CREATE INDEX IF NOT EXISTS idx_prediction_feedback_prediction_id ON prediction_feedback(prediction_id);
CREATE INDEX IF NOT EXISTS idx_prediction_feedback_type ON prediction_feedback(feedback_type);
CREATE INDEX IF NOT EXISTS idx_prediction_feedback_rating ON prediction_feedback(accuracy_rating);

-- Tool Learning Stats indexes
CREATE INDEX IF NOT EXISTS idx_tool_learning_stats_tool ON tool_learning_stats(tool_name);
CREATE INDEX IF NOT EXISTS idx_tool_learning_stats_f1 ON tool_learning_stats(f1_score);

-- Keyword Tool Weights indexes
CREATE INDEX IF NOT EXISTS idx_keyword_tool_weights_keyword ON keyword_tool_weights(keyword);
CREATE INDEX IF NOT EXISTS idx_keyword_tool_weights_tool ON keyword_tool_weights(tool_name);
CREATE INDEX IF NOT EXISTS idx_keyword_tool_weights_weight ON keyword_tool_weights(weight DESC);

-- Training Samples indexes
CREATE INDEX IF NOT EXISTS idx_training_samples_source ON training_samples(source);
CREATE INDEX IF NOT EXISTS idx_training_samples_verified ON training_samples(verified);
CREATE INDEX IF NOT EXISTS idx_training_samples_used ON training_samples(used_in_training);
-- Vector similarity index for training samples
CREATE INDEX IF NOT EXISTS idx_training_samples_embedding ON training_samples 
    USING ivfflat (query_embedding vector_cosine_ops) WITH (lists = 100);

-- =====================================================
-- MODEL TRAINING STATUS TABLE
-- Tracks ML model training phases and accuracy
-- =====================================================
CREATE TABLE IF NOT EXISTS model_training_status (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Model Identity
    model_name TEXT NOT NULL UNIQUE DEFAULT 'tool-selector',
    
    -- Current Phase
    -- 'bootstrap': Using LLM, collecting training data
    -- 'training': Model is being trained
    -- 'hybrid': ML + LLM fallback (accuracy 80-90%)
    -- 'ml_primary': ML primary, LLM for edge cases (accuracy > 90%)
    current_phase TEXT NOT NULL DEFAULT 'bootstrap',
    
    -- Training Metrics
    total_training_samples INTEGER DEFAULT 0,
    samples_from_llm INTEGER DEFAULT 0,           -- LLM predictions used as ground truth
    samples_from_feedback INTEGER DEFAULT 0,      -- User feedback samples
    
    -- Accuracy Metrics (from last evaluation)
    current_accuracy NUMERIC(5,4) DEFAULT 0.0,    -- Overall accuracy
    current_precision NUMERIC(5,4) DEFAULT 0.0,
    current_recall NUMERIC(5,4) DEFAULT 0.0,
    current_f1 NUMERIC(5,4) DEFAULT 0.0,
    
    -- Thresholds
    hybrid_threshold NUMERIC(5,4) DEFAULT 0.80,   -- Switch to hybrid at 80%
    ml_primary_threshold NUMERIC(5,4) DEFAULT 0.90, -- Switch to ML primary at 90%
    min_confidence_for_ml NUMERIC(5,4) DEFAULT 0.75, -- Min ML confidence to skip LLM
    
    -- Training History
    last_training_run TEXT,
    last_training_date TIMESTAMPTZ,
    last_evaluation_date TIMESTAMPTZ,
    training_runs_count INTEGER DEFAULT 0,
    
    -- LLM Token Savings
    llm_calls_total INTEGER DEFAULT 0,            -- Total LLM calls made
    llm_calls_saved INTEGER DEFAULT 0,            -- Calls avoided by using ML
    estimated_tokens_saved BIGINT DEFAULT 0,      -- Estimated tokens saved
    
    -- Model Version
    active_model_version TEXT,
    
    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Insert default status if not exists
INSERT INTO model_training_status (model_name, current_phase)
VALUES ('tool-selector', 'bootstrap')
ON CONFLICT (model_name) DO NOTHING;

-- Index for quick lookup
CREATE INDEX IF NOT EXISTS idx_model_training_status_name ON model_training_status(model_name);
CREATE INDEX IF NOT EXISTS idx_model_training_status_phase ON model_training_status(current_phase);
