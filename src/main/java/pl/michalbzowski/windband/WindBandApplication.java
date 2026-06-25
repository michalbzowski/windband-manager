package pl.michalbzowski.windband;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WindBandApplication {

    public static void main(String[] args) {
        SpringApplication.run(WindBandApplication.class, args);
    }
}
