package com.springrest.springrestproject.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Embeddable
@Data
@Getter @Setter
public class ColumnContext {

    @Column(name = "creator_id", updatable = false)
    private Long creatorId;

    @CreationTimestamp
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "last_updater_id")
    private Long lastUpdaterId;

    @UpdateTimestamp
    @Column(name = "last_changed_date")
    private LocalDateTime lastChangedDate;

    @Column(name = "is_sensitive", nullable = false)
    private Boolean isSensitive = false;

    @Column(name = "is_unique", nullable = false)
    private Boolean isUnique = false;

    @Column(name = "validation_regex", length = 500)
    private String validationRegex;

}
