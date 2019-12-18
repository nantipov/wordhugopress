package org.nantipov.utils.wordhugopress.domain;

import lombok.Data;

@Data
public class Reference {
    private final String resourceLocation;
    private final String text;
    private final ResourceTransferRequest resourceTransferRequest;
}
