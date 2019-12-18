package org.nantipov.utils.wordhugopress.components;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import net.gcardone.junidecode.Junidecode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.nantipov.utils.wordhugopress.config.SourcesSettings;
import org.nantipov.utils.wordhugopress.domain.Post;
import org.nantipov.utils.wordhugopress.domain.Reference;
import org.nantipov.utils.wordhugopress.domain.ResourceTransferRequest;
import org.nantipov.utils.wordhugopress.tools.Utils;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;

@Component
@Slf4j
public class PostItemProcessor implements ItemProcessor<Post, Post> {
    private static final Pattern THUMBNAIL_FILE_PATTERN = Pattern.compile("s:4:\"file\";s:\\d+:\"([\\d\\w-_./]+)\"");
    private static final String WORDPRESS_CONTENT_PATH = "wp-content/uploads/";

    private final SourcesSettings sourcesSettings;

    public PostItemProcessor(SourcesSettings sourcesSettings) {
        this.sourcesSettings = sourcesSettings;
    }

    @Override
    public Post process(Post post) {
        post.setPostDirectoryName(
                getPostDirectoryName(post.getTitle(), sourcesSettings.getSources().get(post.getSourceName()))
        );
        post.setResourceTransferRequests(new ArrayList<>());
        addCoverFile(post);
        processContent(post);
        return post;
    }

    private void processContent(Post post) {
        StringBuilder contentBuilder = new StringBuilder();
        Document document = Jsoup.parse(post.getContent());
        document.childNodes().forEach(node -> processContentNode(node, contentBuilder, post));
        post.setProcessedContent(contentBuilder.toString());
    }

    private void processContentNode(Node node, StringBuilder contentBuilder, Post post) {
        if (node instanceof TextNode) {
            contentBuilder.append(((TextNode) node).text());
        } else if (node instanceof Element) {
            processContentHtmlElement((Element) node, contentBuilder, post);
        } else {
            contentBuilder.append(node);
        }
    }

    private void processContentHtmlElement(Element element, StringBuilder contentBuilder, Post post) {
        switch (element.tagName().toLowerCase()) {
            case "ul":
            case "ol":
                contentBuilder.append("\n");
                element.children().forEach(listItem -> processContentHtmlElement(listItem, contentBuilder, post));
                contentBuilder.append("\n");
                break;
            case "li":
                if (element.hasParent() && element.parent().tagName().toLowerCase().equals("ol")) {
                    contentBuilder.append("1. ");
                } else {
                    contentBuilder.append("- ");
                }
                element.childNodes().forEach(node -> processContentNode(node, contentBuilder, post));
                contentBuilder.append("\n");
                break;
            case "a":
                Element firstInnerElement = element.children().first();
                if (firstInnerElement != null && firstInnerElement.tagName().toLowerCase().equals("img")) {
                    processContentHtmlElement(firstInnerElement, contentBuilder, post);
                } else {
                    String href = nullToEmpty(element.attr("href"));
                    String text = element.text();
                    Reference reference = adjustReference(new Reference(href, text, null));
                    if (reference.getResourceTransferRequest() != null) {
                        post.getResourceTransferRequests().add(reference.getResourceTransferRequest());
                    }
                    contentBuilder.append(
                            String.format("[%s](%s)",
                                          Objects.toString(reference.getText(), reference.getResourceLocation()),
                                          reference.getResourceLocation()
                            )
                    );
                }
                break;
            case "img":
                String src = element.attr("src");
                String alt = element.attr("alt");
                Reference reference = adjustReference(new Reference(src, alt, null));
                if (reference.getResourceTransferRequest() != null) {
                    post.getResourceTransferRequests().add(reference.getResourceTransferRequest());
                }
                contentBuilder.append(
                        String.format("![%s](%s)",
                                      nullToEmpty(reference.getText()),
                                      reference.getResourceLocation()
                        )
                );
                break;
            case "pre":
                contentBuilder.append("\n```\n")
                              .append(element.text())
                              .append("\n```\n");
                break;
            case "br":
                contentBuilder.append("\n");
                break;
            case "p":
                contentBuilder.append("\n");
                element.childNodes().forEach(node -> processContentNode(node, contentBuilder, post));
                contentBuilder.append("\n");
                break;
            default:
                element.childNodes().forEach(node -> processContentNode(node, contentBuilder, post));
                break;
        }
    }

    private Reference adjustReference(Reference reference) {
        return sourcesSettings.getSources()
                              .values()
                              .stream()
                              .filter(source -> reference.getResourceLocation()
                                                         .startsWith(source.getWordpressRemoteBaseUrl())
                              )
                              .findAny()
                              .map(source -> adjustInternalReference(reference, source))
                              .orElse(reference);
    }

    private Reference adjustInternalReference(Reference reference, SourcesSettings.Source source) {
        try {
            URL url = new URL(reference.getResourceLocation());
            if (url.getPath() != null &&
                (url.getPath().endsWith("/") || !url.getPath().contains(WORDPRESS_CONTENT_PATH))) {
                // link to the post
                String postName = URLDecoder.decode(url.getPath(), Charsets.UTF_8.name());
                return new Reference(
                        "../" + getPostDirectoryName(postName, source),
                        Objects.toString(reference.getText(), postName),
                        null
                );
            } else {
                // link to the file
                return resourceTransferRequest(reference.getResourceLocation(), source)
                        .map(req -> new Reference(req.getLocalFilename(), reference.getText(), req))
                        .orElse(reference);
            }
        } catch (MalformedURLException | UnsupportedEncodingException e) {
            log.error("Could not adjust the link {}", reference.getResourceLocation(), e);
        }
        return reference;
    }

    private void addCoverFile(Post post) {
        SourcesSettings.Source source = sourcesSettings.getSources().get(post.getSourceName());
        if (source != null) {
            getThumbnailFilename(post.getThumbnailRawData())
                    .flatMap(thumbnailFilename -> resourceTransferRequest(thumbnailFilename, source))
                    .ifPresent(resource -> {
                        post.setThumbnailFilename(resource.getLocalFilename());
                        post.getResourceTransferRequests().add(resource);
                    });
        }
    }

    private Optional<String> getThumbnailFilename(String thumbnailRawData) {
        if (!isNullOrEmpty(thumbnailRawData)) {
            Matcher matcher = THUMBNAIL_FILE_PATTERN.matcher(thumbnailRawData);
            String shortestFilename = null;
            while (matcher.find()) {
                String group = matcher.group(1);
                if (shortestFilename == null || shortestFilename.length() > group.length()) {
                    shortestFilename = group;
                }
            }
            return Optional.ofNullable(shortestFilename);
        }
        return Optional.empty();
    }

    private Optional<ResourceTransferRequest> resourceTransferRequest(String resourceLocation,
                                                                      SourcesSettings.Source postSource) {
        return ImmutableList.<SourcesSettings.Source>builder()
                .addAll(
                        sourcesSettings.getSources().values()
                )
                .add(postSource)
                .build()
                .stream()
                .filter(source ->
                                resourceLocation.startsWith(source.getWordpressRemoteBaseUrl()) ||
                                source.equals(postSource)
                )
                .findFirst()
                .flatMap(source -> resourceTransferRequestFromSource(resourceLocation, source));
    }

    private Optional<ResourceTransferRequest> resourceTransferRequestFromSource(String resourceLocation,
                                                                                SourcesSettings.Source resourceSource) {
        URI resourceURI = URI.create(resourceLocation);
        String resourcePath = resourceURI.getPath();
        if (isNullOrEmpty(resourcePath)) {
            return Optional.empty();
        }

        return Utils.orElseOptional(
                Optional.ofNullable(resourceSource)
                        .map(SourcesSettings.Source::getWordpressHome)
                        .flatMap(wordpressHome ->
                                         checkPathsExistence(
                                                 getPathFromAlternativeLocation(resourcePath, resourceSource),
                                                 getPathFromAlternativeLocation(WORDPRESS_CONTENT_PATH + resourcePath,
                                                                                resourceSource)
                                         )
                        )
                        .map(path ->
                                     new ResourceTransferRequest(
                                             path.toUri(),
                                             adjustFilename(path.getFileName().toString(), resourceSource)
                                     )
                        ),
                Optional.ofNullable(resourceSource)
                        .map(SourcesSettings.Source::getWordpressRemoteBaseUrl)
                        .map(Strings::nullToEmpty)
                        .filter(resourceLocation::startsWith)
                        .map(baseUrl ->
                                     new ResourceTransferRequest(
                                             resourceURI,
                                             adjustFilename(Paths.get(resourcePath).getFileName().toString(),
                                                            resourceSource)
                                     )
                        )
        );
    }

    private Path getPathFromAlternativeLocation(String alternativeLocation, SourcesSettings.Source resourceSource) {
        // replace multi-slash with one
        String location = alternativeLocation.replaceAll("/{2,}", "/");
        String[] subPaths = location.split("/");
        Path path = resourceSource.getWordpressHome(); //TODO: optional `WordpressHome`
        int subPathsIndex = 0;
        while (subPathsIndex < subPaths.length) {
            path = path.resolve(subPaths[subPathsIndex++]);
        }
        return path;
    }

    private Optional<Path> checkPathsExistence(Path... paths) {
        return Stream.of(paths)
                     .filter(Files::exists)
                     .findFirst();
    }

    private String adjustFilename(String filename, SourcesSettings.Source source) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > -1 && !isNullOrEmpty(source.getTargetResourceSuffix())) {
            return filename.substring(0, dotIndex) + source.getTargetResourceSuffix() +
                   filename.substring(dotIndex);
        }
        return filename;
    }

    private String getPostDirectoryName(String title, SourcesSettings.Source source) {
        if (source != null) {
            title += nullToEmpty(source.getTargetResourceSuffix());
        }
        String output = Junidecode.unidecode(title.trim());
        output = CharMatcher.whitespace().replaceFrom(output, '-');
        output = CharMatcher.forPredicate(Character::isLetter)
                            .or(CharMatcher.forPredicate(Character::isDigit))
                            .or(CharMatcher.is('-'))
                            .retainFrom(output);
        return output.toLowerCase();
    }
}
