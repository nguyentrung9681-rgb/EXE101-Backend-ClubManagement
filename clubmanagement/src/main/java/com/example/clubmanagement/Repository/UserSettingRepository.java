package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingRepository extends JpaRepository<UserSetting, Integer> {
}
