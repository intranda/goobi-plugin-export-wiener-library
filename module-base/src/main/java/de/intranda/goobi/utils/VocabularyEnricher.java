package de.intranda.goobi.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.goobi.vocabulary.Field;
import org.goobi.vocabulary.VocabRecord;
import org.goobi.vocabulary.Vocabulary;

import de.sub.goobi.persistence.managers.VocabularyManager;



/**
 * Enriches a given text with the vocabulary defined in the constructor
 * 
 * @author florian
 *
 */
public class VocabularyEnricher {

    private static final Logger logger = Logger.getLogger(VocabularyEnricher.class);
    private final Vocabulary vocabulary;

    public VocabularyEnricher(String vocabulary) {
        this.vocabulary = VocabularyManager.getVocabularyByTitle(vocabulary);
        VocabularyManager.getAllRecords(this.vocabulary);
    }

    public VocabularyEnricher(Vocabulary vocabulary) {
        this.vocabulary = vocabulary;
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

            //handle long keywords first to handle cases where a keyword is a substring of another one (e.g. 'Bentschen' in 'Neu-Bentschen')
            Map<String, VocabRecord> keywordMap = new HashMap<>();
            for (VocabRecord record : vocabulary.getRecords()) {

                for (Field f : record.getFields()) {
                    if (f.getLabel().equals("Keywords")) {
                        keywordMap.put(f.getValue(), record);
                    }
                }
            }
            List<String> keywords = new ArrayList<>(keywordMap.keySet());
            keywords.sort( (k1, k2) -> Integer.compare(k2.length(), k1.length()) );

            String wordRegex = "(?<![a-zA-ZäÄüÜöÖß])({keyword})(?![a-zA-ZäÄüÜöÖß])";

            for (String keyword : keywords) {
                VocabRecord record = keywordMap.get(keyword);
                String title = "";
                String description = "";
                for (Field f : record.getFields()) {
                    if (f.getLabel().equals("Title")) {
                        title = f.getValue();
                    }else if (f.getLabel().equals("Description")) {
                        description = f.getValue();
                    }
                }
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
