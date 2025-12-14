package com.videowebsite.VideoWebsite.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import sendinblue.ApiClient;
import sendinblue.ApiException;
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

    @Value("${brevo.sender.email:Support@skillerclasses.com}")
    private String senderEmail;
    @Value("${brevo.sender.name:Skiller Classes}")
    private String senderName;

    @Autowired
    private SesEmailService sesEmailService;

    public void sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            ApiClient defaultClient = Configuration.getDefaultApiClient();
            defaultClient.setApiKey(apiKey);

            TransactionalEmailsApi apiInstance = new TransactionalEmailsApi();

            SendSmtpEmailSender sender = new SendSmtpEmailSender()
                    .name(senderName)
                    .email(senderEmail);

            List<SendSmtpEmailTo> toList = new ArrayList<>();
            toList.add(new SendSmtpEmailTo().email(toEmail));

            SendSmtpEmail sendEmail = new SendSmtpEmail()
                    .to(toList)
                    .sender(sender)
                    .subject(subject)
                    .htmlContent(htmlContent);

            CreateSmtpEmail response = apiInstance.sendTransacEmail(sendEmail);
            System.out.println("Email sent: " + response.getMessageId());
        } catch (ApiException ae) {
            // Log Brevo/SendinBlue API exception details to help debugging
            System.out.println("Error sending email via Brevo: ApiException code=" + ae.getCode());
            try {
                System.out.println("Response body: " + ae.getResponseBody());
                System.out.println("Response headers: " + ae.getResponseHeaders());
            } catch (Exception inner) {
                // ignore
            }
            ae.printStackTrace();
            // If the API key is unauthorized or Brevo fails, attempt a fallback to SES
            try {
                int code = ae.getCode();
                if (code == 401 || code == 403 || "unauthorized".equalsIgnoreCase(ae.getResponseBody())) {
                    System.out.println("Brevo auth failed (code=" + code + "), attempting SES fallback...");
                    try {
                        sesEmailService.sendEmail(toEmail, subject, htmlContent);
                        System.out.println("SES fallback succeeded for " + toEmail);
                    } catch (Exception sesEx) {
                        System.err.println("SES fallback also failed: " + sesEx.getMessage());
                        sesEx.printStackTrace();
                    }
                }
            } catch (Exception ignore) {
                // ignore any issues with fallback logic
            }
        } catch (Exception e) {
            System.out.println("Error sending email via Brevo");
            e.printStackTrace();
        }
    }
}

