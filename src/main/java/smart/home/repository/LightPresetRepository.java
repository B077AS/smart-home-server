package smart.home.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import smart.home.entity.LightPreset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LightPresetRepository extends JpaRepository<LightPreset, UUID> {

    List<LightPreset> findAllByOrderByCreatedAtDesc();

    Optional<LightPreset> findByIsDefaultTrue();
}
