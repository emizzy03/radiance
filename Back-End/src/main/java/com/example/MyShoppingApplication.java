package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * This is a class that is used to run your application.
 *
 * DO NOT CHANGE ANYTHING IN THIS CLASS
 *
 */
@SpringBootApplication(scanBasePackages = "com.example")
@EntityScan("com.example.entity")
@EnableJpaRepositories("com.example.repository")
public class MyShoppingApplication {

    /**
     * Runs the application
     *
     * @param args The arguments of the program.
     * @throws InterruptedException
     */
    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(MyShoppingApplication.class, args);
    }
}
