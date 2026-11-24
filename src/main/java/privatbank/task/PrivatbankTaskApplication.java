package privatbank.task;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrivatbankTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrivatbankTaskApplication.class, args);
    }

}
