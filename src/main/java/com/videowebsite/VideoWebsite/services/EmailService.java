package com.videowebsite.VideoWebsite.services;

public interface EmailService {
    void sendEmail(String toEmail, String subject, String htmlContent);
}
