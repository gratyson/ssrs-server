package com.gt.ssrs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan("com.gt.ssrs")
public class SsrsApplication {

	public static void main(String[] args) {
		SpringApplication springApplication = new SpringApplication(SsrsApplication.class);
		springApplication.addListeners(new ApplicationPidFileWriter());
		springApplication.run(args);

		//SpringApplication.run(SsrsApplication.class, args);
	}

}
