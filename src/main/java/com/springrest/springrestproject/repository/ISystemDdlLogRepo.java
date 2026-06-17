package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.model.SystemDdlLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ISystemDdlLogRepo extends JpaRepository<SystemDdlLog, Long> {
    List<SystemDdlLog> findByTableNameOrderByExecutedAtAsc(String tableName);
}