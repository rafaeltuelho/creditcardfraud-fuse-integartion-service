package com.redhat.demo.dm.ccfraud.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

@SpringBootApplication
@Configuration
@ComponentScan(basePackages = "com.redhat.demo.dm.ccfraud")
@ImportResource({"classpath*:spring-context.xml"})
public class Application {

    /**
     * Main method to start the application.
     */
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(Application.class, args);
    }

}