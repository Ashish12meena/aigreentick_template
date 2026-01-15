package com.aigreentick.services.template.util.helper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class FileMetaData {
    private final String fileName;
    private final long fileSize;
    private final String mimeType;
}
