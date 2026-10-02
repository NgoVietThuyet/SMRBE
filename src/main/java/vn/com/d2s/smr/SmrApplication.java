package vn.com.d2s.smr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmrApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmrApplication.class, args);
    }
}
