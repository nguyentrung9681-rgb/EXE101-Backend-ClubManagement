package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.EventGoogleSync;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventGoogleSyncRepository extends JpaRepository<EventGoogleSync, Integer> {
    Optional<EventGoogleSync> findByClubEventId(Integer clubEventId);
    Optional<EventGoogleSync> findByGoogleEventId(String googleEventId);

    /** Nạp sync info cho cả một danh sách sự kiện trong 1 câu query. */
    List<EventGoogleSync> findByClubEventIdIn(Collection<Integer> clubEventIds);
}
