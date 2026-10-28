package com.example.clubmanagement.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalyticsEventRequest {
    private String eventName;
    private String sessionId;
    private String path;
    private String referrer;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;
    private String utmContent;
}
