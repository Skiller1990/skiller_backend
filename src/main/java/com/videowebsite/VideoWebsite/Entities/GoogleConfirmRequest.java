package com.videowebsite.VideoWebsite.Entities;

public class GoogleConfirmRequest {
    private String idToken;
    private String razorpayPaymentId;
    private String razorpayOrderId;
    private String signature;

    public GoogleConfirmRequest() {
    }

    public GoogleConfirmRequest(String idToken, String razorpayPaymentId, String razorpayOrderId, String signature) {
        this.idToken = idToken;
        this.razorpayPaymentId = razorpayPaymentId;
        this.razorpayOrderId = razorpayOrderId;
        this.signature = signature;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public void setRazorpayPaymentId(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
