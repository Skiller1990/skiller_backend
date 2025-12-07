package com.videowebsite.VideoWebsite.Entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Setter
@Getter
@RequiredArgsConstructor
public class SignInResponse {
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserResponse getUser() {
        return user;
    }

    public void setUser(UserResponse user) {
        this.user = user;
    }

    private UserResponse user;

    @Setter
    @Getter
    public static class UserResponse{
        String id;
        String email;
        String name;
        List<String> purchases;
        Map<String, Integer> coursesProgress;

        public UserResponse(String id, String userName, String email, List<String> purchases) {
            this.id =id;
            this.name =userName;
            this.email =email;
            this.purchases =purchases;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, Integer> getCoursesProgress() {
            return coursesProgress;
        }

        public void setCoursesProgress(Map<String, Integer> coursesProgress) {
            this.coursesProgress = coursesProgress;
        }
    }
}
