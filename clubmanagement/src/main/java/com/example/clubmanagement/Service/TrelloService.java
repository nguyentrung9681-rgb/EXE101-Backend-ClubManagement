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
    @Value("${trello.api-key:${TRELLO_API_KEY:}}")
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
        String url = BASE_URL + "/members/me/boards?key=" + apiKey + "&token=" + token;
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }

    public Map<String, String> createDefaultLists(String token, String boardId) {
        Map<String, String> listIds = new HashMap<>();
        String[] defaultLists = {"To Do", "Doing", "Done"};
        String[] keys = {"todoListId", "doingListId", "doneListId"};

        for (int i = 0; i < defaultLists.length; i++) {
            String url = BASE_URL + "/boards/" + boardId + "/lists?key=" + apiKey + "&token=" + token;
            Map<String, Object> body = new HashMap<>();
            body.put("name", defaultLists[i]);
            Map<String, Object> res = restTemplate.postForObject(url, body, Map.class);
            if (res != null && res.containsKey("id")) {
                listIds.put(keys[i], res.get("id").toString());
            }
        }
        return listIds;
    }

    public Map<String, Object> createCard(String token, String listId, ClubTask task) {
        String url = BASE_URL + "/cards?key=" + apiKey + "&token=" + token;
        Map<String, Object> body = new HashMap<>();
        body.put("idList", listId);
        body.put("name", task.getTitle());
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            body.put("desc", task.getDescription());
        }
        return restTemplate.postForObject(url, body, Map.class);
    }

    public void updateCard(String token, String cardId, ClubTask task, String listId) {
        String url = BASE_URL + "/cards/" + cardId + "?key=" + apiKey + "&token=" + token;
        Map<String, Object> body = new HashMap<>();
        body.put("name", task.getTitle());
        if (task.getDescription() != null) {
            body.put("desc", task.getDescription());
        }
        if (listId != null) {
            body.put("idList", listId);
        }
        restTemplate.put(url, body);
    }

    public void deleteCard(String token, String cardId) {
        String url = BASE_URL + "/cards/" + cardId + "?key=" + apiKey + "&token=" + token;
        restTemplate.delete(url);
    }

    public String registerWebhook(String token, String boardId, Integer clubId) {
        String callbackUrl = appBaseUrl + "/api/trello/webhook";
        String url = BASE_URL + "/webhooks?key=" + apiKey + "&token=" + token;
        Map<String, Object> body = new HashMap<>();
        body.put("callbackURL", callbackUrl);
        body.put("idModel", boardId);
        body.put("description", "SClub Webhook for Club " + clubId);
        try {
            Map<String, Object> res = restTemplate.postForObject(url, body, Map.class);
            return res != null ? res.get("id").toString() : null;
        } catch (Exception e) {
            return null; // Handle exception or logs safely
        }
    }
}
