package com.filemind.document.model;

public enum ProcessingStatus {
    UPLOADED,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    public boolean isActive() {
        return this == UPLOADED || this == PROCESSING;
    }
}
