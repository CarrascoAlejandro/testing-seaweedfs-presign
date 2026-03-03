package com.example.seaweedfs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SeaweedfsS3DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeaweedfsS3DemoApplication.class, args);
    }
}
