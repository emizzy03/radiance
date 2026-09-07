package com.example.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.service.ImageStorageService;

import java.util.concurrent.TimeUnit;

/**
 * Publicly serves stored product images uploaded through the admin UI.
 * Registered at {@code /media/**} and permitted anonymously in
 * {@link com.example.config.SecurityConfig}.
 */
@RestController
public class MediaController {

    @Autowired
    private ImageStorageService imageStorageService;

    @GetMapping("/media/{filename}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) {
        Resource resource = imageStorageService.load(filename);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(imageStorageService.contentTypeFor(filename))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(resource);
    }
}
