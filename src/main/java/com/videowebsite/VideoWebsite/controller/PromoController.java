package com.videowebsite.VideoWebsite.controller;

import com.videowebsite.VideoWebsite.Entities.PromoCode;
import com.videowebsite.VideoWebsite.services.PromoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PromoController {

    @Autowired
    private PromoService promoService;

    // Admin: create a promo
    @PostMapping("/admin/promocodes")
    public ResponseEntity<?> createPromo(@RequestBody PromoCode promo) {
        try {
            System.out.println("Admin create promo called with: " + promo);
            if (promo.getCode() == null || promo.getCode().isBlank()) return ResponseEntity.badRequest().body("code is required");
            String key = promo.getCode().trim().toUpperCase();
            // ensure the promo object includes the code and createdAt
            promo.setCode(key);
            if (promo.getCreatedAt() == null) promo.setCreatedAt(System.currentTimeMillis());
            if (promo.getUses() == null) promo.setUses(0);
            // store in Firestore
            var db = com.google.firebase.cloud.FirestoreClient.getFirestore();
            db.collection("promoCodes").document(key).set(promo).get();
            return ResponseEntity.ok(Map.of("message", "Promo created", "code", key));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: list promos
    @GetMapping("/admin/promocodes")
    public ResponseEntity<?> listPromos() {
        try {
            var db = com.google.firebase.cloud.FirestoreClient.getFirestore();
            var snaps = db.collection("promoCodes").get().get();
            List<Object> result = new ArrayList<>();
            for (var doc : snaps.getDocuments()) {
                var p = doc.toObject(PromoCode.class);
                // compute usage stats
                var usages = db.collection("promoUsages").whereEqualTo("promoCode", p.getCode()).get().get();
                int total = 0;
                int signupCount = 0;
                int orderCount = 0;
                if (usages != null) {
                    for (var u : usages.getDocuments()) {
                        total++;
                        Object reason = u.get("reason");
                        if (reason != null && "SIGNUP".equals(reason.toString())) signupCount++;
                        if (reason != null && "ORDER_PAYMENT".equals(reason.toString())) orderCount++;
                    }
                }
                var map = Map.of(
                    "promo", p,
                    "totalUses", total,
                    "signupCount", signupCount,
                    "orderCount", orderCount
                );
                result.add(map);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: delete promo
    @DeleteMapping("/admin/promocodes/{code}")
    public ResponseEntity<?> deletePromo(@PathVariable String code) {
        try {
            System.out.println("Admin delete promo called for code: " + code);
            var db = com.google.firebase.cloud.FirestoreClient.getFirestore();
            db.collection("promoCodes").document(code.toUpperCase()).delete().get();
            return ResponseEntity.ok(Map.of("message", "deleted"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: update promo (partial/merge)
    @PutMapping("/admin/promocodes/{code}")
    public ResponseEntity<?> updatePromo(@PathVariable String code, @RequestBody PromoCode promo) {
        try {
            String key = code.trim().toUpperCase();
            var db = com.google.firebase.cloud.FirestoreClient.getFirestore();
            // ensure code field matches path
            promo.setCode(key);
            // merge provided fields into existing document
            db.collection("promoCodes").document(key).set(promo, com.google.cloud.firestore.SetOptions.merge()).get();
            return ResponseEntity.ok(Map.of("message", "updated", "code", key));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Public: validate promo
    @GetMapping("/promocodes/validate")
    public ResponseEntity<?> validate(@RequestParam String code, @RequestParam(required = false) String courseId, @RequestParam(required = false) String userEmail) {
        try {
            var res = promoService.validateForCourseAndUser(code.toUpperCase(), courseId, userEmail);
            return ResponseEntity.ok(Map.of("valid", res.valid, "message", res.message, "promo", res.promo));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
