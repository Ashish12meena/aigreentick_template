package com.aigreentick.services.template.service.impl.contact;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.model.contact.ChatContacts;
import com.aigreentick.services.template.model.contact.ContactAttributes;
import com.aigreentick.services.template.repository.contact.ChatContactsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatContactServiceImpl {
    private final ChatContactsRepository chatContactRepo;

    // ============================================================================
    // ADD this new method to ChatContactServiceImpl.java
    // ============================================================================

    /**
     * Ensures contacts exist and returns a map of mobile -> contactId.
     * Creates missing contacts, returns IDs for all (existing + newly created).
     *
     * @param userId        User ID for contact ownership
     * @param mobileNumbers List of phone numbers
     * @param countryId     Country ID for new contacts
     * @return Map of mobile number -> contact ID
     */
    @Transactional
    public Map<String, Long> ensureContactsExistAndGetIds(Long userId, List<String> mobileNumbers, Long countryId) {
        log.info("Ensuring {} contacts exist and collecting IDs for userId: {}", mobileNumbers.size(), userId);

        Map<String, Long> mobileToContactId = new HashMap<>();

        // 1. Fetch existing contacts
        List<ChatContacts> existingContacts = chatContactRepo
                .findByUserIdAndMobileInAndDeletedAtIsNull(userId, mobileNumbers);

        // Collect existing mobile -> contactId
        for (ChatContacts contact : existingContacts) {
            mobileToContactId.put(contact.getMobile(), contact.getId().longValue());
        }

        Set<String> existingMobiles = mobileToContactId.keySet();

        // 2. Find missing mobiles
        List<String> missingMobiles = mobileNumbers.stream()
                .filter(mobile -> !existingMobiles.contains(mobile))
                .toList();

        // 3. Create missing contacts and collect their IDs
        if (!missingMobiles.isEmpty()) {
            log.info("Creating {} missing contacts", missingMobiles.size());

            String countryIdStr = countryId != null ? countryId.toString() : "91";

            List<ChatContacts> newContacts = missingMobiles.stream()
                    .map(mobile -> {
                        ChatContacts contact = new ChatContacts();
                        contact.setUserId(userId);
                        contact.setMobile(mobile);
                        contact.setName("Contact " + mobile);
                        contact.setCountryId(countryIdStr);
                        contact.setStatus((byte) 1);
                        contact.setAllowedBroadcast(true);
                        contact.setAllowedSms(false);
                        return contact;
                    })
                    .toList();

            // Save and get back with IDs
            List<ChatContacts> savedContacts = chatContactRepo.saveAll(newContacts);

            // Collect new mobile -> contactId
            for (ChatContacts saved : savedContacts) {
                mobileToContactId.put(saved.getMobile(), saved.getId().longValue());
            }

            log.info("Created {} new contacts with IDs", savedContacts.size());
        }

        log.info("Total contacts with IDs: {}", mobileToContactId.size());
        return mobileToContactId;
    }

    /**
     * Ensures contacts exist for all given phone numbers.
     * Creates missing contacts without fetching parameters.
     * Called after reports are created for every send attempt.
     *
     * @param userId        User ID for contact ownership
     * @param mobileNumbers List of phone numbers to ensure exist
     * @param countryId     Country ID for new contacts
     */
    @Transactional
    public void ensureContactsExist(Long userId, List<String> mobileNumbers, Long countryId) {
        log.info("Ensuring {} contacts exist for userId: {}", mobileNumbers.size(), userId);

        // Fetch existing contacts
        List<ChatContacts> existingContacts = chatContactRepo
                .findByUserIdAndMobileInAndDeletedAtIsNull(userId, mobileNumbers);

        Set<String> existingMobiles = existingContacts.stream()
                .map(ChatContacts::getMobile)
                .collect(Collectors.toSet());

        // Find missing mobiles
        List<String> missingMobiles = mobileNumbers.stream()
                .filter(mobile -> !existingMobiles.contains(mobile))
                .toList();

        // Create missing contacts
        if (!missingMobiles.isEmpty()) {
            log.info("Creating {} missing contacts", missingMobiles.size());
            createContactsInBulk(missingMobiles, userId, countryId);
        } else {
            log.debug("All {} contacts already exist", mobileNumbers.size());
        }
    }

    /**
     * Fetches contact attributes for parameter resolution.
     * Returns a map of mobile -> (attributeKey -> attributeValue)
     *
     * @param userId        User ID
     * @param mobileNumbers List of phone numbers
     * @param attributeKeys List of attribute keys to fetch
     * @return Map of mobile to attribute map
     */
    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> getContactAttributes(
            Long userId,
            List<String> mobileNumbers,
            List<String> attributeKeys) {

        log.info("Fetching attributes for {} contacts with {} keys",
                mobileNumbers.size(), attributeKeys.size());

        if (mobileNumbers.isEmpty()) {
            return new HashMap<>();
        }

        // Fetch existing contacts with attributes
        List<ChatContacts> contacts = chatContactRepo
                .findByUserIdAndMobileInAndDeletedAtIsNull(userId, mobileNumbers);

        Map<String, Map<String, String>> result = new HashMap<>();

        for (ChatContacts contact : contacts) {
            Map<String, String> attributeMap = new HashMap<>();

            if (contact.getAttributes() != null) {
                for (ContactAttributes attr : contact.getAttributes()) {
                    if (attributeKeys.contains(attr.getAttribute())) {
                        attributeMap.put(attr.getAttribute(), attr.getAttributeValue());
                    }
                }
            }

            result.put(contact.getMobile(), attributeMap);
        }

        // Ensure all numbers are in result (even without attributes)
        for (String mobile : mobileNumbers) {
            result.computeIfAbsent(mobile, k -> new HashMap<>());
        }

        log.debug("Fetched attributes for {} contacts", result.size());
        return result;
    }

    /**
     * Creates contacts in bulk for missing phone numbers.
     */
    private void createContactsInBulk(List<String> mobileNumbers, Long userId, Long countryId) {
        String countryIdStr = countryId != null ? countryId.toString() : "91";

        List<ChatContacts> contacts = mobileNumbers.stream()
                .map(mobile -> {
                    ChatContacts contact = new ChatContacts();
                    contact.setUserId(userId);
                    contact.setMobile(mobile);
                    contact.setName("Contact " + mobile);
                    contact.setCountryId(countryIdStr);
                    contact.setStatus((byte) 1);
                    contact.setAllowedBroadcast(true);
                    contact.setAllowedSms(false);
                    return contact;
                })
                .toList();

        chatContactRepo.saveAll(contacts);
        log.info("Bulk created {} contacts", contacts.size());
    }

    /**
     * Batch updates contact attributes for bulk operations.
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

        Map<String, ContactAttributes> existingAttributes = contact.getAttributes().stream()
                .collect(Collectors.toMap(
                        ContactAttributes::getAttribute,
                        attr -> attr));

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            ContactAttributes attr = existingAttributes.get(key);
            if (attr != null) {
                attr.setAttributeValue(value);
            } else {
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

    /**
     * Get a single contact by userId and mobile
     */
    @Transactional(readOnly = true)
    public ChatContacts getContactByMobile(Long userId, String mobile) {
        return chatContactRepo.findByUserIdAndMobileAndDeletedAtIsNull(userId, mobile)
                .orElse(null);
    }

    /**
     * Check if contact exists
     */
    @Transactional(readOnly = true)
    public boolean contactExists(Long userId, String mobile) {
        return chatContactRepo.existsByUserIdAndMobileAndDeletedAtIsNull(userId, mobile);
    }

    /**
     * Get all contacts for a user
     */
    @Transactional(readOnly = true)
    public List<ChatContacts> getAllContacts(Long userId) {
        return chatContactRepo.findByUserIdAndDeletedAtIsNull(userId);
    }

    /**
     * Count total contacts for a user
     */
    @Transactional(readOnly = true)
    public long countContacts(Long userId) {
        return chatContactRepo.countByUserIdAndDeletedAtIsNull(userId);
    }
}