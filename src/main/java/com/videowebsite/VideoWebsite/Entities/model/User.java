package com.videowebsite.VideoWebsite.Entities.model;

import lombok.Data;

import java.util.List;

@Data
public class User {
    private String id;
    private String userName;
    private String email;
    private String password;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isGoogleSignIn() {
        return googleSignIn;
    }

    public void setGoogleSignIn(boolean googleSignIn) {
        this.googleSignIn = googleSignIn;
    }

    public List<String> getPurchasedCourses() {
        return purchasedCourses;
    }

    public void setPurchasedCourses(List<String> purchasedCourses) {
        this.purchasedCourses = purchasedCourses;
    }

    private boolean googleSignIn;
    private List<String> purchasedCourses;
    // Promo fields
    private String appliedPromoCode;
    private Long promoAppliedAt;

    // Password reset fields (optional) - stored in Firestore when a reset is requested
    private String passwordResetToken;
    private Long passwordResetExpiry;

    // Constructors, Getters and Setters
    public User() {}

    public String getAppliedPromoCode() { return appliedPromoCode; }
    public void setAppliedPromoCode(String appliedPromoCode) { this.appliedPromoCode = appliedPromoCode; }
    public Long getPromoAppliedAt() { return promoAppliedAt; }
    public void setPromoAppliedAt(Long promoAppliedAt) { this.promoAppliedAt = promoAppliedAt; }

    public String getPasswordResetToken() { return passwordResetToken; }
    public void setPasswordResetToken(String passwordResetToken) { this.passwordResetToken = passwordResetToken; }
    public Long getPasswordResetExpiry() { return passwordResetExpiry; }
    public void setPasswordResetExpiry(Long passwordResetExpiry) { this.passwordResetExpiry = passwordResetExpiry; }
}

