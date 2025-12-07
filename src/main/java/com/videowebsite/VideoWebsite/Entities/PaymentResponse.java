package com.videowebsite.VideoWebsite.Entities;

import lombok.Data;

@Data
public class PaymentResponse {
    private String orderId;
    private String amount;
    private String currency;
    private String key;
    private String status;
    private String message;
    
    public PaymentResponse() {}
    
    public PaymentResponse(String orderId, String amount, String currency, String key) {
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.key = key;
        this.status = "success";
    }
    
    public PaymentResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }
}