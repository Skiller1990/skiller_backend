package com.videowebsite.VideoWebsite.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DelegatingEmailService implements EmailService {

    @Value("${email.provider:brevo}")
    private String provider;

    @Autowired
    private BrevoEmailService brevoEmailService;

    @Autowired
    private SesEmailService sesEmailService;

    @Override
    public void sendEmail(String toEmail, String subject, String htmlContent) {
        if ("ses".equalsIgnoreCase(provider)) {
            sesEmailService.sendEmail(toEmail, subject, htmlContent);
        } else {
            brevoEmailService.sendEmail(toEmail, subject, htmlContent);
        }
    }
}
