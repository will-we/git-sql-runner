package com.example.gitsqlrunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BootstrapApplication {
  private static final Logger log = LoggerFactory.getLogger(BootstrapApplication.class);

  public static void main(String[] args) {
    long start = System.currentTimeMillis();
    log.info("application starting: name={}, argsCount={}", "git-sql-runner", args == null ? 0 : args.length);
    ConfigurableApplicationContext context = SpringApplication.run(BootstrapApplication.class, args);
    long cost = System.currentTimeMillis() - start;
    log.info("application started: port={}, profiles={}, startupMs={}",
      context.getEnvironment().getProperty("server.port", "8080"),
      String.join(",", context.getEnvironment().getActiveProfiles()),
      cost);
  }
}

