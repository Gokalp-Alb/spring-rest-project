package com.springrest.springrestproject.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;


@Embeddable
@Data @Getter @Setter
public class TableContext {

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

}