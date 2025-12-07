package com.videowebsite.VideoWebsite.services;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.videowebsite.VideoWebsite.Entities.PaymentRequest;
import com.videowebsite.VideoWebsite.Entities.PromoCode;
import org.springframework.beans.factory.annotation.Autowired;
import com.videowebsite.VideoWebsite.services.PromoService;
import com.videowebsite.VideoWebsite.Entities.PaymentResponse;
import com.videowebsite.VideoWebsite.Entities.PaymentVerificationRequest;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class PaymentService {
    @Value("${razorpay.key}") 
    private String razorpayKey;
    
    @Value("${razorpay.secret}") 
    private String razorpaySecret;

    private Firestore getFirestore() {
        return FirestoreClient.getFirestore();
    }

    @Autowired
    private PromoService promoService;

    public ResponseEntity<PaymentResponse> createOrder(PaymentRequest request) {
        try {
            RazorpayClient client = new RazorpayClient(razorpayKey, razorpaySecret);
            JSONObject orderRequest = new JSONObject();
            int amountToCharge = request.getAmount();

            // If promo code present, validate and compute discounted amount
            String promoCode = request.getPromoCode();
            PromoCode promo = null;
            if (promoCode != null && !promoCode.trim().isEmpty()) {
                var res = promoService.validateForCourseAndUser(promoCode.toUpperCase(), request.getCourseId(), request.getUserEmail());
                if (!res.valid) {
                    return ResponseEntity.status(400).body(new PaymentResponse("error", "Invalid promo: " + res.message));
                }
                promo = res.promo;
                // apply discount calculation
                if (promo != null) {
                    if ("percent".equalsIgnoreCase(promo.getType())) {
                        int discount = (int) Math.floor((promo.getAmount() / 100.0) * amountToCharge);
                        amountToCharge = Math.max(0, amountToCharge - discount);
                    } else { // flat
                        amountToCharge = Math.max(0, amountToCharge - promo.getAmount());
                    }
                }
            }

            orderRequest.put("amount", amountToCharge * 100); // convert to paise
            orderRequest.put("currency", "INR");

            String rawReceipt = "order_rcptid_" + UUID.randomUUID();
            String receipt = rawReceipt.length() > 40 ? rawReceipt.substring(0, 40) : rawReceipt;
            orderRequest.put("receipt", receipt);

            orderRequest.put("payment_capture", 1);
            
            Order order = client.orders.create(orderRequest);

            // Store order in Firestore
            Firestore db = getFirestore();
            Map<String, Object> orderData = new HashMap<>();
            orderData.put("userEmail", request.getUserEmail());
            orderData.put("courseId", request.getCourseId());
            orderData.put("courseName", request.getCourseName());
            orderData.put("originalAmount", request.getAmount());
            orderData.put("amount", amountToCharge);
            if (promo != null) {
                orderData.put("promoCode", promo.getCode());
                int discount = request.getAmount() - amountToCharge;
                orderData.put("discountAmount", discount);
            }
            orderData.put("orderId", order.get("id"));
            orderData.put("status", "CREATED");
            orderData.put("createdAt", new Date());
            
            db.collection("payment_orders").document(order.get("id").toString()).set(orderData);

            PaymentResponse response = new PaymentResponse(
                order.get("id").toString(),
                order.get("amount").toString(),
                order.get("currency").toString(),
                razorpayKey
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                .body(new PaymentResponse("error", "Failed to create order: " + e.getMessage()));
        }
    }

    public ResponseEntity<PaymentResponse> verifyPayment(PaymentVerificationRequest request) {
        try {
            String orderId = request.getRazorpay_order_id();
            String paymentId = request.getRazorpay_payment_id();
            String signature = request.getRazorpay_signature();
            String payload = orderId + "|" + paymentId;

            String generatedSignature = hmacSha256(payload, razorpaySecret);
            
            if (generatedSignature.equals(signature)) {
                // Update payment status in Firestore
                Firestore db = getFirestore();
                DocumentReference orderRef = db.collection("payment_orders").document(orderId);
                
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", "PAID");
                updates.put("paymentId", paymentId);
                updates.put("verifiedAt", new Date());
                orderRef.update(updates);
                
                // Add course to user's purchased courses
                addCourseToUser(request.getUserEmail(), request.getCourseId());

                // If order included a promoCode, try to apply usage atomically
                try {
                    var orderSnap = orderRef.get().get();
                    if (orderSnap.exists()) {
                        Object promoObj = orderSnap.get("promoCode");
                        if (promoObj != null) {
                            String promoCode = promoObj.toString();
                            // find user id
                            var userDocRef = getUserDocumentRefByEmail(db, request.getUserEmail());
                            String userId = null;
                            if (userDocRef != null) userId = userDocRef.getId();
                            promoService.applyUsageAtomic(promoCode, userId, request.getUserEmail(), "ORDER_PAYMENT", request.getCourseId());
                        }
                    }
                } catch (Exception ex) {
                    // Log but don't fail verification
                    ex.printStackTrace();
                }
                
                return ResponseEntity.ok(new PaymentResponse("success", "Payment verified successfully"));
            } else {
                return ResponseEntity.status(400)
                    .body(new PaymentResponse("error", "Invalid payment signature"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                .body(new PaymentResponse("error", "Payment verification failed: " + e.getMessage()));
        }
    }
    
    public ResponseEntity<Map<String, Object>> checkPaymentStatus(String userEmail, String courseId) {
        try {
            Firestore db = getFirestore();
            // Check if user has purchased the course
            // Users are stored by generated id (user.getId()), so lookup by email
            var userDocRef = getUserDocumentRefByEmail(db, userEmail);
            if (userDocRef == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("hasPaid", false);
                response.put("message", "User not found");
                return ResponseEntity.ok(response);
            }
            var userDoc = userDocRef.get().get();
            
                Map<String, Object> response = new HashMap<>();
                if (userDoc.exists()) {
                    List<String> purchasedCourses = (List<String>) userDoc.get("purchasedCourses");
                    if (purchasedCourses != null && purchasedCourses.contains(courseId)) {
                        response.put("hasPaid", true);
                        response.put("message", "Course already purchased");
                    } else {
                        response.put("hasPaid", false);
                        response.put("message", "Course not purchased");
                    }
                } else {
                    response.put("hasPaid", false);
                    response.put("message", "User not found");
                }
                return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("hasPaid", false);
            errorResponse.put("error", "Failed to check payment status: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    private void addCourseToUser(String userEmail, String courseId) {
        try {
            Firestore db = getFirestore();
            // Find user document by email (users are stored by id)
            var userDocRef = getUserDocumentRefByEmail(db, userEmail);
            if (userDocRef == null) return; // user not found

            var userDoc = userDocRef.get().get();
            if (userDoc.exists()) {
                List<String> purchasedCourses = (List<String>) userDoc.get("purchasedCourses");
                if (purchasedCourses == null) {
                    purchasedCourses = new ArrayList<>();
                }

                if (!purchasedCourses.contains(courseId)) {
                    purchasedCourses.add(courseId);
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("purchasedCourses", purchasedCourses);
                    userDocRef.update(updates);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper: find a Firestore document reference for a user by email.
     * Returns null if not found.
     */
    private com.google.cloud.firestore.DocumentReference getUserDocumentRefByEmail(Firestore db, String email) throws ExecutionException, InterruptedException {
        var snapshot = db.collection("users").whereEqualTo("email", email).get().get();
        if (snapshot == null || snapshot.isEmpty()) return null;
        return snapshot.getDocuments().get(0).getReference();
    }

    private String hmacSha256(String payload, String secret) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(payload.getBytes());
        // Convert to hex string
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}