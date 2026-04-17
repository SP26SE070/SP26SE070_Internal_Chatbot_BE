package com.gsp26se114.chatbot_rag_be.service;

import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
@Slf4j
public class MinioService {

    @Autowired(required = false)
    private MinioClient minioClient;
    
    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.public-endpoint:http://localhost:9000}")
    private String publicEndpoint;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String internalEndpoint;
    
    /**
     * Upload document vào MinIO
     * 
     * @param file MultipartFile từ user
     * @param folder Folder path: "tenant-{uuid}/documents"
     * @return Storage path: "tenant-123/documents/uuid_filename.pdf"
     */
    public String uploadDocument(MultipartFile file, String folder) {
        if (minioClient == null) {
            log.warn("MinIO is not available — skipping operation");
            throw new RuntimeException("File storage service is currently unavailable");
        }
        try {
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
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
        if (minioClient == null) {
            log.warn("MinIO is not available — skipping operation");
            throw new RuntimeException("File storage service is currently unavailable");
        }
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
        if (minioClient == null) {
            log.warn("MinIO is not available — skipping operation");
            throw new RuntimeException("File storage service is currently unavailable");
        }
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
        if (minioClient == null) {
            log.warn("MinIO is not available — skipping operation");
            throw new RuntimeException("File storage service is currently unavailable");
        }
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

            // Replace internal hostname with public hostname for external access
            if (!internalEndpoint.equals(publicEndpoint)) {
                url = url.replace(internalEndpoint, publicEndpoint);
            }

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
        if (minioClient == null) {
            log.warn("MinIO is not available — skipping operation");
            throw new RuntimeException("File storage service is currently unavailable");
        }
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
