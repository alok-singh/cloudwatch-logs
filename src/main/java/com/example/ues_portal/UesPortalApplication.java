package com.example.ues_portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.example.ues_portal")
public class UesPortalApplication {

	public static void main(String[] args) {
		SpringApplication.run(UesPortalApplication.class, args);
	}

}
