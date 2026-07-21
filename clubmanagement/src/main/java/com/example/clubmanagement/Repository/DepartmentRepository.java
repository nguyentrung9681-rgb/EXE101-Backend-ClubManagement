package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Integer> {
    List<Department> findByClubId(Integer clubId);
}
