package com.example.clubmanagement.dto;

import lombok.*;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EmojiCategoryResponse {
    private String category;
    private List<String> emojis;
}
