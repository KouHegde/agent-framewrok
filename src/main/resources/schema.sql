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
    
    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Unique constraint on tools (one agent per tool combination)
    CONSTRAINT unique_allowed_tools UNIQUE (allowed_tools)
);

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
-- INDEXES
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_agents_allowed_tools ON agents(allowed_tools);
CREATE INDEX IF NOT EXISTS idx_agents_created_by ON agents(created_by);
CREATE INDEX IF NOT EXISTS idx_agents_tenant_id ON agents(tenant_id);
CREATE INDEX IF NOT EXISTS idx_agents_status ON agents(status);
CREATE INDEX IF NOT EXISTS idx_agent_mcp_servers_agent_id ON agent_mcp_servers(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_mcp_servers_server_name ON agent_mcp_servers(server_name);
