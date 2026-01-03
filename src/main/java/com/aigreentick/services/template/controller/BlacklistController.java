package com.aigreentick.services.template.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.dto.response.ResponseMessage;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.model.Blacklist;
import com.aigreentick.services.template.service.impl.BlacklistServiceImpl;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/blacklist")
@RequiredArgsConstructor
@Slf4j
public class BlacklistController {
    private final BlacklistServiceImpl blacklistService;

    @PostMapping("/block")
    public ResponseEntity<?> blockNumber(@Valid @RequestBody BlockNumberRequest request) {
        log.info("Blocking number: {}", request.getMobile());
        
        Long userId = 1L; 
        
        Blacklist blacklist = blacklistService.blockNumber(
                userId,
                request.getMobile(),
                request.getCountryId());
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Number blocked successfully",
                blacklist));
    }

    @PutMapping("/unblock")
    public ResponseEntity<?> unblockNumber(@Valid @RequestBody UnblockNumberRequest request) {
        log.info("Unblocking number: {}", request.getMobile());
        
        Long userId = 1L;
        
        Blacklist blacklist = blacklistService.unblockNumber(userId, request.getMobile());
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Number unblocked successfully",
                blacklist));
    }

    @GetMapping("/blocked-numbers")
    public ResponseEntity<?> getBlockedNumbers() {
        Long userId = 1L; 
        
        List<Blacklist> blockedNumbers = blacklistService.getBlockedNumbers(userId);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Blocked numbers fetched successfully",
                blockedNumbers));
    }

    @GetMapping("/count")
    public ResponseEntity<?> getBlockedCount() {
        Long userId = 1L; 
        
        long count = blacklistService.getBlockedCount(userId);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Count fetched successfully",
                count));
    }

    @DeleteMapping("/remove/{mobile}")
    public ResponseEntity<?> removeFromBlacklist(@PathVariable String mobile) {
        log.info("Removing number from blacklist: {}", mobile);
        
        Long userId = 1L;
        
        blacklistService.removeFromBlacklist(userId, mobile);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Number removed from blacklist successfully",
                null));
    }

    @Data
    public static class BlockNumberRequest {
        private String mobile;
        private Long countryId;
    }

    @Data
    public static class UnblockNumberRequest {
        private String mobile;
    }
}