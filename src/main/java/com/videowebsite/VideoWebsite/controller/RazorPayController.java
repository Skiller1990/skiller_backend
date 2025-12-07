package com.videowebsite.VideoWebsite.controller;

import com.videowebsite.VideoWebsite.Entities.PaymentRequest;
import com.videowebsite.VideoWebsite.Entities.PaymentResponse;
import com.videowebsite.VideoWebsite.Entities.PaymentVerificationRequest;
import com.videowebsite.VideoWebsite.services.PaymentService;
import com.videowebsite.VideoWebsite.services.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = "*")
public class RazorPayController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/create-order")
    public ResponseEntity<PaymentResponse> createOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PaymentRequest paymentRequest) {

        // Check Authorization header
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(new PaymentResponse("error", "User not authenticated"));
        }

        String token = authorization.substring(7);
        try {
            String emailFromToken = jwtService.extractUserName(token);
            if (emailFromToken == null || !emailFromToken.equals(paymentRequest.getUserEmail())) {
                return ResponseEntity.status(403)
                        .body(new PaymentResponse("error", "User not authorized to perform this purchase"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(new PaymentResponse("error", "Invalid or expired token"));
        }

        return paymentService.createOrder(paymentRequest);
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentResponse> verifyPayment(@RequestBody PaymentVerificationRequest verificationRequest) {
        return paymentService.verifyPayment(verificationRequest);
    }
    
    @GetMapping("/check-status")
    public ResponseEntity<Map<String, Object>> checkPaymentStatus(
            @RequestParam String userEmail, 
            @RequestParam String courseId) {
        return paymentService.checkPaymentStatus(userEmail, courseId);
    }
}
