package com.videowebsite.VideoWebsite.contact;

import java.time.Instant;
import java.util.UUID;

/**
 * Plain POJO used to transfer contact data. Stored in Firestore collection `contacts`.
 */
public class Contact {
    private String id;
    private String name;
    private String email;
    private String subject;
    private String message;
    private Instant createdAt;

    public Contact() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
