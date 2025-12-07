package com.videowebsite.VideoWebsite.Entities;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SignupRequest {

    // Accept multiple common JSON property names for username: userName, username, name
    @JsonProperty("userName")
    @JsonAlias({"username", "name"})
    public String userName;

    public String email;
    public String password;

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getUserName() {
        return userName;
    }

    // Optional promo fields
    private Boolean promoOptIn;
    private String promoCode;

    public Boolean getPromoOptIn() { return promoOptIn; }
    public void setPromoOptIn(Boolean promoOptIn) { this.promoOptIn = promoOptIn; }
    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }

    // Add setters so Jackson or other frameworks can set values if needed
    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public String toString() {
        return "SignupRequest{userName='" + userName + "', email='" + email + "'}";
    }
}
