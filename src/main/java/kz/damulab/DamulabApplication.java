package kz.damulab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DamulabApplication {

    public static void main(String[] args) {
        SpringApplication.run(DamulabApplication.class, args);
    }
}
