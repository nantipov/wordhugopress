package org.nantipov.utils.wordhugopress.config.job.convertposts;

import org.nantipov.utils.wordhugopress.components.PostItemWriter;
import org.nantipov.utils.wordhugopress.domain.Post;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConvertPostsWriterConfig {

    static final String BEAN_NAME = "postsWriter";

    @Bean(BEAN_NAME)
    public ItemWriter<Post> writer(PostItemWriter postItemWriter) {
        return postItemWriter;
    }
}
