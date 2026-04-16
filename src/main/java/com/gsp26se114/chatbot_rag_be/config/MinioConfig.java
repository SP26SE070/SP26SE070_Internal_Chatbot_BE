package com.gsp26se114.chatbot_rag_be.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO Configuration
 * MinIO = S3-compatible object storage
 * Local dev: Docker container
 * Production: MinIO cluster hoặc AWS S3
 * 
 * Enable/Disable: minio.enabled=true/false trong application.yaml
 */
@Configuration
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class MinioConfig {
    
    @Value("${minio.endpoint}")
    private String endpoint;
    
    @Value("${minio.access-key}")
    private String accessKey;
    
    @Value("${minio.secret-key}")
    private String secretKey;
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
            
            // Create bucket if not exists
            boolean exists = client.bucketExists(
                BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build()
            );
            
            if (!exists) {
                client.makeBucket(
                    MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build()
                );
                log.info("MinIO bucket '{}' created successfully", bucketName);
            } else {
                log.info("MinIO bucket '{}' already exists", bucketName);
            }
            
            log.info("MinIO client initialized: endpoint={}, bucket={}", endpoint, bucketName);
            return client;
            
        } catch (Exception e) {
            log.error("Failed to initialize MinIO client: {}", e.getMessage());
            log.warn("MinIO is unavailable — document upload/download will not work");
            return null;
        }
    }
}
