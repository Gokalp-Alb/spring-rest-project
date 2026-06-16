package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.dto.request.query.SelectQueryRequest;
import com.springrest.springrestproject.repository.IUserRepo;
import com.springrest.springrestproject.service.interfaces.IQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QueryService implements IQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final IUserRepo userRepo;

    @Override
    public List<Map<String, Object>> executeSelect(SelectQueryRequest request, Long userId, Pageable pageable) {
        userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User unrecognized"));

        String fieldsStr = (request.fields() == null || request.fields().isEmpty())
                ? "*"
                : String.join(", ", request.fields());

        StringBuilder sqlBuilder = new StringBuilder(String.format("SELECT %s FROM %s", fieldsStr, request.tableName()));

        if (request.whereColumn() != null && !request.whereColumn().isEmpty()) {
            sqlBuilder.append(String.format(" WHERE %s = ?", request.whereColumn()));
            sqlBuilder.append(" LIMIT ? OFFSET ?");
            return jdbcTemplate.queryForList(
                    sqlBuilder.toString(),
                    request.whereValue(),
                    pageable.getPageSize(),
                    pageable.getOffset()
            );
        }

        sqlBuilder.append(" LIMIT ? OFFSET ?");
        return jdbcTemplate.queryForList(
                sqlBuilder.toString(),
                pageable.getPageSize(),
                pageable.getOffset()
        );
    }
}