package com.example.clubmanagement.Enum;

public enum DocumentType {
    EVENT("Event"),
    CLUB_ACTIVITY("Club Activity");

    private final String displayName;

    DocumentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }
}

