package com.springrest.springrestproject.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;


@Embeddable
@Data
public class AdminSecurityContext {

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

    @Column(name = "is_sensitive")
    private boolean isSensitive = false;

    public void setIsSensitive(Boolean isSensitive){
        this.isSensitive = isSensitive;
    }

}