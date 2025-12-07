package com.videowebsite.VideoWebsite.contact;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
@CrossOrigin(origins = "*")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    public ResponseEntity<?> submitContact(@RequestBody Contact request) {
        Contact saved = contactService.save(request);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }
}
