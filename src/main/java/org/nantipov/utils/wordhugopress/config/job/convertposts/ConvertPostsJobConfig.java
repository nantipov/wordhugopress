package org.nantipov.utils.wordhugopress.config.job.convertposts;

import org.nantipov.utils.wordhugopress.components.PostItemProcessor;
import org.nantipov.utils.wordhugopress.domain.Post;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class ConvertPostsJobConfig {

    private static final String BEAN_CONVERT_JOB = "convertPostsJob";
    private static final String BEAN_CONVERT_STEP = BEAN_CONVERT_JOB + "_convertPostsStep";

    @Bean(BEAN_CONVERT_JOB)
    public Job convertPostsJob(JobRepository jobRepository, @Qualifier(BEAN_CONVERT_STEP) Step step1) {
        return new JobBuilder(BEAN_CONVERT_JOB, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(step1)
                .build();
    }

    @Bean(BEAN_CONVERT_STEP)
    public Step convertStepJob(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier(ConvertPostsReaderConfig.BEAN_NAME) ItemReader<Post> reader,
            @Qualifier(ConvertPostsWriterConfig.BEAN_NAME) ItemWriter<Post> writer,
            PostItemProcessor processor
    ) {
        return new StepBuilder(BEAN_CONVERT_STEP, jobRepository)
                .<Post, Post>chunk(10, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
