package com.example.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.dto.DailyPurchaseReport;
import com.example.dto.PurchaseRequest;
import com.example.entity.ErrorResponse;
import com.example.entity.Purchase;
import com.example.service.PurchaseService;

/**
 * REST controller for recording purchases and viewing daily purchase reports.
 */
@RestController
@RequestMapping("/api/purchases")
@CrossOrigin(origins = "http://localhost:3000")
public class PurchaseController {

    @Autowired
    private PurchaseService purchaseService;

    /**
     * Records a purchase (checkout). Publicly accessible so shoppers can buy.
     */
    @PostMapping
    public ResponseEntity<?> recordPurchase(@Validated @RequestBody PurchaseRequest request,
                                            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String message = bindingResult.getFieldErrors().stream()
                    .findFirst()
                    .map(e -> e.getDefaultMessage())
                    .orElse("Invalid request");
            return ResponseEntity.badRequest().body(new ErrorResponse(message, null));
        }
        try {
            Purchase purchase = purchaseService.recordPurchase(request.getProductId(), request.getQuantity());
            return ResponseEntity.status(HttpStatus.CREATED).body(purchase);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage(), null));
        }
    }

    /**
     * ADMIN-only report of daily purchase aggregates per item.
     */
    @GetMapping("/daily-report")
    public ResponseEntity<List<DailyPurchaseReport>> getDailyReport() {
        return ResponseEntity.ok(purchaseService.getDailyReport());
    }
}
