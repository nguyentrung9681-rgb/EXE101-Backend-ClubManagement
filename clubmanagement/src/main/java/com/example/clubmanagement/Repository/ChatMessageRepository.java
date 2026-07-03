package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    //lấy lịch sử chat phòng ban, sắp xếp tin nhắn cũ trước mới sau
    List<ChatMessage> findByDepartmentIdOrderBySentAtAsc(Long departmentId);
}
