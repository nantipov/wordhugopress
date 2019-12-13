package org.nantipov.utils.wordhugopress.components;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import lombok.extern.slf4j.Slf4j;
import net.gcardone.junidecode.Junidecode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.nantipov.utils.wordhugopress.config.SourcesSettings;
import org.nantipov.utils.wordhugopress.domain.Post;
import org.nantipov.utils.wordhugopress.domain.ResourceTransferRequest;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public Post process(Post post) throws Exception {
        post.setPostDirectoryName(getPostDirectoryName(post.getTitle()));
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
                Element firstElement = element.children().first();
                if (firstElement != null && firstElement.tagName().toLowerCase().equals("img")) {
                    processContentHtmlElement(firstElement, contentBuilder, post);
                } else {
                    String href = nullToEmpty(element.attr("href"));  //TODO: internal or external
                    String text = element.text();
                    String link = href;
                    SourcesSettings.Source source = sourcesSettings.getSources().get(post.getSourceName());
                    if (source != null && href.startsWith(source.getWordpressRemoteBaseUrl())) {
                        // internal link
                        try {
                            URL url = new URL(href);
                            if (url.getFile() == null || url.getFile().equals(url.getPath())) {
                                // link to the post
                                String postName = URLDecoder.decode(url.getPath(), Charsets.UTF_8.name());
                                link = "../" + getPostDirectoryName(postName);
                                if (isNullOrEmpty(text)) {
                                    text = postName;
                                }
                            }
                        } catch (MalformedURLException | UnsupportedEncodingException e) {
                            log.error("Could not process the link {}", href, e);
                        }
                    }
                    if (isNullOrEmpty(text)) {
                        text = link;
                    }
                    contentBuilder.append(String.format("[%s](%s)", text, link));
                }
                break;
            case "img":
                //TODO: img, deliver file
                //TODO: internal or external
                String src = element.attr("src");
                String alt = element.attr("alt");
                contentBuilder.append(String.format("![%s](%s)", nullToEmpty(alt), src));
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

    private Optional<ResourceTransferRequest> resourceTransferRequest(String filename, SourcesSettings.Source source) {
        Path localWordpressPath = source.getWordpressHome().resolve(filename);
        if (Files.exists(localWordpressPath)) {
            return Optional.of(
                    new ResourceTransferRequest(localWordpressPath.toUri(), localWordpressPath.getFileName().toString())
            );
        } else if (!isNullOrEmpty(source.getWordpressRemoteBaseUrl())) {
            return Optional.of(
                    new ResourceTransferRequest(
                            URI.create(source.getWordpressRemoteBaseUrl()).resolve(WORDPRESS_CONTENT_PATH + filename),
                            localWordpressPath.getFileName().toString()
                    )
            );
        } else {
            return Optional.empty();
        }
    }

    private String getPostDirectoryName(String title) {
        String output = Junidecode.unidecode(title.trim());
        output = CharMatcher.whitespace().replaceFrom(output, '-');
        output = CharMatcher.forPredicate(Character::isLetter)
                            .or(CharMatcher.forPredicate(Character::isDigit))
                            .or(CharMatcher.is('-'))
                            .retainFrom(output);
        return output.toLowerCase();
    }
}
