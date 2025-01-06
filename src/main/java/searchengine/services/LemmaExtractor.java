package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class LemmaExtractor {
    private final LuceneMorphology luceneMorphology;
    private static final String WORD_TYPE_REGEX = "\\W\\w&&[^а-яА-Я\\s]";
    private static final String[] PARTICLES_NAMES = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"};



    public LemmaExtractor() throws IOException {
        this.luceneMorphology = new RussianLuceneMorphology();
    }

    public HashMap<String, Integer> extractLemmas(String text) {
        String[] words = arrayContainsRussianWords(text);
        HashMap<String, Integer> lemmaFrequency = new HashMap<>();

        for (String word : words) {
            if (isCorrectWordForm(word)) {
                List<String> wordBaseForms = luceneMorphology.getNormalForms(word);
                if (!anyWordBaseBelongToParticle(wordBaseForms)) {
                    for (String baseForm : wordBaseForms) {
                        lemmaFrequency.put(baseForm, lemmaFrequency.getOrDefault(baseForm, 0) + 1);
                    }
                }
            }
        }
        return lemmaFrequency;
    }


    public Set<String> getLemmaSet(String text) {

        return extractLemmas(text).keySet();
    }

    public void saveLemmasAndIndexes(Page page,
                                     Map<String, Integer> lemmas,
                                     LemmaRepository lemmaRepository,
                                     IndexRepository indexRepository) {
        List<Lemma> lemmaList = new ArrayList<>();
        List<Index> indexList = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            Lemma lemma = new Lemma();
            lemma.setSite(page.getSite());
            lemma.setLemma(entry.getKey());
            lemma.setFrequency(entry.getValue());
            lemmaList.add(lemma);

            Index index = new Index();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(entry.getValue());
            indexList.add(index);
        }

        lemmaRepository.saveAll(lemmaList);
        indexRepository.saveAll(indexList);
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }

    private boolean hasParticleProperty(String wordBase) {
        for (String property : PARTICLES_NAMES) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private String[] arrayContainsRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-я\\s])", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .split("\\s+");
    }
    private boolean isCorrectWordForm(String word) {
        return word.matches(WORD_TYPE_REGEX);
    }
}
