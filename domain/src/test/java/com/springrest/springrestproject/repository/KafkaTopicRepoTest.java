package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.model.KafkaTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class KafkaTopicRepoTest extends BaseIntegrationTest {

    @Autowired
    private KafkaTopicRepo repo;

    @Test
    void savesAndFindsByTopicName() {
        KafkaTopic saved = repo.save(new KafkaTopic(null, "orders-events"));
        assertTrue(saved.id() > 0);

        Optional<KafkaTopic> found = repo.findByTopicName("orders-events");
        assertTrue(found.isPresent());
        assertEquals(saved.id(), found.get().id());
    }

    @Test
    void findByIdReturnsSavedTopic() {
        KafkaTopic saved = repo.save(new KafkaTopic(null, "invoices-events"));
        Optional<KafkaTopic> found = repo.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals("invoices-events", found.get().topicName());
    }

    @Test
    void findByTopicNameReturnsEmptyWhenAbsent() {
        assertTrue(repo.findByTopicName("does-not-exist").isEmpty());
    }
}
