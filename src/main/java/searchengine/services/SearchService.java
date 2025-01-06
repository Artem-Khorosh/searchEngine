package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResult;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Index;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Comparator;

@Service
public class SearchService {
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final LemmaExtractor lemmaExtractor;
    @Autowired
    private final IndexRepository indexRepository;

    private static final double MAX_LEMMA_FREQUENCY_PERCENT = 0.1;


    @Autowired
    public SearchService(SiteRepository siteRepository,
                         PageRepository pageRepository,
                         LemmaRepository lemmaRepository,
                         LemmaExtractor lemmaExtractor,
                         IndexRepository indexRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaExtractor = lemmaExtractor;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    public SearchResponse search(String query, String site, int offset, int limit) {
        List<String> lemmas = lemmaExtractor.getLemmaSet(query)
                .stream()
                .toList();
        if (lemmas.isEmpty()) {
            return null;
        }

        List<String> filteredLemmas = filterFrequentLemmas(lemmas);

        if (filteredLemmas.isEmpty()) {
            return null;
        }
        List<Page> pages = findPagesByLemmas(filteredLemmas, site, offset, limit);

        if (pages.isEmpty()) {
            SearchResponse response = new SearchResponse();
            response.setResult(true);
            response.setCount(0);
            response.setData(List.of());
            return response;
        }

        double maxAbsoluteRelevance = pages.stream()
                .mapToDouble(page -> calculateAbsoluteRelevance(page, filteredLemmas))
                .max()
                .orElse(1.0);

        List<SearchResult> results = pages.stream().map(page -> {
                    SearchResult result = new SearchResult();
                    result.setSite(page.getSite().getUrl());
                    result.setSiteName(page.getSite().getName());
                    result.setUri(page.getPath());
                    result.setTitle(extractTitle(page.getContent()));
                    result.setSnippet(generateSnippet(page.getContent(), filteredLemmas));
                    double absoluteRelevance = calculateAbsoluteRelevance(page, filteredLemmas);
                    result.setRelevance((float) (absoluteRelevance / maxAbsoluteRelevance));
                    return result;
                }).sorted(Comparator.comparing(SearchResult::getRelevance).reversed())
                .collect(Collectors.toList());

        SearchResponse response = new SearchResponse();
        response.setResult(true);
        response.setCount(results.size());
        response.setData(results);
        return response;

    }

    private double calculateAbsoluteRelevance(Page page, List<String> lemmas) {
        return lemmas.stream()
                .mapToDouble(lemma -> indexRepository.findByPageAndLemma(page, lemmaRepository.findAllByLemma(lemma).get(0))
                        .stream()
                        .mapToDouble(Index::getRank)
                        .sum())
                .sum();
    }

    private List<Page> findPagesByLemmas(List<String> lemmas, String site, int offset, int limit) {
        List<Page> pages;
        Pageable pageable = PageRequest.of(offset / limit, limit);
        if (site != null) {
            Site siteEntity = siteRepository.findByUrl(site).orElse(null);
            if (siteEntity == null) {
                return List.of();
            }
            pages = pageRepository.findBySiteAndLemmas(siteEntity, List.of(lemmas.get(0)), pageable);
        } else {
            pages = pageRepository.findByLemmas(List.of(lemmas.get(0)), pageable);

        }

        for (int i = 1; i < lemmas.size(); i++) {
            String lemma = lemmas.get(i);
            pages = pages.stream()
                    .filter(page -> page.getContent().contains(lemma))
                    .collect(Collectors.toList());
            if (pages.isEmpty()) {
                break;
            }
        }
        return pages;
    }

    private List<String> filterFrequentLemmas(List<String> lemmas) {
        return lemmas.stream()
                .sorted(Comparator.comparingInt(lemma ->
                        lemmaRepository.findAllByLemma(lemma)
                                .stream()
                                .mapToInt(Lemma::getFrequency)
                                .sum()
                ))
                .collect(Collectors.toList());
    }

    private String extractTitle(String content) {
        int titleStart = content.indexOf("<title>") + 7;
        int titleEnd = content.indexOf("</title>");
        if (titleStart != -1 && titleEnd != -1) {
            return content.substring(titleStart, titleEnd);
        }
        return "Без заголовка";
    }

    private String generateSnippet(String content, List<String> lemmas) {
        String lowerContent = content.toLowerCase();
        int snippetLength = 300;
        int start = Integer.MAX_VALUE;
        int end = 0;

        for (String lemma : lemmas) {
            int index = lowerContent.indexOf(lemma.toLowerCase());
            if (index != -1) {
                start = Math.min(start, index);
                end = Math.max(end, index + lemma.length());
            }
        }

        if (start == Integer.MAX_VALUE) {
            return content.substring(0, Math.min( snippetLength, content.length()));
        }

        start = Math.max(0, start - snippetLength / 2);
        end = Math.min(content.length(), end + snippetLength / 2);

        String snippet = content.substring(start, end);

        for (String lemma : lemmas) {
            snippet = snippet.replaceAll("(?i)" + lemma, "<b>" + lemma + "</b>");
        }

        return snippet;
    }

}
