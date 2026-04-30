package smart.home.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import smart.home.entity.PlugLog;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PlugLogRepository extends JpaRepository<PlugLog, Long> {

    @Modifying
    @Query("DELETE FROM PlugLog p WHERE p.timestamp < :cutoffDate")
    void deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    List<PlugLog> findTop50ByOrderByTimestampDesc();

    List<PlugLog> findTop500ByOrderByTimestampDesc();

    long countByTimestampAfter(LocalDateTime timestamp);
}