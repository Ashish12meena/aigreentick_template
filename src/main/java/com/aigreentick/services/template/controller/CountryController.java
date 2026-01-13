package com.aigreentick.services.template.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.dto.response.ResponseMessage;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.model.Country;
import com.aigreentick.services.template.service.impl.other.CountryServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/country")
@RequiredArgsConstructor
@Slf4j
public class CountryController {
    private final CountryServiceImpl countryService;

    @PostMapping("/add")
    public ResponseEntity<?> addCountry(@RequestBody Country countrt) {
        // log.info("Fetching country by ID: {}", id);
        
        Country country = countryService.save(countrt);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Country added successfully",
                country));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCountryById(@PathVariable Long id) {
        log.info("Fetching country by ID: {}", id);
        
        Country country = countryService.getCountryById(id);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Country fetched successfully",
                country));
    }

    @GetMapping("/mobile-code/{mobileCode}")
    public ResponseEntity<?> getCountryByMobileCode(@PathVariable String mobileCode) {
        log.info("Fetching country by mobile code: {}", mobileCode);
        
        Country country = countryService.getCountryByMobileCode(mobileCode);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Country fetched successfully",
                country));
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<?> getCountryByName(@PathVariable String name) {
        log.info("Fetching country by name: {}", name);
        
        Country country = countryService.getCountryByName(name);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Country fetched successfully",
                country));
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllCountries() {
        log.info("Fetching all countries");
        
        List<Country> countries = countryService.getAllCountries();
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Countries fetched successfully",
                countries));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchCountries(@RequestParam String search) {
        log.info("Searching countries with term: {}", search);
        
        List<Country> countries = countryService.searchCountries(search);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Countries fetched successfully",
                countries));
    }
}