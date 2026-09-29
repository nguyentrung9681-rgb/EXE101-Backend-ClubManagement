package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {
    List<ChatAttachment> findByMessageId(Long messageId);
}
