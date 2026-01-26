package com.aigreentick.services.template.dto.response.template;

/**
 * Statistics returned after syncing templates with Facebook.
 * 
 * @param inserted Number of new templates inserted
 * @param updated Number of existing new_created templates updated with Facebook data
 * @param deleted Number of stale templates soft-deleted
 */
public record TemplateSyncStats(int inserted, int updated, int deleted) {
    
    /**
     * Constructor for backward compatibility (inserted includes both new and updated)
     */
    public TemplateSyncStats(int insertedOrUpdated, int deleted) {
        this(insertedOrUpdated, 0, deleted);
    }
    
    /**
     * Total templates processed (inserted + updated)
     */
    public int totalProcessed() {
        return inserted + updated;
    }
}