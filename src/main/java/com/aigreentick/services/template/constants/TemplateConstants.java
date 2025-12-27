package com.aigreentick.services.template.constants;

public class TemplateConstants {
    private TemplateConstants() {

    }

    // --- Mongo DB Field Names (if needed) ---
    public static final class Fields {
        private Fields() {
        }

        public static final String TEMPLATE_ID = "templateId";
        public static final String USER_ID = "userId";
        public static final String STATUS = "status";
        public static final String CREATED_AT = "createdAt";
        public static final String UPDATED_AT = "updatedAt";
    }

    // --- API Paths ---
    public static final class Paths {
        private Paths() {
        }

        public static final String BASE = "/api/v1/templates"; // base other are after this
        public static final String CREATE = "/create";
        public static final String SYNC_MY_TEMPLATES = "/sync-my-templates";
        public static final String UPDATE_STATUS = "/{templateId}/status";
        public static final String MY_TEMPLATES = "/my-templates";
        public static final String TEMPLATE_BY_ID = "/{id}";

        // --- Admin-specific paths ---
        public static final class Admin {
            private Admin() {
            }

            public static final String BASE = "/admin/v1/templates";
            public static final String TOGGLE_INCOMING = "/toggle-incoming";
            public static final String INCOMING_STATUS = "/incoming-status";
        }
    }

    // --- Default Pagination Values ---
    public static final class Defaults {
        private Defaults() {
        }

        public static final int PAGE = 0;
        public static final int SIZE = 10;
    }

    // Messages ---
    public static final class Messages {
        private Messages() {
        }

        public static final String TEMPLATE_NOT_FOUND = "Template not found";
        public static final String TEMPLATE_ID_NOT_FOUND = "Template id not found";
        public static final String TEMPLATE_ID_NULL = "Template ID must not be null";
        public static final String TEMPLATE_EXISTS_MSG = "Template with name '%s' already exists.  ";
        public static final String TEMPLATE_CREATED = "Template created successfully";
        public static final String TEMPLATES_FETCHED = "Templates fetched successfully";
        public static final String TEMPLATE_STATUS_UPDATED = "Template status updated successfully";
    }

}
