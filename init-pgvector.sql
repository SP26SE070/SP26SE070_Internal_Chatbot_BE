-- Enable pgvector extension for vector similarity search
CREATE EXTENSION IF NOT EXISTS vector;

-- Verify extension is installed
SELECT * FROM pg_extension WHERE extname = 'vector';

-- Log installation
DO $$
BEGIN
    RAISE NOTICE 'pgvector extension initialized successfully';
END $$;
