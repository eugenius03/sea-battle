package com.chnu.seabattle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SeaBattleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeaBattleApplication.class, args);
    }

}
