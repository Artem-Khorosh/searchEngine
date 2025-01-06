package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    @Override
    public StatisticsResponse getStatistics() {
        List<searchengine.model.Site> sites = siteRepository.findAll();
        TotalStatistics total = new TotalStatistics();
        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        int totalPages = 0;
        int totalLemmas = 0;
        boolean isIndexing = false;

        for (Site site : sites) {
            int pages = pageRepository.countBySite(site);
            int lemmas = lemmaRepository.countBySite(site);

            totalPages += pages;
            totalLemmas += lemmas;
            if (site.getStatus() == Status.INDEXING) {
                isIndexing = true;
            }
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(site.getUrl());
            item.setName(site.getName());
            item.setStatus(site.getStatus().toString());
            item.setStatusTime(site.getStatusTime().toEpochSecond(ZoneOffset.UTC));
            item.setPages(pages);
            item.setLemmas(lemmas);

            if (site.getStatus() == Status.FAILED) {
                item.setError(site.getLastError());
            }
            detailed.add(item);
        }


        total.setSites(sites.size());
        total.setPages(totalPages);
        total.setLemmas(totalLemmas);
        total.setIndexing(isIndexing);


        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setTotal(total);
        statisticsData.setDetailed(detailed);


        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(statisticsData);
        return response;
    }
}
