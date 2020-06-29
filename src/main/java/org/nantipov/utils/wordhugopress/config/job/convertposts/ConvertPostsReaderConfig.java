package org.nantipov.utils.wordhugopress.config.job.convertposts;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.zaxxer.hikari.HikariDataSource;
import org.nantipov.utils.wordhugopress.config.SourcesSettings;
import org.nantipov.utils.wordhugopress.domain.Post;
import org.nantipov.utils.wordhugopress.tools.CompositeItemReader;
import org.nantipov.utils.wordhugopress.tools.PartitionItemReader;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

@Configuration
public class ConvertPostsReaderConfig {

    private static final Set<String> DRAFT_STATUSES = ImmutableSet.of("draft", "auto-draft");

    static final String BEAN_NAME = "postsReader";

    private final SourcesSettings sourcesSettings;
     
    public ConvertPostsReaderConfig(SourcesSettings sourcesSettings) {
        this.sourcesSettings = sourcesSettings;
    }

    @Bean(BEAN_NAME)
    public ItemReader<Post> reader() {
        return CompositeItemReader.of(
                sourcesSettings.getSources()
                               .entrySet()
                               .stream()
                               .map(entry -> reader(entry.getKey(), entry.getValue()))
                               .collect(Collectors.toList())
        );
    }

    private ItemReader<Post> reader(String sourceName, SourcesSettings.Source source) {        
        return new PartitionItemReader<>(
                new JdbcCursorItemReaderBuilder<Post>()
                        .name("readerDatabase" + sourceName)
                        .dataSource(dataSource(source.getDatabase()))
                        .sql(getSQLQueryPosts(source.getWordpressTablePrefix()))
                        .rowMapper((rs, rowNum) -> post(rs, sourceName))
                        .build(),
                (p1, p2) -> p2.getId() == p1.getId(),
                posts -> {
                    Post firstPost = posts.iterator().next();
                    if (posts.size() > 1) {
                        Multimap<String, String> taxonomy =
                                posts.stream()
                                     .map(Post::getTaxonomy)
                                     .reduce((m1, m2) ->
                                                     ImmutableSetMultimap.<String, String>builder()
                                                             .putAll(m1)
                                                             .putAll(m2)
                                                             .build()
                                     )
                                     .orElseGet(ImmutableMultimap::of);
                        firstPost.setTaxonomy(taxonomy);
                    }
                    return firstPost;
                },
                -1
        );
    }
    
    private String getSQLQueryPosts(String tablePrefix){
    return "SELECT\n" +
        "    p.post_date, p.ID, p.post_modified, p.post_title, p.post_content, p.post_status,\n" +
        "    u.display_name user_displayname,\n" +
        "    tax.taxonomy, tax.term_value,\n" +
        "    thumbnail.thumbnail_data\n" +
        "FROM\n" +
        "    " + tablePrefix + "_posts p\n" +
        "    JOIN " + tablePrefix + "_users u ON (u.ID = p.post_author)\n" +
        "    LEFT JOIN (\n" +
        "        SELECT termtax.taxonomy, term.name term_value, tr.object_id post_id\n" +
        "        FROM " + tablePrefix + "_term_relationships tr, " + tablePrefix + "_term_taxonomy termtax, " + tablePrefix + "_terms term\n" +
        "        WHERE\n" +
        "                termtax.term_taxonomy_id = tr.term_taxonomy_id\n" +
        "                AND term.term_id = termtax.term_id\n" +
        "        ) tax ON (tax.post_id = p.ID)\n" +
        "    LEFT JOIN (\n" +
        "        SELECT pm2.meta_value as thumbnail_data, pm1.post_id\n" +
        "        FROM " + tablePrefix + "_postmeta pm1, " + tablePrefix + "_postmeta pm2\n" +
        "        WHERE\n" +
        "                pm1.meta_key = '_thumbnail_id'\n" +
        "                AND pm2.post_id = pm1.meta_value\n" +
        "                AND pm2.meta_key = '_wp_attachment_metadata'\n" +
        "        ) thumbnail ON (thumbnail.post_id = p.ID)\n" +
        "\n" +
        "WHERE\n" +
        "    p.post_type = 'post'\n" +
        "ORDER BY p.ID";
    }
    
    private DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                         .driverClassName("com.mysql.cj.jdbc.Driver")
                         .type(HikariDataSource.class)
                         .build();
    }

    private Post post(ResultSet rs, String sourceName) throws SQLException {
        Post post = new Post();
        post.setAuthor(rs.getString("user_displayname"));
        post.setCreatedAt(rs.getTimestamp("post_date"));
        post.setId(rs.getLong("ID"));
        post.setModifiedAt(rs.getTimestamp("post_modified"));
        post.setTitle(rs.getString("post_title"));
        post.setContent(rs.getString("post_content"));
        post.setSourceName(sourceName);
        if (rs.getString("taxonomy") != null && rs.getString("term_value") != null) {
            post.setTaxonomy(ImmutableSetMultimap.of(rs.getString("taxonomy"), rs.getString("term_value")));
        } else {
            post.setTaxonomy(ImmutableMultimap.of());
        }
        post.setThumbnailRawData(rs.getString("thumbnail_data"));
        post.setDraft(DRAFT_STATUSES.contains(Strings.nullToEmpty(rs.getString("post_status")).toLowerCase()));
        return post;
    }
}
