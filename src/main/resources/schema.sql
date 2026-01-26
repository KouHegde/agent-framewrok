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
