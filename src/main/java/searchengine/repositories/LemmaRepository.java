package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    List<Lemma> findAllByLemma(String lemma);
    void deleteBySite(Site site);
    int countBySite(Site site);


}
