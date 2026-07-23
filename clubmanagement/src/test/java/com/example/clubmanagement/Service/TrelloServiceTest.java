package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.ClubTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class TrelloServiceTest {

    private TrelloService trelloService;
    private RestTemplate mockRestTemplate;

    @BeforeEach
    void setUp() {
        trelloService = new TrelloService();
        mockRestTemplate = Mockito.mock(RestTemplate.class);
        
        // Inject fields using reflection
        ReflectionTestUtils.setField(trelloService, "apiKey", "testApiKey");
        ReflectionTestUtils.setField(trelloService, "restTemplate", mockRestTemplate);
    }

    @Test
    void testCreateCardSendsJsonBody() {
        ClubTask task = ClubTask.builder()
                .title("Thiết kế Banner Sự kiện Chào Tân")
                .description("Thiết kế banner kích thước 2x3m tone màu đỏ")
                .build();

        String expectedUrl = "https://api.trello.com/1/cards?key=testApiKey&token=testToken";

        // Call the service method
        trelloService.createCard("testToken", "testListId", task);

        // Capture and assert arguments passed to RestTemplate
        ArgumentCaptor<Map> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockRestTemplate).postForObject(
                eq(expectedUrl),
                bodyCaptor.capture(),
                eq(Map.class)
        );

        Map<String, Object> capturedBody = bodyCaptor.getValue();
        assertEquals("testListId", capturedBody.get("idList"));
        assertEquals("Thiết kế Banner Sự kiện Chào Tân", capturedBody.get("name"));
        assertEquals("Thiết kế banner kích thước 2x3m tone màu đỏ", capturedBody.get("desc"));
    }

    @Test
    void testUpdateCardSendsJsonBody() {
        ClubTask task = ClubTask.builder()
                .title("Thiết kế Banner Sự kiện Chào Tân")
                .description("Thiết kế banner kích thước 2x3m tone màu đỏ")
                .build();

        String expectedUrl = "https://api.trello.com/1/cards/testCardId?key=testApiKey&token=testToken";

        // Call the service method
        trelloService.updateCard("testToken", "testCardId", task, "newListId");

        // Capture and assert arguments passed to RestTemplate
        ArgumentCaptor<Map> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockRestTemplate).put(
                eq(expectedUrl),
                bodyCaptor.capture()
        );

        Map<String, Object> capturedBody = bodyCaptor.getValue();
        assertEquals("newListId", capturedBody.get("idList"));
        assertEquals("Thiết kế Banner Sự kiện Chào Tân", capturedBody.get("name"));
        assertEquals("Thiết kế banner kích thước 2x3m tone màu đỏ", capturedBody.get("desc"));
    }
}
