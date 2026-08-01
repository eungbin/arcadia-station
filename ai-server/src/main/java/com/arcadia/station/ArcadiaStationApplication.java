package com.arcadia.station;

import com.arcadia.station.ai.common.ArcadiaAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ArcadiaAiProperties.class)
public class ArcadiaStationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArcadiaStationApplication.class, args);
    }
}
