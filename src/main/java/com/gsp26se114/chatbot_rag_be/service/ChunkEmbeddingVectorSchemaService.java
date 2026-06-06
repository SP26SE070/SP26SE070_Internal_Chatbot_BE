package com.gsp26se114.chatbot_rag_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Keeps {@code document_chunks.embedding} typmod aligned with the active embedding model
 * (768 Gemini vs 1024 local BGE-M3) so re-index and RAG work without a manual env/DDL dance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkEmbeddingVectorSchemaService {

    private static final Object LOCK = new Object();

    private final JdbcTemplate jdbcTemplate;

    @Value("${embedding.allow-destructive-dimension-migration:false}")
    private boolean allowDestructiveDimensionMigration;

    public void ensureColumnDimension(int targetDimension) {
        if (targetDimension <= 0 || targetDimension > 16_384) {
            return;
        }
        synchronized (LOCK) {
            try {
                Integer current = readEmbeddingColumnDimension();
                if (current != null && current == targetDimension) {
                    return;
                }
                if (!allowDestructiveDimensionMigration) {
                    log.warn(
                            "CRITICAL: document_chunks.embedding is vector({}) but active embedding target is vector({}). "
                                    + "Changing this column requires clearing all existing embeddings and a manual re-index. "
                                    + "Refusing destructive migration because embedding.allow-destructive-dimension-migration=false.",
                            current, targetDimension);
                    throw new IllegalStateException(
                            "document_chunks.embedding dimension mismatch: current=" + current
                                    + ", target=" + targetDimension
                                    + ". Set embedding.allow-destructive-dimension-migration=true only during a planned "
                                    + "manual vector reset, then re-index affected documents.");
                }
                log.warn(
                        "CRITICAL: embedding.allow-destructive-dimension-migration=true; clearing document_chunks.embedding "
                                + "to migrate vector dimension. Manual re-index is required immediately after this change.");
                jdbcTemplate.execute("DROP INDEX IF EXISTS idx_chunks_embedding_cosine");
                jdbcTemplate.update("UPDATE document_chunks SET embedding = NULL WHERE embedding IS NOT NULL");
                jdbcTemplate.execute(
                        "ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector(" + targetDimension + ")");

                // pgvector HNSW supports max 2000 dims for 'vector', up to 4000 for 'halfvec'.
                // For dimensions over 2000, build the index on a halfvec cast.
                if (targetDimension > 2000) {
                    jdbcTemplate.execute(
                            "CREATE INDEX IF NOT EXISTS idx_chunks_embedding_cosine ON document_chunks " +
                            "USING hnsw ((embedding::halfvec(" + targetDimension + ")) halfvec_cosine_ops)");
                    log.info("document_chunks.embedding is now vector({}) with halfvec HNSW index.", targetDimension);
                } else {
                    jdbcTemplate.execute(
                            "CREATE INDEX IF NOT EXISTS idx_chunks_embedding_cosine ON document_chunks " +
                            "USING hnsw (embedding vector_cosine_ops)");
                    log.info("document_chunks.embedding is now vector({}) with standard HNSW index.", targetDimension);
                }
            } catch (Exception ex) {
                log.error("Failed to align document_chunks.embedding to vector({}): {}", targetDimension, ex.getMessage());
                throw new IllegalStateException(
                        "Không thể chỉnh cột PostgreSQL document_chunks.embedding sang vector("
                                + targetDimension + "): " + ex.getMessage(),
                        ex);
            }
        }
    }

    private Integer readEmbeddingColumnDimension() {
        List<Integer> dimensions = jdbcTemplate.query(
                """
                        SELECT CASE
                                   WHEN a.atttypmod > 0 THEN a.atttypmod
                                   ELSE NULL
                               END AS dimension
                        FROM pg_attribute a
                        JOIN pg_class c ON a.attrelid = c.oid
                        JOIN pg_namespace n ON c.relnamespace = n.oid
                        JOIN pg_type t ON a.atttypid = t.oid
                        WHERE n.nspname = 'public'
                          AND c.relname = 'document_chunks'
                          AND a.attname = 'embedding'
                          AND t.typname = 'vector'
                          AND NOT a.attisdropped
                        """,
                (rs, rowNum) -> {
                    int dimension = rs.getInt("dimension");
                    return rs.wasNull() ? null : dimension;
                });
        return dimensions.isEmpty() ? null : dimensions.get(0);
    }
}
