package searchengine.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.services.LemmaExtractor;
import searchengine.services.PageCrawlerTask;

@Component
public class PageCrawlerTaskFactory {
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaExtractor lemmaExtractor;

    @Autowired
    public PageCrawlerTaskFactory(PageRepository pageRepository,
                                  LemmaRepository lemmaRepository,
                                  IndexRepository indexRepository,
                                  LemmaExtractor lemmaExtractor) {
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.lemmaExtractor = lemmaExtractor;
    }

    public PageCrawlerTask create(String url, Site site, boolean indexing) {
        PageCrawlerTask task = new PageCrawlerTask(pageRepository, lemmaRepository, indexRepository, lemmaExtractor, indexing);
        task.setUrl(url);
        task.setSite(site);
        return task;
    }
}