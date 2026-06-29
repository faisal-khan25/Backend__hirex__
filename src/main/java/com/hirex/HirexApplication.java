package com.hirex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class HirexApplication {

    private static final Logger log = LoggerFactory.getLogger(HirexApplication.class);

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(HirexApplication.class, args);

        // Log startup information for debugging
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║        HireX Application Started Successfully              ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("Active profiles: {}", context.getEnvironment().getActiveProfiles());
        log.info("Server port: {}", context.getEnvironment().getProperty("server.port"));

        // List all REST controllers
        String[] controllers = context.getBeanNamesForType(Object.class);
        boolean liveInterviewControllerFound = false;
        for (String beanName : controllers) {
            if (beanName.contains("LiveInterviewController")) {
                log.info("✓ Found bean: {}", beanName);
                liveInterviewControllerFound = true;
            }
        }

        if (!liveInterviewControllerFound) {
            log.warn("⚠️ WARNING: LiveInterviewController not found in application context!");
            log.warn("   Available controller beans: ");
            for (String beanName : controllers) {
                if (beanName.contains("Controller")) {
                    log.warn("     - {}", beanName);
                }
            }
        }
    }

}