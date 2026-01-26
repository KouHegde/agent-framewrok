CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    agent_spec TEXT,  -- JSON string of AgentSpec
    user_id TEXT,
    user_config TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_mcp_servers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    server_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_mcp_server_unique UNIQUE (agent_id, server_name)
);

CREATE INDEX IF NOT EXISTS idx_agents_name ON agents(name);
CREATE INDEX IF NOT EXISTS idx_agents_user_id ON agents(user_id);
CREATE INDEX IF NOT EXISTS idx_agents_reasoning_style ON agents(reasoning_style);
CREATE INDEX IF NOT EXISTS idx_agent_mcp_servers_agent_id ON agent_mcp_servers(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_mcp_servers_server_name ON agent_mcp_servers(server_name);
