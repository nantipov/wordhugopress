package org.nantipov.utils.wordhugopress.domain;

import lombok.Data;

import java.net.URI;

@Data
public class ResourceTransferRequest {
    private final URI from;
    private final String localFilename;
}
