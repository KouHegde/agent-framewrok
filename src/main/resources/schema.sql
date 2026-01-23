CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bot_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    
    -- Agent description/goal
    description TEXT,
    goal TEXT,
    
    -- RAG Configuration
    rag_scope TEXT[] DEFAULT '{}',              -- e.g., {"policy_docs", "tickets", "audit_logs"}
    
    -- Reasoning Configuration  
    reasoning_style TEXT DEFAULT 'direct',       -- direct, step-by-step, comparative, analytical
    temperature NUMERIC(3,2) DEFAULT 0.3,        -- 0.00 to 1.00
    
    -- Retriever Configuration
    retriever_type TEXT DEFAULT 'simple',        -- simple, multi-query, ensemble
    retriever_k INTEGER DEFAULT 5,               -- number of chunks to retrieve
    
    -- Execution Configuration
    execution_mode TEXT DEFAULT 'static',        -- static, dynamic
    permissions TEXT[] DEFAULT '{read_only}',    -- e.g., {"read_only"}, {"read_only", "write"}
    
    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT agents_user_bot_unique UNIQUE (user_id, bot_id),
    CONSTRAINT chk_temperature CHECK (temperature >= 0 AND temperature <= 1),
    CONSTRAINT chk_retriever_k CHECK (retriever_k > 0 AND retriever_k <= 50)
);

CREATE TABLE IF NOT EXISTS agent_mcp_servers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    server_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_mcp_server_unique UNIQUE (agent_id, server_name)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_agents_user_id ON agents(user_id);
CREATE INDEX IF NOT EXISTS idx_agents_reasoning_style ON agents(reasoning_style);
CREATE INDEX IF NOT EXISTS idx_agent_mcp_servers_agent_id ON agent_mcp_servers(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_mcp_servers_server_name ON agent_mcp_servers(server_name);
