package com.furia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FuriaBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(FuriaBotApplication.class, args);
	}

}
