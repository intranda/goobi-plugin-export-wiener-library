package de.intranda.goobi.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;

import de.intranda.goobi.plugins.vocabulary.Field;
import de.intranda.goobi.plugins.vocabulary.Record;
import de.intranda.goobi.plugins.vocabulary.Vocabulary;
import de.intranda.goobi.plugins.vocabulary.VocabularyManager;

/**
 * Enriches a given text with the vocabulary defined in the constructor
 * 
 * @author florian
 *
 */
public class VocabularyEnricher {
    
    private static final Logger logger = Logger.getLogger(VocabularyEnricher.class);
    private final Vocabulary vocabulary;
    
    public VocabularyEnricher(File configFile, String vocabulary) {
        XMLConfiguration config = loadConfig(configFile);
        config.setListDelimiter('&');
        config.setExpressionEngine(new XPathExpressionEngine());
        
        VocabularyManager vm = new VocabularyManager(config);
        vm.loadVocabulary(vocabulary);
        this.vocabulary = vm.getVocabulary();

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
            Map<String, Record> keywordMap = new HashMap<>();
            for (Record record : vocabulary.getRecords()) {
                for (String keyword : record.getAllKeywords()) {
                    keywordMap.put(keyword, record);
                }
            }
            List<String> keywords = new ArrayList<>(keywordMap.keySet());
            keywords.sort( (k1, k2) -> Integer.compare(k2.length(), k1.length()) );
            
            String wordRegex = "(?<![a-zA-ZäÄüÜöÖß])({keyword})(?![a-zA-ZäÄüÜöÖß])";
            
            for (String keyword : keywords) {
                Record record = keywordMap.get(keyword);
                String note = "<note><term>" + record.getTitle() + "</term>" + StringEscapeUtils.escapeHtml(record.getDescription()) + "</note>";
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

    /**
     * @param configFile
     * @return
     */
    private XMLConfiguration loadConfig(File configFile) {
        XMLConfiguration config;
        try {
            config = new XMLConfiguration(configFile);
        } catch (ConfigurationException e) {
            logger.error(e);
            config = new XMLConfiguration();
        }
        return config;
    }

}
