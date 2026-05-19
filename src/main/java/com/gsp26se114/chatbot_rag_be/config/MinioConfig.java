package com.gsp26se114.chatbot_rag_be.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

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

    @Value("${minio.tenant-assets-bucket}")
    private String tenantAssetsBucket;

    @Autowired
    private Environment environment;
    
    @Bean
    @ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

            ensureBucketExists(client, bucketName, false);
            ensureBucketExists(client, tenantAssetsBucket, true);

            log.info("MinIO client initialized: endpoint={}, bucket={}, tenantAssetsBucket={}",
                endpoint, bucketName, tenantAssetsBucket);
            return client;
            
        } catch (Exception e) {
            log.error("Failed to initialize MinIO client: {}", e.getMessage());
            log.warn("MinIO is unavailable — document upload/download will not work");
            return null;
        }
    }

    private void ensureBucketExists(MinioClient client, String bucket, boolean publicRead)
            throws Exception {
        if (bucket == null || bucket.isBlank()) {
            return;
        }
        boolean exists = client.bucketExists(
            BucketExistsArgs.builder()
                .bucket(bucket)
                .build()
        );

        if (!exists) {
            client.makeBucket(
                MakeBucketArgs.builder()
                    .bucket(bucket)
                    .build()
            );
            log.info("MinIO bucket '{}' created successfully", bucket);
        } else {
            log.info("MinIO bucket '{}' already exists", bucket);
        }

        if (publicRead) {
            try {
                applyPublicReadPolicy(client, bucket);
            } catch (Exception e) {
                log.warn("Unable to set public read policy for bucket '{}': {}", bucket, e.getMessage());
            }
        }
    }

    private void applyPublicReadPolicy(MinioClient client, String bucket) throws Exception {
        String policy = String.format(
            "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::%s/*\"]}]}",
            bucket
        );
        client.setBucketPolicy(
            SetBucketPolicyArgs.builder()
                .bucket(bucket)
                .config(policy)
                .build()
        );
        log.info("Public read policy applied to MinIO bucket '{}'", bucket);
    }

    @Bean(name = "publicMinioClient")
    @ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
    public MinioClient publicMinioClient() {
        try {
            String publicEndpoint = environment.getProperty("minio.public-endpoint", endpoint);
            String accessKey = environment.getProperty("minio.access-key", "minioadmin");
            String secretKey = environment.getProperty("minio.secret-key", "minioadmin123");
            MinioClient client = MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(accessKey, secretKey)
                .build();
            log.info("Public MinIO client initialized: {}", publicEndpoint);
            return client;
        } catch (Exception e) {
            log.warn("Public MinIO client not available: {}", e.getMessage());
            return null;
        }
    }
}
