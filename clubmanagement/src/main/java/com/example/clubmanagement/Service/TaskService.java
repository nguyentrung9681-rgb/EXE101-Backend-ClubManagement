package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Department;
import com.example.clubmanagement.Entity.Task;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.TaskStatus;
import com.example.clubmanagement.Repository.DepartmentMemberRepository;
import com.example.clubmanagement.Repository.DepartmentRepository;
import com.example.clubmanagement.Repository.TaskRepository;
import com.example.clubmanagement.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskService {
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private DepartmentMemberRepository departmentMemberRepository;
    @Autowired
    private UserRepository userRepository;

    private void validateMemberOfDept(Integer userId, Long departmentId) {
        if (!departmentMemberRepository.existsByDepartmentIdAndUserUserId (departmentId, userId)) {
            throw new RuntimeException("Bạn không thuộc phòng ban này để thao tác Workspace.");
        }
    }

    public Task createTask(Integer creatorId, Long departmentId, String title, String description, Integer assignedUserId) {
        validateMemberOfDept(creatorId, departmentId);

        Department dept = departmentRepository.findById(departmentId).orElseThrow(() -> new RuntimeException("Không tìm thấy ban."));
        User creator = userRepository.findById(creatorId).orElseThrow(() -> new RuntimeException("Không tìm thấy người tạo."));

        User assignee = null;
        if (assignedUserId != null) {
            assignee = userRepository.findById(assignedUserId).orElseThrow(() -> new RuntimeException("Không tìm thấy người được gán task."));
        }

        Task task = Task.builder()
                .department(dept)
                .title(title)
                .description(description)
                .status(TaskStatus.TODO)
                .createdByUser(creator)
                .assignedUser(assignee)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return taskRepository.save(task);
    }

    public Task updateTaskStatus(Integer userId, Long taskId, TaskStatus newStatus) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Không tìm thấy Task."));
        validateMemberOfDept(userId, task.getDepartment().getId());

        task.setStatus(newStatus);
        task.setUpdatedAt(LocalDateTime.now());
        return taskRepository.save(task);
    }

    public List<Task> getKanbanBoard(Integer userId, Long departmentId) {
        validateMemberOfDept(userId, departmentId);
        return taskRepository.findByDepartmentId(departmentId);
    }
}
