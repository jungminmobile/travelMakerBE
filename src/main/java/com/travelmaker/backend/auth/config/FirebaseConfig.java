package com.travelmaker.backend.auth.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import java.io.IOException;

@Configuration
public class FirebaseConfig {
    @Bean
    @Lazy
    public FirebaseAuth firebaseAuth(@Value("${app.firebase.project-id}") String projectId)
            throws IOException {
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(projectId)
                .build();
        FirebaseApp app = FirebaseApp.initializeApp(options, "travel-maker-auth");
        return FirebaseAuth.getInstance(app);
    }
}