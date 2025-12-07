package com.videowebsite.VideoWebsite.Entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GooleSignInResponse {
    @JsonProperty
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
