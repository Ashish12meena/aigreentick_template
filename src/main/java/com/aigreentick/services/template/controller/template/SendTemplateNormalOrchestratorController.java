package com.aigreentick.services.template.controller.template;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.constants.TemplateConstants;
import com.aigreentick.services.template.dto.request.template.normal.SendTemplateNormalRequestDto;
import com.aigreentick.services.template.dto.response.common.ResponseMessage;
import com.aigreentick.services.template.dto.response.template.TemplateResponseDto;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.service.impl.template.SendTemplateByNormalOrchestratorServiceImpl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for Normal WhatsApp template broadcast.
 * 
 * Normal broadcast uses:
 * - Comma-separated variables string (same for ALL contacts)
 * - No per-contact variable override for non-carousel
 * - Simpler than CSV flow
 * 
 * Endpoints:
 * - POST /api/v1/template/normal/broadcast - Execute normal broadcast
 * - GET /api/v1/template/normal/sample - Get sample request JSON
 */
@RestController
@RequestMapping("api/v1/template/normal")
@RequiredArgsConstructor
@Slf4j
public class SendTemplateNormalOrchestratorController {

    private final SendTemplateByNormalOrchestratorServiceImpl sendTemplateByNormalOrchestratorServiceImpl;

    /**
     * Broadcast WhatsApp template using Normal flow.
     * 
     * Request body example:
     * {
     *   "templateId": "123",
     *   "campName": "January Sale",
     *   "countryId": 1,
     *   "isMedia": false,
     *   "mobileNumbers": ["919876543210", "919876543211"],
     *   "variables": "John,Premium,50%",
     *   "carouselCards": [
     *     {
     *       "cardIndex": 0,
     *       "imageUrl": "https://example.com/img1.jpg",
     *       "variables": {"1": "Product A", "2": "$99"},
     *       "buttons": [{"type": "URL", "variables": {"1": "sku-123"}}]
     *     }
     *   ]
     * }
     * 
     * @param userId User ID from header
     * @param request Normal broadcast request DTO
     * @return ResponseMessage with broadcast status
     */
    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcast(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody SendTemplateNormalRequestDto request) {

        log.info("Broadcasting template via Normal flow for userId={}, templateId={}, recipients={}", 
                userId, request.getTemplateId(), 
                request.getMobileNumbers() != null ? request.getMobileNumbers().size() : 0);

        TemplateResponseDto response = sendTemplateByNormalOrchestratorServiceImpl.broadcastTemplate(request, userId);

        return ResponseEntity.ok(
                new ResponseMessage<>(
                        ResponseStatus.SUCCESS.name(),
                        TemplateConstants.Messages.TEMPLATE_CREATED,
                        response));
    }

    /**
     * Get sample request JSON for Normal broadcast.
     * Useful for API documentation and testing.
     * 
     * @return Sample request JSON
     */
    @GetMapping("/sample")
    public ResponseEntity<?> getSampleRequest() {
        SampleNormalRequest sample = new SampleNormalRequest();
        
        return ResponseEntity.ok(
                new ResponseMessage<>(
                        ResponseStatus.SUCCESS.name(),
                        "Sample Normal broadcast request",
                        sample));
    }

    /**
     * Inner class representing sample request structure.
     * Used for generating sample JSON response.
     */
    @lombok.Data
    static class SampleNormalRequest {
        private String templateId = "123";
        private String campName = "January Sale Campaign";
        private Integer countryId = 91;
        private Boolean isMedia = false;
        private String mediaType = null;
        private String mediaUrl = null;
        private java.util.List<String> mobileNumbers = java.util.List.of(
                "919876543210", 
                "919876543211", 
                "919876543212"
        );
        private String scheduleDate = null;
        private String variables = "John,Premium Member,50% OFF";
        private java.util.List<SampleCarouselCard> carouselCards = java.util.List.of(
                new SampleCarouselCard(0, "https://example.com/product1.jpg", 
                        java.util.Map.of("1", "Product A", "2", "$99.99"),
                        java.util.List.of(new SampleButton("URL", java.util.Map.of("1", "product-a-123")))),
                new SampleCarouselCard(1, "https://example.com/product2.jpg", 
                        java.util.Map.of("1", "Product B", "2", "$149.99"),
                        java.util.List.of(new SampleButton("URL", java.util.Map.of("1", "product-b-456"))))
        );
        
        @lombok.Data
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        static class SampleCarouselCard {
            private Integer cardIndex;
            private String imageUrl;
            private java.util.Map<String, String> variables;
            private java.util.List<SampleButton> buttons;
        }
        
        @lombok.Data
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        static class SampleButton {
            private String type;
            private java.util.Map<String, String> variables;
        }
    }
}