package com.dbidding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DbiddingApplication {

	public static void main(String[] args) {
		SpringApplication.run(DbiddingApplication.class, args);
	}

}
