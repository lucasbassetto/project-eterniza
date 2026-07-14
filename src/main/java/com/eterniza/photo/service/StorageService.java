package com.eterniza.photo.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;

@Slf4j
@Service
public class StorageService {

    @Value("${eterniza.r2.endpoint}") private String endpoint;
    @Value("${eterniza.r2.access-key}") private String accessKey;
    @Value("${eterniza.r2.secret-key}") private String secretKey;
    @Value("${eterniza.r2.bucket}") private String bucket;
    @Value("${eterniza.r2.public-url}") private String publicUrl;

    private S3Client s3;

    @PostConstruct
    public void init() {
        s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    public String upload(String key, MultipartFile file) throws IOException {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket).key(key).contentType(file.getContentType()).build(),
                RequestBody.fromBytes(file.getBytes()));
        return publicUrl + "/" + key;
    }
}
