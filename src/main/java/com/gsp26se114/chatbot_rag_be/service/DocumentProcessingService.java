package com.gsp26se114.chatbot_rag_be.service;

import com.google.gson.Gson;
import com.gsp26se114.chatbot_rag_be.entity.DocumentChunkEntity;
import com.gsp26se114.chatbot_rag_be.entity.DocumentEntity;
import com.gsp26se114.chatbot_rag_be.entity.DocumentTag;
import com.gsp26se114.chatbot_rag_be.repository.DocumentChunkRepository;
import com.gsp26se114.chatbot_rag_be.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Background service để process documents: extract text → chunks → embeddings
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final MinioService minioService;
    private final TextExtractorService textExtractor;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final Gson gson = new Gson();

    /**
     * Process document synchronously (for testing)
     * Uses separate transaction for error handling to avoid rollback-only issues
     */
    @Transactional
    public void processDocumentSync(UUID documentId) {
        log.info("=== SYNC METHOD CALLED === Document: {}", documentId);
        try {
            // Delete existing chunks to allow re-processing
            chunkRepository.deleteByDocumentId(documentId);
            processDocument(documentId);
        } catch (Exception e) {
            log.error("Processing failed for document: {}", documentId, e);
            // Update status in separate transaction (won't be affected by parent rollback)
            updateDocumentErrorStatus(documentId, e.getMessage());
            // Rethrow as runtime exception to inform client
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }
    
    /**
     * Process document async: download → extract → chunk → embed
     */
    @Async
    @Transactional
    public void processDocumentAsync(UUID documentId) {
        log.info("=== ASYNC METHOD CALLED === Thread: {}, Document: {}", 
                Thread.currentThread().getName(), documentId);
        try {
            // Skip if already processed or being processed
            DocumentEntity doc = documentRepository.findById(documentId).orElse(null);
            if (doc == null) {
                log.warn("Document not found for async processing: {}", documentId);
                return;
            }
            if ("COMPLETED".equals(doc.getEmbeddingStatus()) || "PROCESSING".equals(doc.getEmbeddingStatus())) {
                log.info("Document {} already {}, skipping async processing", documentId, doc.getEmbeddingStatus());
                return;
            }
            processDocument(documentId);
        } catch (Exception e) {
            log.error("Async processing failed for document: {}", documentId, e);
            updateDocumentErrorStatus(documentId, e.getMessage());
        }
    }
    
    /**
     * Core processing logic with detailed step-by-step logging.
     * Chunk saves use separate commits via flush() to ensure errors surface
     * before status is set to COMPLETED.
     */
    @Transactional
    private void processDocument(UUID documentId) {
        log.info("[PROCESSING START] Document: {}", documentId);

        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        try {
            // 1. Update status
            log.info("[STEP 1] Updating document status to PROCESSING");
            document.setEmbeddingStatus("PROCESSING");
            documentRepository.save(document);
            log.info("[STEP 1] ✓ Status updated successfully");

            // 2. Download file from MinIO
            log.info("[STEP 2] Downloading file from MinIO: {}", document.getStoragePath());
            byte[] fileContent = minioService.downloadDocument(document.getStoragePath());
            log.info("[STEP 2] ✓ Downloaded {} bytes", fileContent.length);

            // 3. Extract text
            log.info("[STEP 3] Extracting text from {} file", document.getFileType());
            String text = textExtractor.extractText(
                    fileContent,
                    document.getFileType(),
                    document.getOriginalFileName());
            log.info("[STEP 3] ✓ Extracted {} characters", text.length());

            if (text.isBlank()) {
                throw new RuntimeException("No text extracted from document");
            }

            // 4. Split into chunks
            log.info("[STEP 4] Splitting text into chunks");
            List<String> chunks = chunkingService.splitText(text);
            log.info("[STEP 4] ✓ Created {} chunks", chunks.size());

            if (chunks.isEmpty()) {
                throw new RuntimeException("No chunks created from text");
            }

            // 5. Create embeddings and persist chunks
            log.info("[STEP 5] Generating embeddings for {} chunks", chunks.size());
            int savedCount = 0;
            int failedCount = 0;

            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);

                try {
                    // Create embedding
                    float[] embedding = embeddingService.createEmbedding(chunkText);
                    String embeddingVector = embeddingService.toVectorString(embedding);

                    // Create chunk entity
                    DocumentChunkEntity chunk = DocumentChunkEntity.builder()
                            .id(UUID.randomUUID())
                            .documentId(document.getId())
                            .versionId(document.getActiveVersionId())
                            .tenantId(document.getTenantId())
                            .chunkIndex(i)
                            .content(chunkText)
                            .embedding(embeddingVector)
                            .embeddingModel("gemini-embedding-001")
                            .tokenCount(chunkText.length() / 4) // Rough estimate
                            // Copy access control from parent document
                            .visibility(document.getVisibility().toString())
                            .accessibleDepartments(document.getAccessibleDepartments())
                            .accessibleRoles(document.getAccessibleRoles())
                            .ownerDepartmentId(document.getOwnerDepartmentId())
                            .categoryId(document.getCategoryId())
                            .tagIds(extractTagIds(document))
                            .createdAt(java.time.LocalDateTime.now())
                            .build();

                    // Persist immediately — flush to surface errors before setting COMPLETED
                    log.debug("[STEP 5] Saving chunk {}/{}: id={}", i + 1, chunks.size(), chunk.getId());
                    chunkRepository.insertChunkWithVectorCast(
                            chunk.getId(), chunk.getDocumentId(), chunk.getTenantId(), chunk.getChunkIndex(),
                            chunk.getContent(), chunk.getEmbedding(), chunk.getEmbeddingModel(), chunk.getTokenCount(),
                            chunk.getVisibility(),
                            gson.toJson(chunk.getAccessibleDepartments()),
                            gson.toJson(chunk.getAccessibleRoles()),
                            chunk.getCategoryId(),
                            gson.toJson(chunk.getTagIds()),
                            chunk.getVersionId(),
                            chunk.getOwnerDepartmentId(), chunk.getCreatedAt()
                    );
                    chunkRepository.flush();
                    savedCount++;

                    if ((i + 1) % 10 == 0 || i == chunks.size() - 1) {
                        log.info("[STEP 5] ✓ Progress: {}/{} chunks saved", i + 1, chunks.size());
                    }

                } catch (Exception chunkEx) {
                    failedCount++;
                    log.error("[STEP 5] ✗ Failed to save chunk {}/{}: {}", i + 1, chunks.size(), chunkEx.getMessage());
                    // Continue with remaining chunks — don't kill entire document for one bad chunk
                    if (failedCount >= 5) {
                        log.error("[STEP 5] Too many chunk failures ({}), aborting document {}", failedCount, documentId);
                        throw new RuntimeException("Too many chunk failures: " + chunkEx.getMessage(), chunkEx);
                    }
                }
            }

            log.info("[STEP 5] ✓ Chunk embedding complete: {} saved, {} failed", savedCount, failedCount);

            if (savedCount == 0) {
                throw new RuntimeException("No chunks were successfully saved");
            }

            // 6. Update document status — use REQUIRES_NEW so it commits independently
            // This ensures COMPLETED only shows if chunks actually persisted
            log.info("[STEP 6] Marking document COMPLETED ({} chunks)", savedCount);
            updateDocumentCompleteStatus(documentId, savedCount, "gemini-embedding-001");

            log.info("[PROCESSING SUCCESS] ✓✓✓ Document {} completed: {} chunks created ✓✓✓",
                    documentId, savedCount);

        } catch (Exception e) {
            log.error("[PROCESSING FAILED] Document: {}", documentId, e);
            System.err.println("====== PROCESSING ERROR ======");
            System.err.println("Document ID: " + documentId);
            System.err.println("Error Type: " + e.getClass().getName());
            System.err.println("Error Message: " + e.getMessage());
            e.printStackTrace();
            System.err.println("==============================");

            // Rethrow exception to trigger rollback and propagate to caller
            throw new RuntimeException("Document processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Update document to COMPLETED in a separate transaction.
     * Uses REQUIRES_NEW so the COMPLETED status is only persisted if this
     * method's transaction commits successfully — which only happens after
     * all chunk inserts have flushed without error.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateDocumentCompleteStatus(UUID documentId, int chunkCount, String embeddingModel) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
        document.setEmbeddingStatus("COMPLETED");
        document.setChunkCount(chunkCount);
        document.setEmbeddingModel(embeddingModel);
        document.setEmbeddingError(null);
        documentRepository.save(document);
        log.info("[STEP 6] ✓ Document {} marked COMPLETED with {} chunks", documentId, chunkCount);
    }

    /**
     * Update document error status in a separate transaction
     * This prevents "transaction marked as rollback-only" errors
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateDocumentErrorStatus(UUID documentId, String errorMessage) {
        try {
            DocumentEntity document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
            
            document.setEmbeddingStatus("FAILED");
            document.setEmbeddingError(errorMessage != null && errorMessage.length() > 500 
                    ? errorMessage.substring(0, 500) + "..." 
                    : errorMessage);
            documentRepository.save(document);
            
            log.info("[ERROR STATUS SAVED] Document {} marked as FAILED", documentId);
        } catch (Exception e) {
            log.error("Failed to update error status for document: {}", documentId, e);
            // Don't rethrow - this is best effort
        }
    }

    /**
     * Re-process document (xóa chunks cũ, tạo lại)
     */
    @Transactional
    public void reprocessDocument(UUID documentId) {
        log.info("Re-processing document: {}", documentId);
        
        // Delete old chunks
        chunkRepository.deleteByDocumentId(documentId);
        
        // Process again
        processDocumentAsync(documentId);
    }

    private List<UUID> extractTagIds(DocumentEntity document) {
        if (document.getTags() == null || document.getTags().isEmpty()) {
            return List.of();
        }

        return document.getTags().stream()
                .map(DocumentTag::getId)
                .toList();
    }
}
