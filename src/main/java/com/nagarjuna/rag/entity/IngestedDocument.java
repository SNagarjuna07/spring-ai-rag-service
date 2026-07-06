package com.nagarjuna.rag.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingested_documents")
@Data
public class IngestedDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private int chunkCount;

    @Column(nullable = false)
    private Instant ingestedAt;

    @PrePersist
    public void save() {
        ingestedAt = Instant.now();
    }
}
