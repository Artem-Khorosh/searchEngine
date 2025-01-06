package searchengine.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Integer> {
    void deleteBySiteUrl(String url);
    boolean existsByPath(String path);
    Optional<Page> findByPath(String path);
    int countBySite(Site site);

    @Query("SELECT p FROM Page p WHERE p.site = :site AND p.content LIKE %:#{#lemmas[0]}%")
    List<Page> findBySiteAndLemmas(@Param("site") Site site, @Param("lemmas") List<String> lemmas, Pageable pageable);
    @Query("SELECT p FROM Page p WHERE p.content LIKE %:#{#lemmas[0]}%")
    List<Page> findByLemmas(@Param("lemmas") List<String> lemmas, Pageable pageable);

    @Query("SELECT COUNT(p) FROM Page p WHERE p.content LIKE %:#{#lemmas[0]}%")
    int countByLemmas(@Param("lemmas") List<String> lemmas);
}
