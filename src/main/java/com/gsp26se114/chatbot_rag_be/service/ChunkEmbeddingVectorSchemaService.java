package com.gsp26se114.chatbot_rag_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps {@code document_chunks.embedding} typmod aligned with the active embedding model
 * (768 Gemini vs 1024 local BGE-M3) so re-index and RAG work without a manual env/DDL dance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkEmbeddingVectorSchemaService {

    private static final Object LOCK = new Object();
    private static final Pattern VECTOR_DIM = Pattern.compile("vector\\((\\d+)\\)");

    private final JdbcTemplate jdbcTemplate;

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
                log.warn(
                        "Aligning document_chunks.embedding to vector({}) (was {}); existing vectors cleared.",
                        targetDimension,
                        current);
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
        List<String> typeRows = jdbcTemplate.query(
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
        if (typeRows.isEmpty()) {
            return null;
        }
        String fmt = typeRows.get(0);
        if (fmt == null) {
            return null;
        }
        Matcher m = VECTOR_DIM.matcher(fmt);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }
}
