package com.aigreentick.services.template.controller.media;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.aigreentick.services.template.service.impl.template.MediaServiceImpl;

import java.util.concurrent.TimeUnit;

/**
 * Controller for serving media files from the upload directory.
 * Handles image, video, and document files uploaded during template sync.
 */
@RestController
@RequestMapping("/uploads/media")
@RequiredArgsConstructor
@Slf4j
public class MediaController {

    private final MediaServiceImpl mediaService;

    /**
     * Serve media file by filename.
     * 
     * Supports:
     * - Images (JPG, PNG, GIF)
     * - Videos (MP4)
     * - Documents (PDF, DOC, DOCX)
     * 
     * URL Pattern: GET /uploads/media/{filename}
     * 
     * Example:
     * GET /uploads/media/abc123def456.jpg
     * GET /uploads/media/video789.mp4
     * 
     * @param filename Name of the file to retrieve
     * @return ResponseEntity with file resource and appropriate headers
     */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        log.info("Request to serve media file: {}", filename);

        try {
            // Security: Prevent directory traversal attacks
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                log.warn("Potential directory traversal attempt: {}", filename);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Check if file exists
            if (!mediaService.mediaFileExists(filename)) {
                log.warn("Media file not found: {}", filename);
                return ResponseEntity.notFound().build();
            }

            // Get the file resource
            Resource resource = mediaService.getMediaFile(filename);

            // Determine content type
            String contentType = mediaService.getMediaContentType(filename);

            // Get file size for Content-Length header
            long fileSize = mediaService.getFileSize(filename);

            // Build response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(fileSize);
            
            // Cache control for better performance
            // Cache for 7 days (media files rarely change)
            headers.setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());

            // For download instead of inline display, uncomment:
            // headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

            log.info("Serving media file: {} (type: {}, size: {} bytes)", 
                    filename, contentType, fileSize);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);

        } catch (Exception e) {
            log.error("Error serving media file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Check if a media file exists (HEAD request for efficient checking).
     * 
     * URL Pattern: HEAD /uploads/media/{filename}
     * 
     * @param filename Name of the file to check
     * @return 200 if exists, 404 if not found
     */
    @RequestMapping(value = "/{filename:.+}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> checkFileExists(@PathVariable String filename) {
        log.debug("Checking existence of media file: {}", filename);

        // Security check
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (mediaService.mediaFileExists(filename)) {
            long fileSize = mediaService.getFileSize(filename);
            String contentType = mediaService.getMediaContentType(filename);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(fileSize);

            return ResponseEntity.ok().headers(headers).build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete a media file (admin only - implement authentication as needed).
     * 
     * URL Pattern: DELETE /uploads/media/{filename}
     * 
     * @param filename Name of the file to delete
     * @return 200 if deleted, 404 if not found
     */
    @DeleteMapping("/{filename:.+}")
    public ResponseEntity<String> deleteFile(@PathVariable String filename) {
        log.info("Request to delete media file: {}", filename);

        // Security check
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            log.warn("Potential directory traversal attempt in delete: {}", filename);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Invalid filename");
        }

        // TODO: Add authentication/authorization check here
        // Example: if (!hasAdminRole()) { return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); }

        boolean deleted = mediaService.deleteMediaFile(filename);

        if (deleted) {
            return ResponseEntity.ok("File deleted successfully: " + filename);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Health check endpoint for media service.
     * 
     * URL Pattern: GET /uploads/media/health
     * 
     * @return Service status
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Media service is running");
    }
}