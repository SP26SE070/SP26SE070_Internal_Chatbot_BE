package com.gsp26se114.chatbot_rag_be.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Cột tương thích cho DB cũ: roles.level (1–5), documents.minimum_role_level,
 * document_chunks.minimum_role_level, users.token_version, subscriptions.grace_period_days.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaCompatibilityRunner {

    private final JdbcTemplate jdbcTemplate;

    @Value("${embedding.storage-dimension:768}")
    private int embeddingStorageDimension;

    @EventListener(ApplicationReadyEvent.class)
    public void applyCompatibilityMigrations() {
        try {
            jdbcTemplate.execute("ALTER TABLE roles ADD COLUMN IF NOT EXISTS level INTEGER");
            jdbcTemplate.execute("""
                    UPDATE roles
                    SET level = CASE
                        WHEN code = 'SUPER_ADMIN' THEN 1
                        WHEN code = 'STAFF' THEN 2
                        WHEN code = 'TENANT_ADMIN' THEN COALESCE(level, 2)
                        WHEN code = 'EMPLOYEE' THEN COALESCE(level, 4)
                        ELSE COALESCE(level, 4)
                    END
                    WHERE level IS NULL OR code IN ('SUPER_ADMIN', 'STAFF')
                    """);
            jdbcTemplate.execute("ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_level_check");
            jdbcTemplate.execute("ALTER TABLE roles ADD CONSTRAINT roles_level_check CHECK (level BETWEEN 1 AND 5 OR level IS NULL)");

            jdbcTemplate.execute("ALTER TABLE documents ADD COLUMN IF NOT EXISTS minimum_role_level INTEGER");
            try {
                jdbcTemplate.execute("""
                        UPDATE documents SET minimum_role_level = required_level
                        WHERE minimum_role_level IS NULL
                          AND EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public' AND table_name = 'documents' AND column_name = 'required_level'
                          )
                        """);
            } catch (Exception ignored) {
                // required_level column may already be dropped
            }
            jdbcTemplate.execute("UPDATE documents SET minimum_role_level = 4 WHERE minimum_role_level IS NULL");
            jdbcTemplate.execute("ALTER TABLE documents ALTER COLUMN minimum_role_level SET DEFAULT 4");
            try {
                jdbcTemplate.execute("ALTER TABLE documents DROP COLUMN IF EXISTS required_level");
            } catch (Exception ex) {
                log.debug("Drop required_level skipped: {}", ex.getMessage());
            }

            jdbcTemplate.execute("ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS minimum_role_level INTEGER");
            jdbcTemplate.execute("""
                    UPDATE document_chunks c
                    SET minimum_role_level = COALESCE(c.minimum_role_level, d.minimum_role_level, 4)
                    FROM documents d
                    WHERE c.document_id = d.document_id AND c.minimum_role_level IS NULL
                    """);
            jdbcTemplate.execute("UPDATE document_chunks SET minimum_role_level = 4 WHERE minimum_role_level IS NULL");
            jdbcTemplate.execute("ALTER TABLE document_chunks ALTER COLUMN minimum_role_level SET DEFAULT 4");

            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER");
            jdbcTemplate.execute("UPDATE users SET token_version = 1 WHERE token_version IS NULL");
            jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN token_version SET DEFAULT 1");
            jdbcTemplate.execute("ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS grace_period_days INTEGER");
            jdbcTemplate.execute("UPDATE subscriptions SET grace_period_days = 7 WHERE grace_period_days IS NULL");
            jdbcTemplate.execute("ALTER TABLE subscriptions ALTER COLUMN grace_period_days SET DEFAULT 7");
            // Avoid PostgreSQL keyword collision with column "mode" on chatbot_configs.
            try {
                jdbcTemplate.execute("ALTER TABLE IF EXISTS chatbot_configs ADD COLUMN IF NOT EXISTS chat_mode VARCHAR(20)");
                jdbcTemplate.execute("ALTER TABLE IF EXISTS chatbot_configs ADD COLUMN IF NOT EXISTS top_k INTEGER");
                jdbcTemplate.execute("ALTER TABLE IF EXISTS chatbot_configs ADD COLUMN IF NOT EXISTS similarity_threshold DOUBLE PRECISION");
                jdbcTemplate.execute("ALTER TABLE IF EXISTS chatbot_configs ADD COLUMN IF NOT EXISTS embedding_provider VARCHAR(20)");
                jdbcTemplate.execute("""
                        UPDATE chatbot_configs
                        SET chat_mode = COALESCE(chat_mode, "mode")
                        WHERE chat_mode IS NULL
                        """);
            } catch (Exception ignored) {
                // Old schema may not have "mode" column.
            }
            jdbcTemplate.execute("UPDATE chatbot_configs SET chat_mode = 'BALANCED' WHERE chat_mode IS NULL");
            jdbcTemplate.execute("UPDATE chatbot_configs SET top_k = 7 WHERE top_k IS NULL");
            jdbcTemplate.execute("UPDATE chatbot_configs SET similarity_threshold = 0.7 WHERE similarity_threshold IS NULL");
            jdbcTemplate.execute("UPDATE chatbot_configs SET embedding_provider = 'GEMINI' WHERE embedding_provider IS NULL");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS chatbot_configs ALTER COLUMN chat_mode SET DEFAULT 'BALANCED'");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS chatbot_configs ALTER COLUMN top_k SET DEFAULT 7");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS chatbot_configs ALTER COLUMN similarity_threshold SET DEFAULT 0.7");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS chatbot_configs ALTER COLUMN embedding_provider SET DEFAULT 'GEMINI'");
            jdbcTemplate.execute("DELETE FROM tenants WHERE tenant_id = '880e8400-e29b-41d4-a716-446655440003'");

            maybeUpgradeChunkEmbeddingVectorDimension();

            log.info("Schema compatibility migration applied (roles.level 1–5, minimum_role_level, chatbot_configs.chat_mode).");
        } catch (Exception ex) {
            log.warn("Schema compatibility migration skipped: {}", ex.getMessage());
        }
    }

    /**
     * When {@code embedding.storage-dimension} is not the baseline 768, align PostgreSQL
     * {@code document_chunks.embedding} with the configured vector dimension. Clears existing vectors so re-index is required.
     */
    private void maybeUpgradeChunkEmbeddingVectorDimension() {
        if (embeddingStorageDimension == 768) {
            return;  // 768 is the baseline, no migration needed
        }
        try {
            var typeRows = jdbcTemplate.query(
                    """
                            SELECT pg_catalog.format_type(a.atttypid, a.atttypmod) AS t
                            FROM pg_attribute a
                            JOIN pg_class c ON a.attrelid = c.oid
                            JOIN pg_namespace n ON c.relnamespace = n.oid
                            WHERE n.nspname = 'public'
                              AND c.relname = 'document_chunks'
                              AND a.attname = 'embedding'
                              AND NOT a.attisdropped
                            """,
                    (rs, rowNum) -> rs.getString("t"));
            String fmt = typeRows.isEmpty() ? null : typeRows.get(0);
            if (fmt != null && fmt.contains(String.valueOf(embeddingStorageDimension))) {
                log.debug("document_chunks.embedding already vector({}); skip column migration.", embeddingStorageDimension);
                return;
            }
            jdbcTemplate.execute("DROP INDEX IF EXISTS idx_chunks_embedding_cosine");
            jdbcTemplate.update("UPDATE document_chunks SET embedding = NULL WHERE embedding IS NOT NULL");
            jdbcTemplate.execute("ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector(" + embeddingStorageDimension + ")");
            if (embeddingStorageDimension > 2000) {
                jdbcTemplate.execute(
                        "CREATE INDEX IF NOT EXISTS idx_chunks_embedding_cosine ON document_chunks " +
                        "USING hnsw ((embedding::halfvec(" + embeddingStorageDimension + ")) halfvec_cosine_ops)");
            } else {
                jdbcTemplate.execute(
                        "CREATE INDEX IF NOT EXISTS idx_chunks_embedding_cosine ON document_chunks " +
                        "USING hnsw (embedding vector_cosine_ops)");
            }
            log.info(
                    "document_chunks.embedding set to vector({}) for configured embeddings; prior vectors cleared - re-index documents.",
                    embeddingStorageDimension);
        } catch (Exception ex) {
            log.warn("Chunk embedding vector({}) migration skipped: {}", embeddingStorageDimension, ex.getMessage());
        }
    }
}
