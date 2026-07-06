package com.nagarjuna.rag.exception;

public class DocumentProcessingException extends RuntimeException {

    public DocumentProcessingException(String msg, Throwable e) {

        super(msg, e);
    }

    public DocumentProcessingException(String msg) {

        super(msg);
    }
}
