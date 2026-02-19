package com.gsp26se114.chatbot_rag_be.service;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * MinIO Service cho document storage
 * Handles file upload, download, delete operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {
    
    private final MinioClient minioClient;
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    /**
     * Upload document vào MinIO
     * 
     * @param file MultipartFile từ user
     * @param folder Folder path: "tenant-{uuid}/documents"
     * @return Storage path: "tenant-123/documents/uuid_filename.pdf"
     */
    public String uploadDocument(MultipartFile file, String folder) {
        try {
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String uniqueFilename = UUID.randomUUID().toString() + "_" + originalFilename;
            String objectName = folder + "/" + uniqueFilename;
            
            // Upload to MinIO
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build()
            );
            
            log.info("Document uploaded to MinIO: bucket={}, path={}, size={}", 
                bucketName, objectName, file.getSize());
            
            return objectName;
            
        } catch (Exception e) {
            log.error("Failed to upload document to MinIO: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to upload document", e);
        }
    }
    
    /**
     * Download document từ MinIO (for embedding processing)
     * 
     * @param storagePath Path trong MinIO
     * @return byte[] file content
     */
    public byte[] downloadDocument(String storagePath) {
        try {
            InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storagePath)
                    .build()
            );
            
            byte[] content = stream.readAllBytes();
            stream.close();
            
            log.debug("Document downloaded from MinIO: path={}, size={}", storagePath, content.length);
            return content;
            
        } catch (Exception e) {
            log.error("Failed to download document from MinIO: {}", storagePath, e);
            throw new RuntimeException("Failed to download document", e);
        }
    }
    
    /**
     * Delete document từ MinIO
     * 
     * @param storagePath Path trong MinIO
     */
    public void deleteDocument(String storagePath) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storagePath)
                    .build()
            );
            
            log.info("Document deleted from MinIO: path={}", storagePath);
            
        } catch (Exception e) {
            log.error("Failed to delete document from MinIO: {}", storagePath, e);
            throw new RuntimeException("Failed to delete document", e);
        }
    }
    
    /**
     * Generate pre-signed URL (15 minutes expiry)
     * OPTIONAL - Chỉ dùng nếu cần user download file trực tiếp
     * 
     * @param storagePath Path trong MinIO
     * @return Pre-signed URL
     */
    public String getPresignedUrl(String storagePath) {
        try {
            String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(io.minio.http.Method.GET)
                    .bucket(bucketName)
                    .object(storagePath)
                    .expiry(15 * 60) // 15 minutes
                    .build()
            );
            
            log.debug("Pre-signed URL generated: path={}", storagePath);
            return url;
            
        } catch (Exception e) {
            log.error("Failed to generate pre-signed URL: {}", storagePath, e);
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }
    
    /**
     * Check if document exists
     * 
     * @param storagePath Path trong MinIO
     * @return true if exists
     */
    public boolean documentExists(String storagePath) {
        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storagePath)
                    .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
