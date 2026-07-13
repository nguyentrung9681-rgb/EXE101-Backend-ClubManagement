package com.example.clubmanagement.Enum;

public enum DocumentType {
    MEETING_MINUTES("Biên bản họp"),
    EVENT_PLAN("Kế hoạch sự kiện"),
    REPORT("Báo cáo"),
    FINANCE("Báo cáo tài chính"),
    OTHER("Khác");

    private final String displayName;

    DocumentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }
}

