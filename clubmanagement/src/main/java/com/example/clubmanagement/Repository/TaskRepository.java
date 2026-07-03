package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.Task;
import com.example.clubmanagement.Enum.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByDepartmentId(Long departmentId);
    List<Task> findByDepartmentIdAndStatus(Long departmentId, TaskStatus status);
}
