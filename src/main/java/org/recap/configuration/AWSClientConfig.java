package org.recap.configuration;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AWSClientConfig {

    @Bean
    public AmazonS3 getAwsClient() {
        AWSCredentials credentials = new BasicAWSCredentials(
                "AKIA45ITQHSNWAWZU6AQ",
                "hg90O8iyPVGbHr1a7qc5+KxqtjRlWHAMZFzyqHUx"
        );

        AmazonS3 awsS3Client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.US_EAST_2)
                .build();
        return awsS3Client;
    }

}