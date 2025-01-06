package searchengine.services;

import lombok.Getter;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
public class IndexingService {
    @Getter
    private volatile boolean indexing = false;

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private ExecutorService executorService;
    private final LemmaExtractor lemmaExtractor;

    @Autowired
    public IndexingService(SitesList sitesList,
                           SiteRepository siteRepository,
                           PageRepository pageRepository,
                           LemmaRepository lemmaRepository,
                           IndexRepository indexRepository,
                           LemmaExtractor lemmaExtractor) throws IOException {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.lemmaExtractor = lemmaExtractor;
    }

    public synchronized void startIndexing() {
        if (indexing) {
            System.err.println("Indexing has already started.");
            return;
        }
        indexing = true;
        executorService = Executors.newFixedThreadPool(10);

        for (searchengine.config.Site siteConfig : sitesList.getSites()) {
            executorService.submit(() -> {
                try {
                    indexSite(siteConfig);
                } catch (Exception e) {
                    handleIndexingError(siteConfig, e);
                }
            });
        }
        executorService.shutdown();
        try {
            boolean terminated = executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            if (!terminated) {
                System.err.println("Failed to complete all tasks within the specified time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            indexing = false;
        }
    }

    public synchronized void stopIndexing() {
        if (!indexing) {
            System.err.println("Indexing has not started yet.");
            return;
        }
        indexing = false;
        executorService.shutdownNow();
        for (searchengine.config.Site siteConfig : sitesList.getSites()) {
            Optional<Site> optionalSite = siteRepository.findByUrl(siteConfig.getUrl());
            if (optionalSite.isPresent()) {
                Site site = optionalSite.get();
                if (site.getStatus() == Status.INDEXING) {
                    site.setStatus(Status.FAILED);
                    site.setLastError("Indexing was stopped");
                    site.setStatusTime(LocalDateTime.now());
                    siteRepository.save(site);
                }
            }
        }
    }

    @Transactional
    private void indexSite(searchengine.config.Site siteConfig) {
        Optional<Site> optionalSite = siteRepository.findByUrl(siteConfig.getUrl());
        Site site;
        if (optionalSite.isEmpty()) {
            site = new Site();
            site.setUrl(siteConfig.getUrl());
            site.setName(siteConfig.getName());
        } else {
            site = optionalSite.get();
        }
        site.setStatus(Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
        System.out.println("Started indexing site: " + site.getUrl());

        try {
            crawlSite(site);
            if (indexing) {
                site.setStatus(Status.INDEXED);
                System.out.println("Successfully indexed site: " + site.getUrl());
            } else {
                site.setStatus(Status.FAILED);
                site.setLastError("Indexing stopped by user");
                System.out.println("Indexing stopped by user for site: " + site.getUrl());
            }
        } catch (Exception e) {
            site.setStatus(Status.FAILED);
            site.setLastError(e.getMessage());
            System.err.println("Error indexing site: " + site.getUrl() + " - " + e.getMessage());
        } finally {
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    private void crawlSite(Site site) {
        if (!indexing) {
            return;
        }
        ForkJoinPool pool = new ForkJoinPool();
        PageCrawlerTask task = new PageCrawlerTask(pageRepository, lemmaRepository, indexRepository, lemmaExtractor);
        task.setUrl(site.getUrl());
        task.setSite(site);
        pool.invoke(task);

        System.out.println("Crawled site: " + site.getUrl());
    }

    private void handleIndexingError(searchengine.config.Site siteConfig, Exception e) {
        Optional<Site> optionalSite = siteRepository.findByUrl(siteConfig.getUrl());
        if (optionalSite.isPresent()) {
            Site site = optionalSite.get();
            site.setStatus(Status.FAILED);
            site.setLastError(e.getMessage());
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    @Transactional
    public boolean indexPage(String url) throws IOException {
        String siteUrl = url.split("/")[2];
        Optional<Site> optionalSite = siteRepository.findByUrl(url);
        Site site;
        if (optionalSite.isEmpty()) {
            site = createSiteFromConfig(siteUrl);
            if (site == null) {
                throw new IllegalArgumentException("Site not found in configuration: " + siteUrl);
            }
        } else {
            site = optionalSite.get();
        }

        Optional<Page> existingPage = pageRepository.findByPath(url);
        if (existingPage.isPresent()) {
            Page page = existingPage.get();
            indexRepository.deleteByPage(page);
            lemmaRepository.deleteBySite(site);
            pageRepository.delete(page);
        }

        Connection.Response response = Jsoup.connect(url).execute();
        Document document = response.parse();
        String content = document.html();
        String text = document.text();
        int statusCode = response.statusCode();

        Page page = new Page();
        page.setSite(site);
        page.setPath(url);
        page.setCode(statusCode);
        page.setContent(content);
        pageRepository.save(page);

        Map<String, Integer> lemmas = lemmaExtractor.extractLemmas(text);
        lemmaExtractor.saveLemmasAndIndexes(page, lemmas, lemmaRepository, indexRepository);

        return true;
    }

    private Site createSiteFromConfig(String siteUrl) {
        for (searchengine.config.Site siteConfig : sitesList.getSites()) {
            if (siteConfig.getUrl().equals(siteUrl)) {
                Site site = new Site();
                site.setUrl(siteConfig.getUrl());
                site.setName(siteConfig.getName());
                site.setStatus(Status.INDEXING);
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                return site;
            }
        }
        return null;
    }
}