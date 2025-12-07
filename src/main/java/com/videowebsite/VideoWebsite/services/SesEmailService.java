package com.videowebsite.VideoWebsite.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

@Service
public class SesEmailService implements EmailService {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.accessKeyId:}")
    private String awsAccessKeyId;

    @Value("${aws.secretKey:}")
    private String awsSecretKey;

    @Value("${aws.ses.senderEmail:no-reply@yourdomain.com}")
    private String sender;

    @Override
    public void sendEmail(String toEmail, String subject, String htmlContent) {
        Region region = Region.of(awsRegion);

        SesClient client;
        if (awsAccessKeyId != null && !awsAccessKeyId.isBlank() && awsSecretKey != null && !awsSecretKey.isBlank()) {
            AwsBasicCredentials creds = AwsBasicCredentials.create(awsAccessKeyId, awsSecretKey);
            client = SesClient.builder()
                    .region(region)
                    .credentialsProvider(StaticCredentialsProvider.create(creds))
                    .build();
        } else {
            client = SesClient.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        }

        try (client) {
            Destination destination = Destination.builder()
                    .toAddresses(toEmail)
                    .build();

            Content subj = Content.builder().data(subject).build();
            Content html = Content.builder().data(htmlContent).build();
            Body body = Body.builder().html(html).build();

            Message message = Message.builder().subject(subj).body(body).build();

            SendEmailRequest request = SendEmailRequest.builder()
                    .destination(destination)
                    .message(message)
                    .source(sender)
                    .build();

            SendEmailResponse response = client.sendEmail(request);
            System.out.println("SES sendEmail messageId=" + response.messageId());
        } catch (SesException e) {
            System.err.println("Failed to send email via SES: " + e.awsErrorDetails().errorMessage());
            e.printStackTrace();
        }
    }
}
