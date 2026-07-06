package com.nagarjuna.rag.service;

import com.nagarjuna.rag.dto.IngestResult;
import com.nagarjuna.rag.entity.IngestedDocument;
import com.nagarjuna.rag.exception.DocumentProcessingException;
import com.nagarjuna.rag.repository.IngestedDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * ETL pipeline: DocumentReader -> TokenTextSplitter -> VectorStore.
 * Tika handles PDF, DOCX, TXT, HTML, and most common formats via one reader.
 */
public class DocumentIngestionService {

    private final IngestedDocumentRepository documentRepository;

    private final VectorStore vectorStore;

    private final TokenTextSplitter tokenTextSplitter;


    public IngestResult ingest(MultipartFile file) {

        log.info("Uploading file..");

        // Reject empty uploads immediately
        if (file.isEmpty()) {
            throw new DocumentProcessingException("Uploaded file is empty");
        }

        // Multipart uploads can technically arrive with no filename - fall back to a placeholder
        // rather than storing/citing "null" later.
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "Unnamed";

        List<Document> rawDocs;

        try {
            // Wrap the raw upload bytes in a Resource Tika can read. getFilename() is overridden
            // because a plain ByteArrayResource has no filename by default - Tika uses it as a
            // hint for picking the right parser (PDF vs DOCX vs plain text, etc).
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {

                @Override
                public String getFilename() {
                    return fileName;
                }
            };

            // Tika auto-detects the format and extracts text - this is the "Reader" stage of the ETL pipeline.
            rawDocs = new TikaDocumentReader(resource).get();

        } catch (IOException e) {
            // file.getBytes() is the only thing here that can actually throw - wrap it in our own
            // exception type so GlobalExceptionHandler can turn it into a clean 422, not a raw 500.
            throw new DocumentProcessingException("Could not read uploaded file: " + fileName, e);
        }

        // A file that parses but yields zero Documents is usually a scanned image PDF or genuinely
        // empty content - nothing to embed, so fail loudly instead of silently ingesting nothing.
        if (rawDocs.isEmpty()) {
            throw new DocumentProcessingException("Tika extracted no readable content from: : " + fileName);
        }

        // Split stage: break the full document into embedding-sized chunks.
        List<Document> chunks = tokenTextSplitter.apply(rawDocs);

        // Tag every chunk with the original filename - TikaDocumentReader's own "source"
        // metadata reflects the ByteArrayResource description, not always the display name
        // we want to cite back to the user.
        chunks.forEach(chunk ->
                chunk.getMetadata().put("filename", fileName)
        );

        // Embed + store stage: VectorStore internally calls the embedding model (Ollama) for
        // each chunk, then writes chunk text + vector + metadata into pgvector's vector_store table.
        vectorStore.add(chunks);

        // Build the registry row - separate from pgvector's own table, lets us answer
        // "what have I ingested?" via plain SQL/JPA instead of querying vector internals.
        IngestedDocument document = new IngestedDocument();

        document.setFileName(fileName);
        document.setContentType(file.getContentType());
        document.setChunkCount(chunks.size());

        IngestedDocument saved = documentRepository.save(document);

        log.info("File {} uploaded", saved.getFileName());

        return mapper(saved);
    }

    public List<IngestResult> listIngested() {

        log.info("Fetching all the uploaded files..");

        // findAll() -> entity list -> map each to a DTO → collect. Simple read path
        return documentRepository
                .findAll(Pageable.unpaged())
                .stream()
                .map(this::mapper)
                .toList();
    }

    private IngestResult mapper(IngestedDocument doc) {

        return new IngestResult(
                doc.getId(),
                doc.getFileName(),
                doc.getChunkCount(),
                doc.getIngestedAt()
        );
    }
}