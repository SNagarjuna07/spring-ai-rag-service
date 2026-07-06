<div align="center">

# 🔍 Spring AI RAG Service
### Meet Vera - a document Q&A assistant that never guesses

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M6-6DB33F)](https://spring.io/projects/spring-ai)
[![pgvector](https://img.shields.io/badge/pgvector-PostgreSQL%2016-336791?logo=postgresql)](https://github.com/pgvector/pgvector)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://www.docker.com/)

</div>

---

## What this is

Upload a PDF, DOCX, or TXT file. Ask questions about it. Get answers grounded in the actual
document content with citations pointing back to the exact chunks used, not invented ones.

This is **Retrieval-Augmented Generation (RAG)**: instead of trusting an LLM's frozen training
data, the system retrieves relevant context from your own documents at query time and forces
the model to answer from that context. If the answer isn't in the documents, Vera says so
instead of hallucinating a plausible-sounding guess.

```
Chatbot (P1) → Tool-using agent (P2) → ▶ Retrieval-augmented assistant (P3) ◀ → MCP server (P4)
```

## Architecture

```
                    ┌───────────────────────────────────────────────────┐
                    │                INGESTION PIPELINE                  │
                    │                                                     │
  Upload ──▶ TikaDocumentReader ──▶ TokenTextSplitter ──▶ Ollama (embed) ──▶ pgvector
  (PDF/DOCX/TXT)      (extract text)      (chunk text)      (nomic-embed-text,        │
                                                                768-dim vectors)        │
                    └────────────────────────────────────────────────────────────┘
                                                                                         │
                    ┌────────────────────────────────────────────────────────────┐    │
                    │                     QUERY PIPELINE                          │    │
                    │                                                              │    │
  Question ──▶ ChatClient ──▶ QuestionAnswerAdvisor (similarity search) ◀──────────┘
                    │              │
                    │              ▼
                    │        Groq — llama-3.3-70b-versatile
                    │              │
                    ▼              ▼
              MessageChatMemoryAdvisor    Answer + Source Citations
              (multi-turn history)
                    └────────────────────────────────────────────────────────────┘
```

**Two model providers, on purpose.** Groq handles chat (fast, free, no embeddings endpoint).
Ollama handles embeddings (local, free, fully containerized - `nomic-embed-text`, 768-dim).
Both share one Postgres instance: pgvector's own `vector_store` table for chunk embeddings,
plus a plain `ingested_documents` registry table so listing what's been uploaded doesn't
require reverse-engineering pgvector's internals.

## Tech stack

| Layer | Choice                                                      |
|---|-------------------------------------------------------------|
| Language / Runtime | Java 25                                                     |
| Framework | Spring Boot 4.1.0, Spring AI 2.0.0-M6                       |
| Chat model | Groq - `llama-3.3-70b-versatile` (OpenAI-compatible client) |
| Embedding model | Ollama - `nomic-embed-text`, containerized                  |
| Vector store | PostgreSQL 16 + pgvector (`pgvector/pgvector:pg16`)         |
| Document parsing | Apache Tika (PDF, DOCX, TXT, HTML, and more via one reader) |
| Persistence | Spring Data JPA (document registry)                         |
| Infra | Docker Compose - dual-file dev/prod pattern                 |

## API

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/documents` | Upload a file (multipart) → chunked, embedded, stored |
| `GET` | `/api/v1/documents` | List ingested documents with chunk counts |
| `POST` | `/api/v1/chat` | Ask a question → grounded answer + source citations |

<details>
<summary><strong>Example: <code>POST /api/v1/chat</code></strong></summary>

**Request**
```json
{
  "question": "What is the refund window mentioned in the policy?",
  "sessionId": "optional - include to keep multi-turn conversation memory"
}
```

**Response**
```json
{
  "answer": "The refund window is 30 days from the date of purchase, provided the item is unused and in original packaging.",
  "sources": [
    {
      "filename": "refund-policy.pdf",
      "snippet": "Customers may request a full refund within 30 days of purchase..."
    }
  ]
}
```
</details>

## Running it

Fully Docker-native - no local Maven/Java setup required.

```bash
cp .env.example .env        # fill in GROQ_API_KEY
docker compose -f docker-compose.full.yml up --build
```

First boot pulls `nomic-embed-text` into the Ollama volume (one-time, a few hundred MB).
Subsequent restarts skip the pull entirely.

**Local IDE dev:** `compose.yml` (DB + Ollama only) is auto-detected and started by Spring
Boot's `spring-boot-docker-compose` integration when the app runs from IntelliJ, no manual
`docker compose up` needed. Don't run both workflows simultaneously (port `8080` conflict).

## Design decisions worth asking me about

These are the parts of this project I'd actually want to talk through in an interview 
each one is a real constraint hit and resolved, not a textbook feature checklist.

**Why Ollama instead of paying for OpenAI embeddings?**
Groq has no embeddings endpoint, so a second provider was mandatory either way. Ollama keeps
the entire stack free and Docker-native, at the cost of one extra container and a one-time
model pull on first boot - a deliberate tradeoff for a portfolio project with zero budget.

**Two `ChatModel` beans, one `ChatClient.Builder`.**
Adding both the OpenAI and Ollama Spring AI starters auto-registers a `ChatModel` bean from
*each* - Spring fails to autowire `ChatClient.Builder` with "required a single bean, but 2
were found." Fixed with `spring.ai.ollama.chat.enabled: false`, which suppresses only
Ollama's chat bean while leaving its embedding bean intact.

**Citations aren't free, they need explicit metadata.**
`QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS` on the response context hands back the chunks
used to answer, but chunk metadata reflects whatever the reader happened to set. Ingestion
explicitly tags every chunk with `filename` rather than trusting Tika's `source` field, which
points at the in-memory resource wrapper, not the display name a user would recognize.

**`pgvector/pgvector:pg16`, not `postgres:16`.**
The vector extension isn't in the stock Postgres image. Using the wrong one fails silently at
schema-init time with an unhelpful "type `vector` does not exist" error rather than a clear
"wrong image" message.

**A model that isn't there yet vs. an app that starts too fast.**
Ollama needs `nomic-embed-text` pulled before the first embedding request, but a fresh
container has an empty model cache. A one-shot `ollama-pull` init service, gated with
`depends_on: condition: service_completed_successfully`, blocks app startup until the pull
finishes, instead of racing it and failing the first upload.

## Scope, honestly stated

This is retrieval-augmented Q&A - one retrieval pass per question, no query planning loop, no
self-correction, no autonomous multi-step reasoning. That's a deliberate boundary, not a
missing feature.

## What's next

- [ ] Hybrid search (vector + keyword) for queries where exact terms matter more than semantic similarity
- [ ] `RelevancyEvaluator` for automated retrieval-quality checks
- [ ] Persistent chat memory (swap `InMemoryChatMemoryRepository` for a JDBC-backed one, same Postgres instance already available)
