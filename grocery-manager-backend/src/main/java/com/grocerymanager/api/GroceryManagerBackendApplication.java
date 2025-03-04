package com.grocerymanager.api;

import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
@OpenAPIDefinition(
		info = @Info(
				title = "Grocery Manager API",
				version = "v1",
				description = "API for managing grocery items"
		)
)
public class GroceryManagerBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GroceryManagerBackendApplication.class, args);
	}

}
