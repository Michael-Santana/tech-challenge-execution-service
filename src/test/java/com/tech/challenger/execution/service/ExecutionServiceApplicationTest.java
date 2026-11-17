package com.tech.challenger.execution.service;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

class ExecutionServiceApplicationTest {

    @Test
    void launchesSpringApplication() {
        String[] args = {"--spring.main.web-application-type=none"};
        try (var springApplication = mockStatic(SpringApplication.class)) {
            ExecutionServiceApplication.main(args);
            springApplication.verify(() -> SpringApplication.run(ExecutionServiceApplication.class, args));
        }
    }
}
