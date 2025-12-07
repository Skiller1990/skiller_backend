package com.videowebsite.VideoWebsite.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;

@Configuration
public class FirebaseConfig {
    @PostConstruct
    public void init() throws IOException {
        Resource res = new ClassPathResource("serviceAccountKey.json");

        if (res.exists()) {
            try (InputStream serviceAccountStream = res.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                        .setDatabaseUrl("https://sajid-project-62cf9.firebaseio.com")
                        .build();
                FirebaseApp.initializeApp(options);
                System.out.println("Firebase initialized from serviceAccountKey.json (classpath)");
            }
            return;
        }

        File f = new File("src/main/resources/serviceAccountKey.json");
        if (f.exists() && Files.isReadable(f.toPath())) {
            try (InputStream serviceAccountStream = new FileInputStream(f)) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                        .setDatabaseUrl("https://sajid-project-62cf9.firebaseio.com")
                        .build();
                FirebaseApp.initializeApp(options);
                System.out.println("Firebase initialized from serviceAccountKey.json (filesystem)");
            }
            return;
        }

        System.out.println("Firebase service account key not found; skipping Firebase initialization (OK for local SES testing)");
    }
}
