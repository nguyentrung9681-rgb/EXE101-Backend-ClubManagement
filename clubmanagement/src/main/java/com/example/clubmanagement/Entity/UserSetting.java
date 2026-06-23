package com.example.clubmanagement.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "UserSettings")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class UserSetting {
    @Id
    private Integer userId;

    private Boolean receiveEmailNotification = true;
    private Boolean receiveSystemNotification = true;
    private String theme = "LIGHT";
    private Integer lastSelectedClubId;
}
