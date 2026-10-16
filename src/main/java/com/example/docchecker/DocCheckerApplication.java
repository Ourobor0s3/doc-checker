package com.example.docchecker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DocCheckerApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        // Netty (через Lettuce/Redis) на JDK 23+ печатает предупреждение об устаревшем sun.misc.Unsafe.
        // Без Unsafe он работает чуть медленнее, что для этого сервиса несущественно.
        System.setProperty("io.netty.noUnsafe", "true");
        SpringApplication.run(DocCheckerApplication.class, args);
    }
}
