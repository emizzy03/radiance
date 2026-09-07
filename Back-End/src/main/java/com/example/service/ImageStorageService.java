package com.example.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploaded product images on the local filesystem and serves them back.
 *
 * <p>Security notes:
 * <ul>
 *   <li>Only a fixed allow-list of image content types is accepted.</li>
 *   <li>The stored filename is a server-generated UUID plus a safe extension
 *       derived from the validated content type; the client-supplied filename
 *       is never used for the storage path, preventing path traversal and
 *       overwrites.</li>
 *   <li>Reads resolve the requested name against the uploads root and reject
 *       anything that escapes it.</li>
 * </ul>
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    /** Allowed image content types mapped to their canonical file extension. */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    private static final Map<String, MediaType> EXTENSION_MEDIA_TYPES = Map.of(
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "webp", MediaType.parseMediaType("image/webp"),
            "gif", MediaType.IMAGE_GIF);

    private final Path root;

    public ImageStorageService(@Value("${app.uploads.dir:uploads}") String uploadsDir) {
        this.root = Paths.get(uploadsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("Image uploads directory: {}", root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize uploads directory: " + root, e);
        }
    }

    /**
     * Validates and stores the given image, returning its public URL path
     * (e.g. {@code /media/<uuid>.png}).
     *
     * @throws IllegalArgumentException if the file is empty or not an allowed image type
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        String contentType = file.getContentType();
        String extension = contentType == null ? null : ALLOWED_TYPES.get(contentType.toLowerCase());
        if (extension == null) {
            throw new IllegalArgumentException(
                    "Unsupported image type. Allowed types: JPEG, PNG, WEBP, GIF");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path destination = root.resolve(filename).normalize();
        if (!destination.startsWith(root)) {
            throw new IllegalArgumentException("Invalid destination path");
        }
        try {
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store image", e);
        }
        return "/media/" + filename;
    }

    /**
     * Loads a stored image by filename, or {@code null} if it does not exist.
     * The filename is treated as a single path segment; any attempt to traverse
     * outside the uploads root is rejected.
     */
    public Resource load(String filename) {
        if (filename == null || filename.isBlank()
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return null;
        }
        Path file = root.resolve(filename).normalize();
        if (!file.startsWith(root)) {
            return null;
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
        } catch (Exception e) {
            log.debug("Could not load image {}: {}", filename, e.getMessage());
        }
        return null;
    }

    /** Best-effort content type for a stored filename based on its extension. */
    public MediaType contentTypeFor(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            String ext = filename.substring(dot + 1).toLowerCase();
            MediaType type = EXTENSION_MEDIA_TYPES.get(ext);
            if (type != null) {
                return type;
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
