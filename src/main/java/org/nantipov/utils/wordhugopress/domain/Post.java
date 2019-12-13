package org.nantipov.utils.wordhugopress.domain;

import com.google.common.collect.Multimap;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
public class Post {
    private long id;
    private String author;
    private Timestamp createdAt;
    private String content;
    private String processedContent;
    private String title;
    private boolean isDraft;
    private Timestamp modifiedAt;
    private String sourceName;
    private String thumbnailRawData;
    private String thumbnailFilename;
    private Multimap<String, String> taxonomy;
    private List<ResourceTransferRequest> resourceTransferRequests;
    private String postDirectoryName;
}
