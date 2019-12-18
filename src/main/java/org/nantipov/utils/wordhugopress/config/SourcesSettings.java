package org.nantipov.utils.wordhugopress.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class SourcesSettings {

    private Map<String, Source> sources = new HashMap<>();

    @Data
    public static class Source {
        private Path wordpressHome;
        private String wordpressRemoteBaseUrl;
        private String targetResourceSuffix;
        private DataSourceProperties database = new DataSourceProperties();
        private List<String> tags = new ArrayList<>();
        private List<String> categories = new ArrayList<>();
    }
}
