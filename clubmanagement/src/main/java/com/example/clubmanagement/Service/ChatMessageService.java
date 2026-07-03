package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.ChatMessage;
import com.example.clubmanagement.Entity.Department;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Repository.ChatMessageRepository;
import com.example.clubmanagement.Repository.DepartmentMemberRepository;
import com.example.clubmanagement.Repository.DepartmentRepository;
import com.example.clubmanagement.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatMessageService {
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private DepartmentMemberRepository departmentMemberRepository;
    @Autowired
    private UserRepository userRepository;

    private void validateMemberOfDept(Integer userId, Long departmentId) {
        if (!departmentMemberRepository.existsByDepartmentIdAndUserUserId (departmentId, userId)) {
            throw new RuntimeException("Bạn không phải thành viên phòng ban này, không thể truy cập chat.");
        }
    }

    public ChatMessage sendMessage(Integer senderId, Long departmentId, String content) {
        validateMemberOfDept(senderId, departmentId);

        Department dept = departmentRepository.findById(departmentId).orElseThrow(() -> new RuntimeException("Không tìm thấy ban."));
        User sender = userRepository.findById(senderId).orElseThrow(() -> new RuntimeException("Không tìm thấy User."));

        ChatMessage message = ChatMessage.builder()
                .department(dept)
                .sender(sender)
                .messageContent(content)
                .sentAt(LocalDateTime.now())
                .build();
        return chatMessageRepository.save(message);
    }

    public List<ChatMessage> getChatHistory(Integer userId, Long departmentId) {
        validateMemberOfDept(userId, departmentId);
        return chatMessageRepository.findByDepartmentIdOrderBySentAtAsc(departmentId);
    }
}
