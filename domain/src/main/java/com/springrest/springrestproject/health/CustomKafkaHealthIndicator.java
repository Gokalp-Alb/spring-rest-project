package com.springrest.springrestproject.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component("kafka")
public class CustomKafkaHealthIndicator implements HealthIndicator {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Override
    public Health health() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);

        try (AdminClient adminClient = AdminClient.create(configs)) {
            String clusterId = adminClient.describeCluster(
                    new DescribeClusterOptions().timeoutMs(3000)
            ).clusterId().get(3, TimeUnit.SECONDS);

            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("bootstrapServers", bootstrapServers)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", "Could not connect to Kafka cluster")
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withException(e)
                    .build();
        }
    }
}