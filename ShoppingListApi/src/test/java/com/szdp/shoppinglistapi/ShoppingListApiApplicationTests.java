package com.szdp.shoppinglistapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:shoppinglist-tests;DB_CLOSE_DELAY=-1")
class ShoppingListApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
