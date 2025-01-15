package searchengine.services;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;
@Slf4j

public class PageCrawlerTask extends RecursiveTask<Void> {
    @Setter
    private String url;
    @Setter
    private Site site;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaExtractor lemmaExtractor;

    private static final int SLEEP_MIN = 500;
    private static final int SLEEP_MAX = 5000;
    private static final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";
    private static final String REFERRER = "http://www.google.com";

    private static final Logger logger = LoggerFactory.getLogger(PageCrawlerTask.class);

    @Setter
    private volatile boolean indexing;


    public PageCrawlerTask(PageRepository pageRepository,
                           LemmaRepository lemmaRepository,
                           IndexRepository indexRepository,
                           LemmaExtractor lemmaExtractor,
                           boolean indexing) {
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.lemmaExtractor = lemmaExtractor;
        this.indexing = indexing;
    }

    @Override
    protected Void compute() {
        if (!indexing || Thread.currentThread().isInterrupted()) {
            return null;
        }
        try {
            if (!visitedUrls.add(url)) {
                return null;
            }

            delay();

            FetchResult result = fetchDocumentWithRetries(url, 3);
            if (result == null || result.statusCode >= 400) {
                return null;
            }

            String content = result.document.html();
            String text = filterCyrillic(result.document.text());
            int statusCode = result.statusCode;

            Page page = new Page();
            page.setSite(site);
            page.setPath(url);
            page.setCode(statusCode);
            page.setContent(content);
            pageRepository.save(page);

            Map<String, Integer> lemmas = lemmaExtractor.extractLemmas(text);
            lemmaExtractor.saveLemmasAndIndexes(page, lemmas, lemmaRepository, indexRepository);

            Elements links = result.document.select("a[href]");
            List<PageCrawlerTask> subTasks = new ArrayList<>();
            for (var link : links) {
                if (!indexing || Thread.currentThread().isInterrupted()) {
                    return null;
                }
                String linkUrl = link.attr("abs:href");
                if (isValidUrl(linkUrl)) {
                    PageCrawlerTask task = new PageCrawlerTask(pageRepository, lemmaRepository, indexRepository, lemmaExtractor, indexing);
                    task.setUrl(linkUrl);
                    task.setSite(site);
                    subTasks.add(task);
                }
            }
            invokeAll(subTasks);
        } catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    private FetchResult fetchDocumentWithRetries(String url, int maxRetries) throws InterruptedException {
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                Connection.Response response = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .referrer(REFERRER)
                        .execute();
                int statusCode = response.statusCode();
                Document document = response.parse();
                System.out.println("Status Code: " + statusCode);
                return new FetchResult(document, statusCode);
            } catch (SocketTimeoutException e) {
                attempt++;
                logger.warn("Warning: Read timeout for URL: " + url + ". Retrying " + attempt + "/" + maxRetries);
                delay();
            } catch (UnsupportedMimeTypeException e) {
                logger.warn("Warning: Skipping URL due to unhandled content type: " + e.getMimeType());
//                return null;
            } catch (HttpStatusException e) {
                logger.warn("Warning: Skipping URL due to HTTP error: " + e.getStatusCode() + ", URL: " + e.getUrl());
//                return null;
            } catch (IOException e) {
                logger.error("Error fetching URL: " + url,e);
            }
        }
        return null;
    }

    private void delay() throws InterruptedException {
        Thread.sleep(new Random().nextInt(SLEEP_MAX - SLEEP_MIN + 1) + SLEEP_MIN);
    }

    private boolean isValidUrl(String url) {
        return url.startsWith(site.getUrl()) && !url.contains("#")
                && !url.contains("?") && !pageRepository.existsByPath(url);
    }


    private record FetchResult(Document document, int statusCode) {
    }
    private String filterCyrillic(String input) {
        return input.replaceAll("[^а-яА-ЯёЁ]", "");
    }
}
