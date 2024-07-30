package de.intranda.goobi.utils;

import io.goobi.vocabulary.exchange.Vocabulary;
import io.goobi.workflow.api.vocabulary.VocabularyAPIManager;
import io.goobi.workflow.api.vocabulary.helper.ExtendedVocabularyRecord;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Enriches a given text with the vocabulary defined in the constructor
 * 
 * @author florian
 *
 */
public class VocabularyEnricher {
    private static final Logger logger = Logger.getLogger(VocabularyEnricher.class);

    private VocabularyAPIManager vocabularyAPIManager = VocabularyAPIManager.getInstance();
    private final Vocabulary vocabulary;
    private final Map<String, ExtendedVocabularyRecord> keywordMapping;
    private final List<String> keywordProcessingOrder;

    public VocabularyEnricher(String vocabulary) {
        this.vocabulary = vocabularyAPIManager.vocabularies().findByName(vocabulary);
        this.keywordMapping = generateKeywordMapping();
        this.keywordProcessingOrder = generateKeywordProcessingOrder();
    }

    private Map<String, ExtendedVocabularyRecord> generateKeywordMapping() {
        Map<String, ExtendedVocabularyRecord> result = new HashMap<>();
        List<ExtendedVocabularyRecord> allRecords = vocabularyAPIManager.vocabularyRecords()
                .list(this.vocabulary.getId())
                .all()
                .request()
                .getContent();
        allRecords.forEach(r -> {
            try {
                Optional<String> value = r.getFieldValueForDefinitionName("Keywords");
                value.ifPresent(v -> result.putIfAbsent(v, r));
            } catch (RuntimeException e) {
                // Ignore missing values
            }
        });
        return result;
    }

    private List<String> generateKeywordProcessingOrder() {
        List<String> result = new ArrayList<>(this.keywordMapping.keySet());
        //handle long keywords first to handle cases where a keyword is a substring of another one (e.g. 'Bentschen' in 'Neu-Bentschen')
        result.sort( (k1, k2) -> Integer.compare(k2.length(), k1.length()) );
        return result;
    }

    /**
     * 
     * @param text
     * @return the given text enrichted with noted generated from the vocabulary
     */
    public String enrich(String text) {
        String result = text;
        try {
            List<TextReplacement> locations = new ArrayList<>();

            String wordRegex = "(?<![a-zA-ZäÄüÜöÖß])({keyword})(?![a-zA-ZäÄüÜöÖß])";

            for (String keyword : keywordProcessingOrder) {
                ExtendedVocabularyRecord record = keywordMapping.get(keyword);
                String title = record.getFieldValueForDefinitionName("Title").orElseThrow();
                String description = record.getFieldValueForDefinitionName("Description").orElseThrow();
                String note = "<note><term>" + title + "</term>" + StringEscapeUtils.escapeHtml(description) + "</note>";
                String regex = wordRegex.replace("{keyword}", keyword);
                Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(result);
                while (m.find()) {
                    int start = m.start();
                    int end = m.end();
                    if(!withinLocation(start, end, locations)) {
                        locations.add(new TextReplacement(start, end, note));
                    }
                }
            }

            Collections.sort(locations);
            Collections.reverse(locations);
            for (TextReplacement location : locations) {
                String span = "<span>" + result.substring(location.getStart(), location.getEnd()) + location.getReplacement() + "</span>";
                result = result.substring(0, location.getStart()) + span + result.substring(location.getEnd());
            }
            return result;
        } catch (Exception e) {
            logger.error("Can't load vocabulary management", e);
            return text;
        }
    }

    private boolean withinLocation(int start, int end, List<TextReplacement> locations) {
        for (TextReplacement location : locations) {
            if(start <= location.getEnd() && end >= location.getStart()) {
                return true;
            }
        }
        return false;
    }
}
