package com.filemind.activity.entity;

public enum ActivityAction {
    REGISTER("Account created"),
    LOGIN("Signed in"),
    DOCUMENT_CREATED("Document uploaded"),
    DOCUMENT_VIEWED("Document viewed"),
    DOCUMENT_DOWNLOADED("Document downloaded"),
    VERSION_CREATED("Version uploaded"),
    DOCUMENT_RENAMED("Document renamed"),
    DOCUMENT_DELETED("Document deleted"),
    VERSION_RESTORED("Version restored"),
    SHARE_GRANTED("Access shared"),
    SHARE_UPDATED("Access updated"),
    SHARE_REVOKED("Access removed"),
    SHARE_LINK_ENABLED("Public link enabled"),
    SHARE_LINK_DISABLED("Public link disabled"),
    REVIEW_NOTE_ADDED("Review note added"),
    OCR_APPLIED("OCR text extracted"),
    AI_SUMMARY("Summary created"),
    AI_TAGS("Tags created"),
    AI_REWRITE("Rewrite completed"),
    AI_QUESTION("Question answered"),
    PROCESSING_RETRIED("Processing retried");

    private final String label;

    ActivityAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
