package com.aigreentick.services.template.service.impl.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
public class MediaServiceImpl {

    @Value("${media.upload.directory:uploads/media}")
    private String uploadDirectory;

    /**
     * Get media file by filename
     * 
     * @param filename The name of the file to retrieve
     * @return Resource representing the file
     * @throws RuntimeException if file not found or cannot be read
     */
    public Resource getMediaFile(String filename) {
        log.info("Fetching media file: {}", filename);
        
        try {
            Path filePath = Paths.get(uploadDirectory).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                log.debug("Media file found: {}", filename);
                return resource;
            } else {
                log.error("Media file not found or not readable: {}", filename);
                throw new RuntimeException("File not found or not readable: " + filename);
            }
        } catch (MalformedURLException e) {
            log.error("Malformed URL for file: {}", filename, e);
            throw new RuntimeException("Error retrieving file: " + filename, e);
        }
    }

    /**
     * Check if a media file exists
     * 
     * @param filename The name of the file to check
     * @return true if file exists and is readable, false otherwise
     */
    public boolean mediaFileExists(String filename) {
        try {
            Path filePath = Paths.get(uploadDirectory).resolve(filename).normalize();
            return Files.exists(filePath) && Files.isReadable(filePath);
        } catch (Exception e) {
            log.error("Error checking file existence: {}", filename, e);
            return false;
        }
    }

    /**
     * Get the MIME type of a media file
     * 
     * @param filename The name of the file
     * @return MIME type string or "application/octet-stream" as default
     */
    public String getMediaContentType(String filename) {
        try {
            Path filePath = Paths.get(uploadDirectory).resolve(filename).normalize();
            String contentType = Files.probeContentType(filePath);
            
            if (contentType == null) {
                // Fallback based on file extension
                contentType = determineContentTypeByExtension(filename);
            }
            
            log.debug("Content type for {}: {}", filename, contentType);
            return contentType;
            
        } catch (IOException e) {
            log.warn("Could not determine content type for {}, using default", filename);
            return "application/octet-stream";
        }
    }

    /**
     * Determine content type based on file extension
     */
    private String determineContentTypeByExtension(String filename) {
        String lowerFilename = filename.toLowerCase();
        
        if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFilename.endsWith(".png")) {
            return "image/png";
        } else if (lowerFilename.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerFilename.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lowerFilename.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerFilename.endsWith(".doc")) {
            return "application/msword";
        } else if (lowerFilename.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        
        return "application/octet-stream";
    }

    /**
     * Get file size in bytes
     * 
     * @param filename The name of the file
     * @return file size in bytes, or -1 if error
     */
    public long getFileSize(String filename) {
        try {
            Path filePath = Paths.get(uploadDirectory).resolve(filename).normalize();
            return Files.size(filePath);
        } catch (IOException e) {
            log.error("Error getting file size for: {}", filename, e);
            return -1;
        }
    }

    /**
     * Delete a media file
     * 
     * @param filename The name of the file to delete
     * @return true if deleted successfully, false otherwise
     */
    public boolean deleteMediaFile(String filename) {
        try {
            Path filePath = Paths.get(uploadDirectory).resolve(filename).normalize();
            
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted media file: {}", filename);
                return true;
            } else {
                log.warn("File not found for deletion: {}", filename);
                return false;
            }
        } catch (IOException e) {
            log.error("Error deleting file: {}", filename, e);
            return false;
        }
    }
}