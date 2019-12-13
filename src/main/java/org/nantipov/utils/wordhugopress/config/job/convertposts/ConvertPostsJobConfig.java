package org.nantipov.utils.wordhugopress.config.job.convertposts;

import org.nantipov.utils.wordhugopress.components.PostItemProcessor;
import org.nantipov.utils.wordhugopress.domain.Post;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConvertPostsJobConfig {

    private static final String BEAN_CONVERT_JOB = "convertPostsJob";
    private static final String BEAN_CONVERT_STEP = BEAN_CONVERT_JOB + "_convertPostsStep";

    private final JobBuilderFactory jobs;
    private final StepBuilderFactory steps;

    @Autowired
    public ConvertPostsJobConfig(JobBuilderFactory jobs, StepBuilderFactory steps) {
        this.jobs = jobs;
        this.steps = steps;
    }

    @Bean(BEAN_CONVERT_JOB)
    public Job convertPostsJob(@Qualifier(BEAN_CONVERT_STEP) Step step1) {
        return jobs.get(BEAN_CONVERT_JOB)
                   .incrementer(new RunIdIncrementer())
                   .start(step1)
                   .build();
    }

    @Bean(BEAN_CONVERT_STEP)
    public Step convertStepJob(
            @Qualifier(ConvertPostsReaderConfig.BEAN_NAME) ItemReader<Post> reader,
            @Qualifier(ConvertPostsWriterConfig.BEAN_NAME) ItemWriter<Post> writer,
            PostItemProcessor processor
    ) {
        return steps.get(BEAN_CONVERT_STEP)
                .<Post, Post>chunk(10)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
