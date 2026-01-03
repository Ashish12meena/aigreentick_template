package com.aigreentick.services.template.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.model.Country;
import com.aigreentick.services.template.repository.CountryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CountryServiceImpl {
    private final CountryRepository countryRepository;

    /**
     * Get country by ID
     */
    public Country getCountryById(Long id) {
        log.debug("Fetching country by ID: {}", id);
        return countryRepository.findActiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("Country not found with ID: " + id));
    }

    /**
     * Get country by mobile code
     */
    public Country getCountryByMobileCode(String mobileCode) {
        log.debug("Fetching country by mobile code: {}", mobileCode);
        return countryRepository.findByMobileCodeAndDeletedAtIsNull(mobileCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Country not found with mobile code: " + mobileCode));
    }

    /**
     * Get country by name
     */
    public Country getCountryByName(String name) {
        log.debug("Fetching country by name: {}", name);
        return countryRepository.findByNameAndDeletedAtIsNull(name)
                .orElseThrow(() -> new IllegalArgumentException("Country not found with name: " + name));
    }

    /**
     * Get all active countries
     */
    public List<Country> getAllCountries() {
        log.debug("Fetching all active countries");
        return countryRepository.findByDeletedAtIsNull();
    }

    /**
     * Search countries by name
     */
    public List<Country> searchCountries(String search) {
        log.debug("Searching countries with term: {}", search);
        return countryRepository.searchByName(search);
    }

    /**
     * Check if country exists by mobile code
     */
    public boolean existsByMobileCode(String mobileCode) {
        return countryRepository.existsByMobileCodeAndDeletedAtIsNull(mobileCode);
    }

    /**
     * Validate country ID
     */
    public void validateCountryId(Long countryId) {
        if (countryId == null) {
            throw new IllegalArgumentException("Country ID cannot be null");
        }
        
        if (!countryRepository.findActiveById(countryId).isPresent()) {
            throw new IllegalArgumentException("Invalid country ID: " + countryId);
        }
    }

    public Country save(Country countrt) {
        countrt.setCreatedAt(LocalDateTime.now());
        countrt.setUpdatedAt(LocalDateTime.now());
        return countryRepository.save(countrt);
    }
}