package com.aigreentick.services.template.repository.common;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.common.Country;

@Repository
public interface CountryRepository extends JpaRepository<Country, Long> {

    /**
     * Find country by mobile code
     */
    Optional<Country> findByMobileCodeAndDeletedAtIsNull(String mobileCode);

    /**
     * Find country by name
     */
    Optional<Country> findByNameAndDeletedAtIsNull(String name);

    /**
     * Find all active countries
     */
    List<Country> findByDeletedAtIsNull();

    /**
     * Check if country exists by mobile code
     */
    boolean existsByMobileCodeAndDeletedAtIsNull(String mobileCode);

    /**
     * Search countries by name (case-insensitive)
     */
    @Query("SELECT c FROM Country c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "AND c.deletedAt IS NULL")
    List<Country> searchByName(@Param("search") String search);

    /**
     * Find country by ID (non-deleted only)
     */
    @Query("SELECT c FROM Country c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Country> findActiveById(@Param("id") Long id);
}