package com.springrest.springrestproject.service.implementations.Kafka;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.model.KafkaTableMapping;
import com.springrest.springrestproject.repository.KafkaTopicRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SpringBootTest
class KafkaMappingServiceTest extends BaseIntegrationTest {

    @Autowired
    private KafkaMappingService service;
    @Autowired
    private KafkaTopicRepo topicRepo;
    @MockitoBean
    private DynamicInboundConsumerManager consumerManager;

    @Test
    void createMappingCreatesTopicWhenAbsent() {
        KafkaTableMapping saved = service.createMapping("orders", "brand-new-topic", "OUTBOUND");

        assertTrue(topicRepo.findByTopicName("brand-new-topic").isPresent());
        assertEquals(topicRepo.findByTopicName("brand-new-topic").get().id(), saved.topicId());
    }

    @Test
    void createMappingReusesExistingTopic() {
        var existingTopic = topicRepo.save(new com.springrest.springrestproject.model.KafkaTopic(null, "shared-topic"));

        KafkaTableMapping saved = service.createMapping("orders", "shared-topic", "OUTBOUND");

        assertEquals(existingTopic.id(), saved.topicId());
    }

    @Test
    void createInboundMappingSubscribesConsumer() {
        service.createMapping("orders", "inbound-topic", "INBOUND");

        verify(consumerManager).subscribeToInboundTopic("inbound-topic", "orders");
    }

    @Test
    void reactivatingInboundMappingResubscribesConsumer() {
        KafkaTableMapping created = service.createMapping("orders", "reactivate-topic", "INBOUND");
        service.updateMapping(created.id(), "orders", "reactivate-topic", "INBOUND", false);
        reset(consumerManager);

        service.updateMapping(created.id(), "orders", "reactivate-topic", "INBOUND", true);

        verify(consumerManager).subscribeToInboundTopic("reactivate-topic", "orders");
    }

    @Test
    void deactivatingInboundMappingUnsubscribesConsumer() {
        KafkaTableMapping created = service.createMapping("orders", "deactivate-topic", "INBOUND");
        reset(consumerManager);

        service.updateMapping(created.id(), "orders", "deactivate-topic", "INBOUND", false);

        verify(consumerManager).unsubscribeFromInboundTopic("deactivate-topic");
    }
}
