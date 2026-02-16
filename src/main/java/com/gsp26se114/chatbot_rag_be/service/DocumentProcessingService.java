package com.gsp26se114.chatbot_rag_be.service;

import com.google.gson.Gson;
import com.gsp26se114.chatbot_rag_be.entity.DocumentChunkEntity;
import com.gsp26se114.chatbot_rag_be.entity.DocumentEntity;
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
     * Core processing logic with detailed step-by-step logging
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
            String text = textExtractor.extractText(fileContent, document.getFileType());
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
            
            // 5. Create embeddings for each chunk
            log.info("[STEP 5] Generating embeddings for {} chunks", chunks.size());
            List<DocumentChunkEntity> chunkEntities = new ArrayList<>();
            
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                
                // Create embedding
                float[] embedding = embeddingService.createEmbedding(chunkText);
                String embeddingVector = embeddingService.toVectorString(embedding);
                
                // Create chunk entity
                DocumentChunkEntity chunk = DocumentChunkEntity.builder()
                        .id(UUID.randomUUID())
                        .documentId(document.getId())
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
                        .createdAt(java.time.LocalDateTime.now())
                        .build();
                
                chunkEntities.add(chunk);
                
                // Batch save every 10 chunks using custom insert with vector cast
                if (chunkEntities.size() >= 10) {
                    for (DocumentChunkEntity c : chunkEntities) {
                        chunkRepository.insertChunkWithVectorCast(
                            c.getId(), c.getDocumentId(), c.getTenantId(), c.getChunkIndex(),
                            c.getContent(), c.getEmbedding(), c.getEmbeddingModel(), c.getTokenCount(),
                            c.getVisibility(), 
                            gson.toJson(c.getAccessibleDepartments()),  // Convert List to JSON
                            gson.toJson(c.getAccessibleRoles()),         // Convert List to JSON
                            c.getOwnerDepartmentId(), c.getCreatedAt()
                        );
                    }
                    chunkEntities.clear();
                    log.info("[STEP 5] ✓ Saved batch, progress: {}/{}", i + 1, chunks.size());
                }
            }
            
            // Save remaining chunks
            log.info("[STEP 5] Saving remaining {} chunks", chunkEntities.size());
            if (!chunkEntities.isEmpty()) {
                for (DocumentChunkEntity c : chunkEntities) {
                    chunkRepository.insertChunkWithVectorCast(
                        c.getId(), c.getDocumentId(), c.getTenantId(), c.getChunkIndex(),
                        c.getContent(), c.getEmbedding(), c.getEmbeddingModel(), c.getTokenCount(),
                        c.getVisibility(), 
                        gson.toJson(c.getAccessibleDepartments()),  // Convert List to JSON
                        gson.toJson(c.getAccessibleRoles()),         // Convert List to JSON
                        c.getOwnerDepartmentId(), c.getCreatedAt()
                    );
                }
                log.info("[STEP 5] ✓ All chunks saved successfully");
            }
            
            // 6. Update document status
            log.info("[STEP 6] Updating document status to COMPLETED");
            document.setEmbeddingStatus("COMPLETED");
            document.setChunkCount(chunks.size());
            document.setEmbeddingModel("gemini-embedding-001");
            document.setEmbeddingError(null);
            documentRepository.save(document);
            
            log.info("[PROCESSING SUCCESS] ✓✓✓ Document {} completed: {} chunks created ✓✓✓", 
                    documentId, chunks.size());
            
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
}
