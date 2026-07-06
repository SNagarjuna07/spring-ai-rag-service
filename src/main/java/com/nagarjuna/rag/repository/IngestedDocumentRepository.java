package com.nagarjuna.rag.repository;

import com.nagarjuna.rag.entity.IngestedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IngestedDocumentRepository extends JpaRepository<IngestedDocument, UUID> {}
