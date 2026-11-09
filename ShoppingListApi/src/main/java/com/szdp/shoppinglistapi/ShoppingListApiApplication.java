package com.szdp.shoppinglistapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShoppingListApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShoppingListApiApplication.class, args);
	}

}
