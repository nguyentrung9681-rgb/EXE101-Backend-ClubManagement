package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.ClubTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrelloService {
    @Value("${TRELLO_API_KEY:}")
    private String apiKey;

    @Value("${APP_BASE_URL:http://localhost:8080}")
    private String appBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String BASE_URL = "https://api.trello.com/1";

    public String getAuthorizeUrl(Integer clubId) {
        return UriComponentsBuilder.fromUriString("https://trello.com/1/authorize")
                .queryParam("expiration", "never")
                .queryParam("name", "SClub Management")
                .queryParam("scope", "read,write")
                .queryParam("response_type", "token")
                .queryParam("key", apiKey)
                .queryParam("return_url", appBaseUrl + "/api/trello/callback?clubId=" + clubId)
                .toUriString();
    }

    public List<Map<String, Object>> getBoards(String token) {
        java.net.URI uri = UriComponentsBuilder.fromUriString(BASE_URL + "/members/me/boards")
                .queryParam("key", apiKey)
                .queryParam("token", token)
                .build().encode().toUri();
        ResponseEntity<List> response = restTemplate.getForEntity(uri, List.class);
        return response.getBody();
    }

    public Map<String, String> createDefaultLists(String token, String boardId) {
        Map<String, String> listIds = new HashMap<>();
        String[] defaultLists = {"To Do", "Doing", "Done"};
        String[] keys = {"todoListId", "doingListId", "doneListId"};

        for (int i = 0; i < defaultLists.length; i++) {
            java.net.URI uri = UriComponentsBuilder.fromUriString(BASE_URL + "/boards/" + boardId + "/lists")
                    .queryParam("name", defaultLists[i])
                    .queryParam("key", apiKey)
                    .queryParam("token", token)
                    .build().encode().toUri();
            Map<String, Object> res = restTemplate.postForObject(uri, null, Map.class);
            if (res != null && res.containsKey("id")) {
                listIds.put(keys[i], res.get("id").toString());
            }
        }
        return listIds;
    }

    public Map<String, Object> createCard(String token, String listId, ClubTask task) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(BASE_URL + "/cards")
                .queryParam("idList", listId)
                .queryParam("name", task.getTitle())
                .queryParam("key", apiKey)
                .queryParam("token", token);
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            builder.queryParam("desc", task.getDescription());
        }
        java.net.URI uri = builder.build().encode().toUri();
        return restTemplate.postForObject(uri, null, Map.class);
    }

    public void updateCard(String token, String cardId, ClubTask task, String listId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(BASE_URL + "/cards/" + cardId)
                .queryParam("name", task.getTitle())
                .queryParam("key", apiKey)
                .queryParam("token", token);
        if (task.getDescription() != null) {
            builder.queryParam("desc", task.getDescription());
        }
        if (listId != null) {
            builder.queryParam("idList", listId);
        }
        java.net.URI uri = builder.build().encode().toUri();
        restTemplate.put(uri, null);
    }

    public void deleteCard(String token, String cardId) {
        java.net.URI uri = UriComponentsBuilder.fromUriString(BASE_URL + "/cards/" + cardId)
                .queryParam("key", apiKey)
                .queryParam("token", token)
                .build().encode().toUri();
        restTemplate.delete(uri);
    }

    public String registerWebhook(String token, String boardId, Integer clubId) {
        String callbackUrl = appBaseUrl + "/api/trello/webhook";
        java.net.URI uri = UriComponentsBuilder.fromUriString(BASE_URL + "/webhooks")
                .queryParam("callbackURL", callbackUrl)
                .queryParam("idModel", boardId)
                .queryParam("description", "SClub Webhook for Club " + clubId)
                .queryParam("key", apiKey)
                .queryParam("token", token)
                .build().encode().toUri();
        try {
            Map<String, Object> res = restTemplate.postForObject(uri, null, Map.class);
            return res != null ? res.get("id").toString() : null;
        } catch (Exception e) {
            return null; // Handle exception or logs safely
        }
    }
}
