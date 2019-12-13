package org.nantipov.utils.wordhugopress.components;

import com.google.common.collect.ImmutableMap;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.nantipov.utils.wordhugopress.domain.Post;
import org.nantipov.utils.wordhugopress.domain.ResourceTransferRequest;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PostItemWriter implements ItemWriter<Post> {

    private final Path contentItemDir;
    private final Template template;

    public PostItemWriter(@Value("${app.target.hugo-site-content-items-dir}") Path contentItemDir,
                          Configuration freemarkerConfiguration) throws IOException {
        this.contentItemDir = contentItemDir;
        this.template = freemarkerConfiguration.getTemplate("empty-post.ftl");
    }

    @Override
    public void write(List<? extends Post> items) throws Exception {
        for (Post post : items) {
            writePost(post);
        }
    }

    private void writePost(Post post) throws IOException, TemplateException {
        Path dir = Files.createDirectories(contentItemDir.resolve(post.getPostDirectoryName()));
        Path file = dir.resolve("index.md");
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            Map<String, Object> model = ImmutableMap.of("post", post);
            template.process(model, writer);
        }
        if (post.getResourceTransferRequests() != null) {
            for (ResourceTransferRequest request : post.getResourceTransferRequests()) {
                putLocalFile(request, dir);
            }
        }
    }

    private void putLocalFile(ResourceTransferRequest resourceTransferRequest, Path localDir) {
        Path localFile = localDir.resolve(resourceTransferRequest.getLocalFilename());
        if (Files.exists(localFile)) {
            return;
        }
        try {
            Files.copy(resourceTransferRequest.getFrom().toURL().openStream(), localFile,
                       StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Could not deliver request {}", resourceTransferRequest, e);
        }
    }
}
