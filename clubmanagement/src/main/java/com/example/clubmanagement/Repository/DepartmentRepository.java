package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.Department;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Integer> {
    List<Department> findByClubId(Integer clubId);

    /** Nạp sẵn trưởng ban và user của trưởng ban cho danh sách phòng ban. */
    @EntityGraph(attributePaths = {"head", "head.user"})
    List<Department> getByClubId(Integer clubId);
}
