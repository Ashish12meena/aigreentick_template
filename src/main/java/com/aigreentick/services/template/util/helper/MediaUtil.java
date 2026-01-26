package com.aigreentick.services.template.util.helper;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;

public class MediaUtil {

    private static final SecureRandom secureRandom = new SecureRandom();

    public static String getUrl(String url, String type) {
        try {
            // Read remote file as bytes (similar to curl_exec) 
            byte[] data = new URL(url).openStream().readAllBytes();

            // Generate 20 random bytes
            byte[] bytes = new byte[20];
            secureRandom.nextBytes(bytes);

            String hex = bytesToHex(bytes);

            String fileName;
            if ("VIDEO".equals(type)) {
                fileName = hex + ".mp4";
            } else if ("DOCUMENT".equals(type)) {
                fileName = hex + ".pdf";
            } else {
                fileName = hex + ".jpg";
            }

            // Local path (equivalent to public_path("uploads/media/"))
            Path path = Paths.get("public/uploads/media/", fileName);

            // Ensure directory exists
            Files.createDirectories(path.getParent());

            // Write file
            Files.write(path, data);

            // Return public URL
            return "https://aigreentick.com/uploads/media/" + fileName;

        } catch (IOException e) {
            throw new IllegalStateException("Failed to download or save media", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

