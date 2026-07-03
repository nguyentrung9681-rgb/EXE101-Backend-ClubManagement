package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.DepartmentMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentMemberRepository extends JpaRepository<DepartmentMember, Long> {
    List<DepartmentMember> findByDepartmentId(Long departmentId);
    Optional<DepartmentMember> findByDepartmentIdAndUserUserId (Long departmentId, Integer userId);
    boolean existsByDepartmentIdAndUserUserId (Long departmentId, Integer userId);
}
