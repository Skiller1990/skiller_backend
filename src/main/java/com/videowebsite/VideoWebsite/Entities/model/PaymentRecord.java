package com.videowebsite.VideoWebsite.Entities.model;

import lombok.Data;

import java.util.Date;

@Data
public class PaymentRecord {
    private String orderId;
    private String paymentId;
    private String userEmail;
    private int amount;
    private String status;
    private Date createdAt;
    private Date verifiedAt;
    private Date capturedAt;
}
