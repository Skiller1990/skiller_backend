package com.videowebsite.VideoWebsite.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import sendinblue.ApiClient;
import sendinblue.Configuration;
import sibApi.TransactionalEmailsApi;
import sibModel.CreateSmtpEmail;
import sibModel.SendSmtpEmail;
import sibModel.SendSmtpEmailSender;
import sibModel.SendSmtpEmailTo;

@Service
public class BrevoEmailService implements EmailService {

    @Value("${brevo.api.key}")
    private String apiKey;

    public void sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            ApiClient defaultClient = Configuration.getDefaultApiClient();
            defaultClient.setApiKey(apiKey);

            TransactionalEmailsApi apiInstance = new TransactionalEmailsApi();

            SendSmtpEmailSender sender = new SendSmtpEmailSender()
                    .name("Skiller Classes")
                    .email("rsuri95550@gmail.com");  // Use your verified sender

            List<SendSmtpEmailTo> toList = new ArrayList<>();
            toList.add(new SendSmtpEmailTo().email(toEmail));

            SendSmtpEmail sendEmail = new SendSmtpEmail()
                    .to(toList)
                    .sender(sender)
                    .subject(subject)
                    .htmlContent(htmlContent);

            CreateSmtpEmail response = apiInstance.sendTransacEmail(sendEmail);
            System.out.println("Email sent: " + response.getMessageId());
        } catch (Exception e) {
            System.out.println("Error sending email via Brevo");
            e.printStackTrace();
        }
    }
}

