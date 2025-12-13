package com.videowebsite.VideoWebsite.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.videowebsite.VideoWebsite.Entities.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.videowebsite.VideoWebsite.exception.UserNotFoundException;
import com.videowebsite.VideoWebsite.services.UserService;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
public class AuthController {
    @Autowired
    private UserService userService;
//    @Autowired
//    private JwtUtils jwtUtils;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest req) throws ExecutionException, InterruptedException {
        // Avoid printing password to logs. SignupRequest#toString exposes only username and email.
        System.out.println("Sign up called!!! " + req.toString());
        String  user = userService.signup(req);
        SignUpResponse response = new SignUpResponse();
        response.setMessage("User Successfully registered :: " + user);
        System.out.println("Sign completed called!!! " + user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/signin")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        System.out.println("signin up called!!! " + req.toString());
        //String token = userService.login(req);
        //System.out.println("signin up token!!! "+token);
        return ResponseEntity.ok().body(userService.login(req));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteRecord(@RequestBody DeleteRequest request) throws ExecutionException, InterruptedException {
        return userService.deleteUser(request.getEmail());
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleSignUp(@RequestBody Map<String, String> body) throws Exception {
        return userService.googleSignUp(body);
    }

    @GetMapping("/user")
    public ResponseEntity<?> getUserByEmail(@RequestParam String email) {
        try {
            var user = userService.findByEmail(email);
            if (user == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Request password reset. Expects JSON { "email": "user@example.com" }
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Missing email"));
        try {
            userService.requestPasswordReset(email);
            return ResponseEntity.ok(Map.of("message", "Reset email sent if account exists"));
        } catch (UserNotFoundException e) {
            // To avoid enumerating accounts, respond 200 with same message.
            return ResponseEntity.ok(Map.of("message", "Reset email sent if account exists"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reset password. Expects JSON { "token": "...", "newPassword": "..." }
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("newPassword");
        try {
            userService.resetPassword(token, newPassword);
            return ResponseEntity.ok(Map.of("message", "Password reset successful"));
        } catch (ResponseStatusException rse) {
            return ResponseEntity.status(rse.getStatusCode()).body(Map.of("error", rse.getReason()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

//    @PostMapping("/google-signup")
//    public ResponseEntity<?> googleSignUp(@RequestBody Map<String, String> payload) throws Exception {
//        String idToken = payload.get("idToken");
//        GoogleIdToken.Payload googlePayload = GoogleUtil.verifyIdToken(idToken);
//        String email = googlePayload.getEmail();
//        String name = (String) googlePayload.get("name");
//
//        if (userService.existsByEmail(email)) {
//            return ResponseEntity.status(HttpStatus.CONFLICT).body("User already exists");
//        }
//
//        String razorpayOrderId = razorpayService.createOrder(email);
//        return ResponseEntity.ok(Map.of("razorpayOrderId", razorpayOrderId, "user", Map.of("name", name, "email", email)));
//    }
//
//    @PostMapping("/google-confirm")
//    public ResponseEntity<?> googleConfirm(@RequestBody GoogleConfirmRequest request) throws Exception {
//        GoogleIdToken.Payload payload = GoogleUtil.verifyIdToken(request.getIdToken());
//        if (!razorpayService.verifySignature(request)) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid payment signature");
//        }
//        User user = userService.createUser(payload.getEmail(), (String) payload.get("name"));
//        String jwt = jwtService.generateToken(user);
//        return ResponseEntity.ok(Map.of("token", jwt));
//    }
//
//    @PostMapping("/google-signin")
//    public ResponseEntity<?> googleSignIn(@RequestBody Map<String, String> payload) throws Exception {
//        GoogleIdToken.Payload googlePayload = GoogleUtil.verifyIdToken(payload.get("idToken"));
//        String email = googlePayload.getEmail();
//        Optional<User> user = userService.findByEmail(email);
//        if (user.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
//        String jwt = jwtService.generateToken(user.get());
//        return ResponseEntity.ok(Map.of("token", jwt));
//    }
//
//    public static GoogleIdToken.Payload verifyIdToken(String idTokenString) throws Exception {
//        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new JacksonFactory())
//                .setAudience(Collections.singletonList("YOUR_CLIENT_ID"))
//                .build();
//
//        GoogleIdToken idToken = verifier.verify(idTokenString);
//        if (idToken == null) throw new IllegalArgumentException("Invalid ID token");
//        return idToken.getPayload();
//    }
}
