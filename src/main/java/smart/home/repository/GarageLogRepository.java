package smart.home.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import smart.home.entity.GarageLog;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GarageLogRepository extends JpaRepository<GarageLog, Long> {

    @Modifying
    @Query("DELETE FROM GarageLog g WHERE g.timestamp < :cutoffDate")
    void deleteOlderThan(LocalDateTime cutoffDate);

    List<GarageLog> findTop50ByOrderByTimestampDesc();

    List<GarageLog> findTop500ByOrderByTimestampDesc();

    long countByTimestampAfter(LocalDateTime timestamp);
}