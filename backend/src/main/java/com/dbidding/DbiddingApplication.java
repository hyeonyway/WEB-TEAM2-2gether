package com.dbidding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.dbidding.account.authentication.AuthenticationModeProperties;
import com.dbidding.account.password.PasswordHashProperties;
import com.dbidding.upload.config.S3UploadProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
	AuthenticationModeProperties.class,
	PasswordHashProperties.class,
	S3UploadProperties.class
})
public class DbiddingApplication {

	public static void main(String[] args) {
		SpringApplication.run(DbiddingApplication.class, args);
	}

}
