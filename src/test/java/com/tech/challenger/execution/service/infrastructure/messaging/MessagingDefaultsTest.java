package com.tech.challenger.execution.service.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class MessagingDefaultsTest {

    @Test
    void defaultsMatchOsServiceEventExchangeAndRoutingKey() throws IOException {
        var properties = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml")).getFirst();
        var environment = new MockEnvironment();
        environment.getPropertySources().addLast(properties);

        assertThat(environment.getProperty("app.messaging.os-exchange")).isEqualTo("os.exchange");
        assertThat(environment.getProperty("app.messaging.os-events-routing-key")).isEqualTo("os.event");
        assertThat(environment.getProperty("app.messaging.os-routing-key")).isEqualTo("execution.status");
    }
}
