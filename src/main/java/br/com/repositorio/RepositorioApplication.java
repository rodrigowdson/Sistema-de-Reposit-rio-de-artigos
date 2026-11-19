package br.com.repositorio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RepositorioApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepositorioApplication.class, args);
    }
}
