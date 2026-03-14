package com.gt.ssrs.app.aws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.gt.ssrs")
public class SsrsApplication {

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(SsrsApplication.class);
        springApplication.run(args);
    }
}
