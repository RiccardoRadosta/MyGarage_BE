package com.ilmiogarage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class MyGarageApplication {

    private static final Logger log = LoggerFactory.getLogger(MyGarageApplication.class);

    public static void main(String[] args) {
        loadDotenvIfPresent();
        SpringApplication.run(MyGarageApplication.class, args);
    }

    /**
     * Carica le variabili definite nel file .env (se presente) come System Properties,
     * consentendo il funzionamento sia via Maven (spring-boot:run) che via IDE o JAR eseguibile.
     * Non sovrascrive variabili già impostate a livello di sistema operativo.
     */
    public static void loadDotenvIfPresent() {
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(envPath)) {
            String line;
            int loadedCount = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separatorIndex = line.indexOf('=');
                if (separatorIndex > 0) {
                    String key = line.substring(0, separatorIndex).trim();
                    String value = line.substring(separatorIndex + 1).trim();

                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }

                    if (System.getenv(key) == null && System.getProperty(key) == null) {
                        System.setProperty(key, value);
                        loadedCount++;
                    }
                }
            }
            log.info("Caricate {} variabili d'ambiente dal file .env", loadedCount);
        } catch (IOException e) {
            log.warn("Impossibile leggere il file .env: {}", e.getMessage());
        }
    }
}
