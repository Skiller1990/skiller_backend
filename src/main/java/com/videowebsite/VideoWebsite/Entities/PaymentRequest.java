package com.videowebsite.VideoWebsite.Entities;

public class PaymentRequest {
    private int amount;
    private String userEmail;
    private String courseId;
    private String courseName;
    private String promoCode; // optional
    
    public PaymentRequest() {}
    
    public int getAmount() {
        return amount;
    }
    
    public void setAmount(int amount) {
        this.amount = amount;
    }
    
    public String getUserEmail() {
        return userEmail;
    }
    
    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
    
    public String getCourseId() {
        return courseId;
    }
    
    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }
    
    public String getCourseName() {
        return courseName;
    }
    
    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }
}
