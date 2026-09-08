package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Unit tests for image upload storage invariants: content-type allow-list,
 * server-generated filenames (never the client-supplied name), and path
 * traversal rejection on read.
 */
class ImageStorageServiceTest {

    @TempDir
    Path tempDir;

    private ImageStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new ImageStorageService(tempDir.toString());
    }

    private MockMultipartFile pngNamed(String originalFilename) {
        byte[] bytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        return new MockMultipartFile("file", originalFilename, "image/png", bytes);
    }

    @Test
    void store_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> storage.store(empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void store_rejectsUnsupportedContentType() {
        MockMultipartFile svg = new MockMultipartFile(
                "file", "icon.svg", "image/svg+xml", "<svg/>".getBytes());
        assertThatThrownBy(() -> storage.store(svg))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported image type");
    }

    @Test
    void store_ignoresClientFilenameAndUsesUuidPng() {
        // Client-supplied path segments must never become the stored name.
        String url = storage.store(pngNamed("../../etc/passwd.png"));

        assertThat(url).startsWith("/media/");
        assertThat(url).endsWith(".png");
        assertThat(url).doesNotContain("etc");
        assertThat(url).doesNotContain("passwd");
        assertThat(url).doesNotContain("..");
    }

    @Test
    void store_mapsJpegContentTypeToJpgExtension() {
        MockMultipartFile jpeg = new MockMultipartFile(
                "file", "photo.JPEG", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, 1, 2});
        String url = storage.store(jpeg);
        assertThat(url).endsWith(".jpg");
    }

    @Test
    void load_rejectsPathTraversalFilenames() {
        assertThat(storage.load("..")).isNull();
        assertThat(storage.load("../secret.png")).isNull();
        assertThat(storage.load("foo/bar.png")).isNull();
        assertThat(storage.load("foo\\bar.png")).isNull();
        assertThat(storage.load("")).isNull();
        assertThat(storage.load(null)).isNull();
    }

    @Test
    void load_returnsStoredImageByGeneratedName() {
        String url = storage.store(pngNamed("photo.png"));
        String filename = url.substring("/media/".length());

        Resource resource = storage.load(filename);
        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
    }

    @Test
    void contentTypeFor_usesExtensionAllowList() {
        assertThat(storage.contentTypeFor("a.jpg")).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(storage.contentTypeFor("a.png")).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(storage.contentTypeFor("a.gif")).isEqualTo(MediaType.IMAGE_GIF);
        assertThat(storage.contentTypeFor("a.webp"))
                .isEqualTo(MediaType.parseMediaType("image/webp"));
        assertThat(storage.contentTypeFor("a.exe")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(storage.contentTypeFor("noext")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }
}
