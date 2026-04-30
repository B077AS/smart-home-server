package smart.home.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import smart.home.entity.GarageDoorStateLog;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GarageDoorStateLogRepository extends JpaRepository<GarageDoorStateLog, Long> {

    @Modifying
    @Query("DELETE FROM GarageDoorStateLog g WHERE g.timestamp < :cutoffDate")
    void deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    List<GarageDoorStateLog> findTop50ByOrderByTimestampDesc();

    List<GarageDoorStateLog> findTop500ByOrderByTimestampDesc();

    long countByTimestampAfter(LocalDateTime timestamp);

    long countByNewState(String newState);
}