package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.model.KafkaTableMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface IKafkaTableMappingRepo extends JpaRepository<KafkaTableMapping, Long> {
    List<KafkaTableMapping> findByDirectionAndActiveTrue(String direction);
    Optional<KafkaTableMapping> findByTableNameAndDirectionAndActiveTrue(String tableName, String direction);
}