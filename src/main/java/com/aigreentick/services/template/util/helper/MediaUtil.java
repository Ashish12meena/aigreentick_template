package com.aigreentick.services.template.util.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;

/**
 * Utility for downloading and storing media files from external URLs.
 * Creates local copies with public URLs.
 */
@Component
@Slf4j
public class MediaUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${media.upload.directory:uploads/media}")
    private String uploadDirectory;

    @Value("${media.base-url:https://aigreentick.com}")
    private String mediaBaseUrl;

    /**
     * Downloads media from URL and stores it locally.
     * 
     * @param sourceUrl URL to download from (e.g., header_handle from Facebook)
     * @param mediaType Type of media (VIDEO, DOCUMENT, IMAGE)
     * @return Public URL of stored file
     */
    public String downloadAndStoreMedia(String sourceUrl, String mediaType) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            log.warn("Source URL is null or blank, skipping download");
            return null;
        }

        try {
            log.info("Downloading media from: {}", sourceUrl);

            // Generate random filename
            byte[] randomBytes = new byte[20];
            SECURE_RANDOM.nextBytes(randomBytes);
            String randomHex = bytesToHex(randomBytes);

            // Determine file extension based on type
            String fileName = generateFileName(randomHex, mediaType);

            // Create upload directory if not exists
            Path uploadPath = Paths.get(uploadDirectory);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("Created upload directory: {}", uploadPath);
            }

            // Download and save file
            Path filePath = uploadPath.resolve(fileName);
            downloadFile(sourceUrl, filePath);

            // Generate and return public URL
            String publicUrl = mediaBaseUrl + "/" + uploadDirectory + "/" + fileName;
            log.info("Media stored successfully: {}", publicUrl);

            return publicUrl;

        } catch (IOException e) {
            log.error("Failed to download and store media from: {}", sourceUrl, e);
            return sourceUrl; // Return original URL as fallback
        }
    }

    /**
     * Downloads file from URL and saves to local path
     */
    private void downloadFile(String sourceUrl, Path targetPath) throws IOException {
        try (InputStream in = new URL(sourceUrl).openStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("File downloaded to: {}", targetPath);
        }
    }

    /**
     * Generates filename based on media type
     */
    private String generateFileName(String randomHex, String mediaType) {
        return switch (mediaType.toUpperCase()) {
            case "VIDEO" -> randomHex + ".mp4";
            case "DOCUMENT" -> randomHex + ".pdf";
            default -> randomHex + ".jpg"; // IMAGE or unknown defaults to jpg
        };
    }

    /**
     * Converts byte array to hexadecimal string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Validates if a URL is accessible
     */
    public boolean isUrlAccessible(String url) {
        try {
            new URL(url).openConnection().connect();
            return true;
        } catch (IOException e) {
            log.warn("URL not accessible: {}", url);
            return false;
        }
    }
}