package com.aigreentick.services.template.service.impl;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.dto.response.PhoneBookResponseDto;
import com.aigreentick.services.template.model.ChatContacts;
import com.aigreentick.services.template.model.ContactAttributes;
import com.aigreentick.services.template.repository.ChatContactsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatContactServiceImpl {
    private final ChatContactsRepository chatContactRepo;

    /**
     * Fetches parameters for given phone numbers from chat_contacts table.
     * Creates missing contacts if they don't exist.
     * 
     * @param mobileNumbers List of phone numbers to fetch/create
     * @param keys List of attribute keys to fetch
     * @param userId User ID for contact ownership
     * @param defaultValue Default value to use when attribute is missing
     * @return PhoneBookResponseDto containing parameter mappings
     */
    @Transactional
    public PhoneBookResponseDto getParamsForPhoneNumbers(
            List<String> mobileNumbers, 
            List<String> keys,
            Long userId, 
            String defaultValue) {
        
        log.info("Fetching params for {} phone numbers with {} keys for userId: {}", 
                mobileNumbers.size(), keys.size(), userId);

        // 1. Fetch existing contacts for this user
        List<ChatContacts> existingContacts = chatContactRepo
                .findByUserIdAndMobileInAndDeletedAtIsNull(userId, mobileNumbers);
        
        log.debug("Found {} existing contacts", existingContacts.size());

        // 2. Identify missing phone numbers
        Set<String> existingMobiles = existingContacts.stream()
                .map(ChatContacts::getMobile)
                .collect(Collectors.toSet());
        
        List<String> missingMobiles = mobileNumbers.stream()
                .filter(mobile -> !existingMobiles.contains(mobile))
                .toList();

        // 3. Bulk create missing contacts
        if (!missingMobiles.isEmpty()) {
            log.info("Creating {} missing contacts", missingMobiles.size());
            List<ChatContacts> newContacts = createContactsInBulk(missingMobiles, userId);
            existingContacts.addAll(newContacts);
        }

        // 4. Build parameter map from contacts and their attributes
        Map<String, Map<String, String>> parameterMap = buildParameterMap(
                existingContacts, keys, defaultValue);

        // 5. Ensure all requested numbers are in the response (even if no attributes)
        ensureAllNumbersPresent(parameterMap, mobileNumbers, keys, defaultValue);

        PhoneBookResponseDto response = new PhoneBookResponseDto();
        response.setData(parameterMap);
        
        log.info("Successfully built parameters for {} phone numbers", parameterMap.size());
        return response;
    }

    /**
     * Creates contacts in bulk for missing phone numbers.
     */
    private List<ChatContacts> createContactsInBulk(List<String> mobileNumbers, Long userId) {
        Timestamp now = Timestamp.from(Instant.now());
        
        List<ChatContacts> contacts = mobileNumbers.stream()
                .map(mobile -> {
                    ChatContacts contact = new ChatContacts();
                    contact.setUserId(userId);
                    contact.setMobile(mobile);
                    contact.setName("Contact " + mobile); // Default name
                    contact.setCountryId("91"); // Default country ID
                    contact.setStatus((byte) 1);
                    contact.setAllowedBroadcast(true);
                    contact.setAllowedSms(false);
                    return contact;
                })
                .toList();

        List<ChatContacts> savedContacts = chatContactRepo.saveAll(contacts);
        log.info("Bulk created {} contacts", savedContacts.size());
        
        return savedContacts;
    }

    /**
     * Builds parameter map from contacts and their attributes.
     * 
     * Structure:
     * {
     *   "9876543210": {
     *     "{{1}}": "John Doe",
     *     "{{2}}": "Premium"
     *   },
     *   "9876543211": {
     *     "{{1}}": "Jane Smith",
     *     "{{2}}": "default"
     *   }
     * }
     */
    private Map<String, Map<String, String>> buildParameterMap(
            List<ChatContacts> contacts,
            List<String> keys,
            String defaultValue) {
        
        Map<String, Map<String, String>> result = new HashMap<>();

        for (ChatContacts contact : contacts) {
            String mobile = contact.getMobile();
            Map<String, String> params = new HashMap<>();

            // Create attribute lookup map for this contact
            Map<String, String> attributeMap = contact.getAttributes().stream()
                    .collect(Collectors.toMap(
                            ContactAttributes::getAttribute,
                            ContactAttributes::getAttributeValue,
                            (v1, v2) -> v1 // In case of duplicates, take first
                    ));

            // Map each key to its value or default
            for (String key : keys) {
                String value = attributeMap.getOrDefault(key, defaultValue);
                params.put(key, value);
            }

            result.put(mobile, params);
        }

        return result;
    }

    /**
     * Ensures all requested phone numbers are present in the result map.
     * Adds missing numbers with default values.
     */
    private void ensureAllNumbersPresent(
            Map<String, Map<String, String>> parameterMap,
            List<String> allNumbers,
            List<String> keys,
            String defaultValue) {
        
        for (String mobile : allNumbers) {
            parameterMap.computeIfAbsent(mobile, k -> {
                Map<String, String> defaultParams = new HashMap<>();
                for (String key : keys) {
                    defaultParams.put(key, defaultValue);
                }
                return defaultParams;
            });
        }
    }

    /**
     * Batch updates contact attributes for bulk operations.
     * Useful for updating contact data from external sources.
     */
    @Transactional
    public void updateContactAttributes(
            Long userId,
            String mobile,
            Map<String, String> attributes) {
        
        ChatContacts contact = chatContactRepo
                .findByUserIdAndMobileAndDeletedAtIsNull(userId, mobile)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Contact not found for mobile: " + mobile));

        // Create attribute lookup for existing attributes
        Map<String, ContactAttributes> existingAttributes = contact.getAttributes().stream()
                .collect(Collectors.toMap(
                        ContactAttributes::getAttribute,
                        attr -> attr
                ));

        // Update or create attributes
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            ContactAttributes attr = existingAttributes.get(key);
            if (attr != null) {
                // Update existing
                attr.setAttributeValue(value);
            } else {
                // Create new
                ContactAttributes newAttr = new ContactAttributes();
                newAttr.setContact(contact);
                newAttr.setAttribute(key);
                newAttr.setAttributeValue(value);
                contact.getAttributes().add(newAttr);
            }
        }

        chatContactRepo.save(contact);
        log.info("Updated attributes for contact: {}", mobile);
    }
}