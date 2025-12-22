package com.videowebsite.VideoWebsite.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;

@Configuration
public class FirebaseConfig {
    @Value("${firebase.database.url:https://sajid-project-62cf9.firebaseio.com}")
    private String databaseUrl;
    @Value("${firebase.project.id:sajid-project}")
    private String projectId;
    @Value("${firebase.private.key.id}")
    private String privateKeyId;
    @Value("${firebase.private.key}")
    private String privateKey;
    @Value("${firebase.client.email}")
    private String clientEmail;
    @Value("${firebase.client.id}")
    private String clientId;
    @Value("${firebase.client.x509.cert.url}")
    private String clientX509CertUrl;

    @PostConstruct
    public void init() throws IOException {

        if (privateKey == null || privateKey.isEmpty()) {
            System.out.println("⚠ Firebase environment variables not found. Skipping Firebase initialization.");
            return;
        }

        // IMPORTANT: Convert \n to actual newlines
        privateKey = privateKey.replace("\\n", "\n");

        Map<String, Object> credentialsMap = new HashMap<>();
        credentialsMap.put("type", "service_account"); // optional but safe
        credentialsMap.put("project_id", projectId);
        credentialsMap.put("private_key_id", privateKeyId);
        credentialsMap.put("private_key", privateKey);
        credentialsMap.put("client_email", clientEmail);
        credentialsMap.put("client_id", clientId);
        credentialsMap.put("auth_uri", "https://accounts.google.com/o/oauth2/auth");
        credentialsMap.put("token_uri", "https://oauth2.googleapis.com/token");
        credentialsMap.put("auth_provider_x509_cert_url", "https://www.googleapis.com/oauth2/v1/certs");
        credentialsMap.put("client_x509_cert_url", clientX509CertUrl);

        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new ByteArrayInputStream(new ObjectMapper().writeValueAsBytes(credentialsMap))
        );

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setDatabaseUrl(databaseUrl)
                .build();

        FirebaseApp.initializeApp(options);

        System.out.println("✅ Firebase initialized from environment variables");
    }
}
