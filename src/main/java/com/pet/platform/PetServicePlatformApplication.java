package com.pet.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.pet.platform.module")
@SpringBootApplication
public class PetServicePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetServicePlatformApplication.class, args);
    }
}
