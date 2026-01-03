package com.aigreentick.services.template.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.dto.response.ResponseMessage;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.model.Report;
import com.aigreentick.services.template.service.impl.ReportServiceImpl;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/report")
@RequiredArgsConstructor
@Slf4j
public class ReportController {
    private final ReportServiceImpl reportService;

    @PostMapping
    public ResponseEntity<?> createReport(@Valid @RequestBody Report report) {
        log.info("Creating report for mobile: {}", report.getMobile());
        
        Report savedReport = reportService.save(report);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Report created successfully",
                savedReport));
    }

    @GetMapping("/broadcast/{broadcastId}")
    public ResponseEntity<?> getReportsByBroadcast(
            @PathVariable Long broadcastId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        log.info("Fetching reports for broadcastId: {}", broadcastId);
        
        Page<Report> reports = reportService.getReportsByBroadcastId(broadcastId, page, size);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Reports fetched successfully",
                reports));
    }

    @GetMapping("/user")
    public ResponseEntity<?> getReportsByUser(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Long userId = 1L; // TODO: Get from authentication context
        
        log.info("Fetching reports for userId: {}", userId);
        
        Page<Report> reports = reportService.getReportsByUserId(userId, page, size);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Reports fetched successfully",
                reports));
    }

    @GetMapping("/message/{messageId}")
    public ResponseEntity<?> getReportByMessageId(@PathVariable String messageId) {
        log.info("Fetching report by messageId: {}", messageId);
        
        Report report = reportService.getReportByMessageId(messageId);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Report fetched successfully",
                report));
    }

    @GetMapping("/stats/{broadcastId}")
    public ResponseEntity<?> getDeliveryStats(@PathVariable Long broadcastId) {
        log.info("Fetching delivery stats for broadcastId: {}", broadcastId);
        
        Map<String, Long> stats = reportService.getDeliveryStats(broadcastId);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Statistics fetched successfully",
                stats));
    }

    @GetMapping("/failed/{broadcastId}")
    public ResponseEntity<?> getFailedReports(@PathVariable Long broadcastId) {
        log.info("Fetching failed reports for broadcastId: {}", broadcastId);
        
        List<Report> reports = reportService.getFailedReports(broadcastId);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Failed reports fetched successfully",
                reports));
    }

    @GetMapping("/date-range")
    public ResponseEntity<?> getReportsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        Long userId = 1L; // TODO: Get from authentication context
        
        log.info("Fetching reports between {} and {}", startDate, endDate);
        
        List<Report> reports = reportService.getReportsByDateRange(userId, startDate, endDate);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Reports fetched successfully",
                reports));
    }

    @GetMapping("/mobile/{mobile}")
    public ResponseEntity<?> getReportsByMobile(@PathVariable String mobile) {
        Long userId = 1L; // TODO: Get from authentication context
        
        log.info("Fetching reports for mobile: {}", mobile);
        
        List<Report> reports = reportService.getReportsByMobile(mobile, userId);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Reports fetched successfully",
                reports));
    }

    @PutMapping("/status")
    public ResponseEntity<?> updateStatus(@Valid @RequestBody UpdateStatusRequest request) {
        log.info("Updating status for messageId: {}", request.getMessageId());
        
        Report report = reportService.updateStatus(request.getMessageId(), request.getStatus());
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Status updated successfully",
                report));
    }

    @Data
    public static class UpdateStatusRequest {
        private String messageId;
        private String status;
    }
}