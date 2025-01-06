package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Site;

import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Integer> {
    void deleteByUrl(String url);
    Optional<Site> findByUrl(String url);

}
