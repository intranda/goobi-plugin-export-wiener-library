package de.intranda.goobi.utils;

import io.goobi.vocabulary.exchange.FieldDefinition;
import io.goobi.vocabulary.exchange.TranslationInstance;
import io.goobi.vocabulary.exchange.Vocabulary;
import io.goobi.vocabulary.exchange.VocabularySchema;
import io.goobi.workflow.api.vocabulary.VocabularyAPIManager;
import io.goobi.workflow.api.vocabulary.jsfwrapper.JSFVocabularyRecord;
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
    private final VocabularySchema schema;
    private final List<JSFVocabularyRecord> glossaryRecords;
    private final Map<String, JSFVocabularyRecord> keywordMapping;
    private final List<String> keywordProcessingOrder;
    private final long keywordFieldId;
    private final long titleFieldId;
    private final long descriptionFieldId;

    public VocabularyEnricher(String vocabulary) {
        this.vocabulary = vocabularyAPIManager.vocabularies().findByName(vocabulary);
        this.schema = vocabularyAPIManager.vocabularySchemas().get(this.vocabulary.getSchemaId());
        this.glossaryRecords = vocabularyAPIManager.vocabularyRecords().all(this.vocabulary.getId());
        this.keywordFieldId = findIdOfField(schema, "Keywords");
        this.titleFieldId = findIdOfField(schema, "Title");
        this.descriptionFieldId = findIdOfField(schema, "Description");
        this.keywordMapping = generateKeywordMapping();
        this.keywordProcessingOrder = generateKeywordProcessingOrder();
    }

    private Map<String, JSFVocabularyRecord> generateKeywordMapping() {
        Map<String, JSFVocabularyRecord> result = new HashMap<>();
        List<JSFVocabularyRecord> allRecords = vocabularyAPIManager.vocabularyRecords().all(this.vocabulary.getId());
        allRecords.forEach(r -> {
            Optional<String> value = extractField(r, this.keywordFieldId);
            value.ifPresent(s -> result.putIfAbsent(s, r));
        });
        return result;
    }

    private List<String> generateKeywordProcessingOrder() {
        List<String> result = new ArrayList<>(this.keywordMapping.keySet());
        //handle long keywords first to handle cases where a keyword is a substring of another one (e.g. 'Bentschen' in 'Neu-Bentschen')
        result.sort( (k1, k2) -> Integer.compare(k2.length(), k1.length()) );
        return result;
    }

    private long findIdOfField(VocabularySchema schema, String name) {
        return schema.getDefinitions().stream()
                .filter(d -> d.getName().equals(name))
                .map(FieldDefinition::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot find field \"" + name + "\" in vocabulary \"" + this.vocabulary.getName() + "\""));
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
                JSFVocabularyRecord record = keywordMapping.get(keyword);
                String title = extractField(record, this.titleFieldId).orElse("");
                String description = extractField(record, this.descriptionFieldId).orElse("");
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

    private Optional<String> extractField(JSFVocabularyRecord record, long fieldId) {
        return record.getFields().stream()
                .filter(f -> f.getDefinitionId().equals(fieldId))
                .flatMap(f -> f.getValues().stream()) // Assume there are no multi-values
                .flatMap(v -> v.getTranslations().stream()) // Assume there are no translations
                .map(TranslationInstance::getValue)
                .findFirst();
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
