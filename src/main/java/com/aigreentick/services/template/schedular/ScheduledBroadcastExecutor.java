package com.aigreentick.services.template.schedular;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.dto.request.template.csv.SendTemplateByCsvRequestDto;
import com.aigreentick.services.template.dto.request.template.normal.SendTemplateNormalRequestDto;
import com.aigreentick.services.template.enums.BroadcastType;
import com.aigreentick.services.template.model.broadcast.Broadcast;
import com.aigreentick.services.template.service.impl.broadcast.BroadcastServiceImpl;
import com.aigreentick.services.template.service.impl.template.broadcast.SendTemplateByCSVOrchestratorServiceImpl;
import com.aigreentick.services.template.service.impl.template.broadcast.SendTemplateByNormalOrchestratorServiceImpl;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledBroadcastExecutor {
    private final BroadcastServiceImpl broadcastServiceImpl;
    private final SendTemplateByCSVOrchestratorServiceImpl csvOrchestrator;
    private final SendTemplateByNormalOrchestratorServiceImpl normalOrchestrator;
    private final ObjectMapper objectMapper;

    /**
     * Execute a scheduled broadcast.
     * Updates status and delegates to appropriate orchestrator.
     */
    @Transactional
    public void executeBroadcast(Broadcast broadcast) {
        log.info("Executing scheduled broadcast: {} (source: {})",
                broadcast.getId(), broadcast.getSource());

        try {
            BroadcastType broadcastType = broadcast.getBroadcastType();
            if (broadcastType == null) {
                // Fallback: try to determine from data
                broadcastType = determineSourceFromData(broadcast);
            }

            if (BroadcastType.CSV.equals(broadcastType)) {
                executeAsCsvBroadcast(broadcast);
            } else if (BroadcastType.NORMAL.equals(broadcastType)) {
                executeAsNormalBroadcast(broadcast);
            } else {
                log.error("Unknown broadcast source: {} for broadcastId: {}",
                        broadcastType, broadcast.getId());
                markBroadcastAsFailed(broadcast, "Unknown broadcast source: " + broadcastType);
            }
        } catch (Exception e) {
            log.error("Failed to execute broadcast: {}", broadcast.getId(), e);
            markBroadcastAsFailed(broadcast, "Execution failed: " + e.getMessage());
        }
    }

    /**
     * Execute as CSV broadcast by reconstructing request from stored data.
     */
    private void executeAsCsvBroadcast(Broadcast broadcast) {
        log.info("Executing as CSV broadcast: {}", broadcast.getId());

        try {
            // Reconstruct CSV request from broadcast data
            SendTemplateByCsvRequestDto request = reconstructCsvRequest(broadcast);

            // Execute through CSV orchestrator
            // Note: orchestrator will handle status updates, report creation, etc.
            csvOrchestrator.broadcastTemplate(request, broadcast.getUserId());

            log.info("CSV broadcast {} dispatched successfully", broadcast.getId());

        } catch (Exception e) {
            log.error("Failed to execute CSV broadcast: {}", broadcast.getId(), e);
            throw new RuntimeException("CSV broadcast execution failed", e);
        }
    }

    /**
     * Execute as Normal broadcast by reconstructing request from stored data.
     */
    private void executeAsNormalBroadcast(Broadcast broadcast) {
        log.info("Executing as Normal broadcast: {}", broadcast.getId());

        try {
            // Reconstruct Normal request from broadcast data
            SendTemplateNormalRequestDto request = reconstructNormalRequest(broadcast);

            // Execute through Normal orchestrator
            normalOrchestrator.broadcastTemplate(request, broadcast.getUserId());

            log.info("Normal broadcast {} dispatched successfully", broadcast.getId());

        } catch (Exception e) {
            log.error("Failed to execute Normal broadcast: {}", broadcast.getId(), e);
            throw new RuntimeException("Normal broadcast execution failed", e);
        }
    }

    /**
     * Reconstruct CSV request from broadcast stored data.
     * 
     * Critical fields:
     * - requests: Stored serialized request payload (JSON)
     * - numbers: Comma-separated mobile numbers
     * - templateId, campName, countryId from broadcast fields
     */
    private SendTemplateByCsvRequestDto reconstructCsvRequest(Broadcast broadcast) {
        try {
            SendTemplateByCsvRequestDto request;

            // Try to deserialize from 'requests' field first
            if (broadcast.getRequests() != null && !broadcast.getRequests().isBlank()) {
                ObjectMapper mapper = objectMapper.copy()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                request = mapper.readValue(
                        broadcast.getRequests(),
                        SendTemplateByCsvRequestDto.class);

                log.debug("Deserialized CSV request from 'requests' field");
            } else {
                // Fallback: create minimal request from broadcast fields
                request = new SendTemplateByCsvRequestDto();
                log.debug("Created minimal CSV request from broadcast fields");
            }

            // Ensure critical fields are populated from broadcast
            request.setTemplateId(String.valueOf(broadcast.getTemplateId()));
            request.setCampName(broadcast.getCampname());
    

            // Reconstruct mobile numbers from 'numbers' field
            if (broadcast.getNumbers() != null && !broadcast.getNumbers().isBlank()) {
                List<Long> mobiles = Arrays.stream(broadcast.getNumbers().split(","))
                        .map(String::trim)
                        .map(Long::valueOf)
                        .toList();
                request.setMobileNumbers(mobiles);
            }

            // Extract media info from data map
            if (broadcast.getData() != null) {
                Object isMedia = broadcast.getData().get("is_media");
                if (isMedia != null) {
                    request.setIsMedia(Boolean.parseBoolean(isMedia.toString()));
                }
            }

            log.debug("Reconstructed CSV request with {} mobile numbers",
                    request.getMobileNumbers() != null ? request.getMobileNumbers().size() : 0);

            return request;

        } catch (Exception e) {
            log.error("Failed to reconstruct CSV request for broadcast: {}",
                    broadcast.getId(), e);
            throw new RuntimeException("Failed to reconstruct CSV request", e);
        }
    }

    /**
     * Reconstruct Normal request from broadcast stored data.
     * 
     * Critical fields:
     * - requests: Stored serialized request payload (JSON)
     * - numbers: Comma-separated mobile numbers (as strings for Normal)
     * - templateId, campName, countryId from broadcast fields
     */
    private SendTemplateNormalRequestDto reconstructNormalRequest(Broadcast broadcast) {
        try {
            SendTemplateNormalRequestDto request;

            // Try to deserialize from 'requests' field first
            if (broadcast.getRequests() != null && !broadcast.getRequests().isBlank()) {
                ObjectMapper mapper = objectMapper.copy()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                request = mapper.readValue(
                        broadcast.getRequests(),
                        SendTemplateNormalRequestDto.class);

                log.debug("Deserialized Normal request from 'requests' field");
            } else {
                // Fallback: create minimal request from broadcast fields
                request = new SendTemplateNormalRequestDto();
                log.debug("Created minimal Normal request from broadcast fields");
            }

            // Ensure critical fields are populated from broadcast
            request.setTemplateId(String.valueOf(broadcast.getTemplateId()));
            request.setCampName(broadcast.getCampname());

            // Reconstruct mobile numbers from 'numbers' field (as strings)
            if (broadcast.getNumbers() != null && !broadcast.getNumbers().isBlank()) {
                List<String> mobiles = Arrays.asList(broadcast.getNumbers().split(","))
                        .stream()
                        .map(String::trim)
                        .toList();
                request.setMobileNumbers(mobiles);
            }

            // Extract media info from data map
            if (broadcast.getData() != null) {
                Object isMedia = broadcast.getData().get("is_media");
                if (isMedia != null) {
                    request.setIsMedia(Boolean.parseBoolean(isMedia.toString()));
                }
            }

            log.debug("Reconstructed Normal request with {} mobile numbers",
                    request.getMobileNumbers() != null ? request.getMobileNumbers().size() : 0);

            return request;

        } catch (Exception e) {
            log.error("Failed to reconstruct Normal request for broadcast: {}",
                    broadcast.getId(), e);
            throw new RuntimeException("Failed to reconstruct Normal request", e);
        }
    }

    /**
     * Determine source from data map when 'source' field is null.
     */
    private BroadcastType determineSourceFromData(Broadcast broadcast) {
        if (broadcast.getData() != null) {
            return broadcast.getBroadcastType();
        }
        return BroadcastType.NORMAL; // Default fallback
    }

    private void markBroadcastAsFailed(Broadcast broadcast, String errorMessage) {
        try {
            broadcast.setStatus("0"); // FAILED/CANCELLED
            broadcast.setUpdatedAt(LocalDateTime.now());

            // Optionally store error in data map
            if (broadcast.getData() == null) {
                broadcast.setData(new java.util.HashMap<>());
            }
            broadcast.getData().put("error", errorMessage);
            broadcast.getData().put("failed_at", LocalDateTime.now().toString());

            broadcastServiceImpl.save(broadcast);

            log.warn("Marked broadcast {} as failed: {}", broadcast.getId(), errorMessage);

        } catch (Exception e) {
            log.error("Failed to mark broadcast {} as failed", broadcast.getId(), e);
        }
    }

}
