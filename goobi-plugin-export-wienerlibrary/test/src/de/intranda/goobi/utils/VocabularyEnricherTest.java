package de.intranda.goobi.utils;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import de.intranda.goobi.plugins.vocabulary.Field;
import de.intranda.goobi.plugins.vocabulary.Record;
import de.intranda.goobi.plugins.vocabulary.Vocabulary;

public class VocabularyEnricherTest {

    @Test
    public void test() {
        Record bentschen = createRecord("Bentschen", "Bentschen", "Der Ort Namens Bentschen");
        Record altBentschen = createRecord("Alt-Bentschen", "Alt-Bentschen", "Der Ortsteil Alt-Bentschen");
        Record neuBentschen = createRecord("Neu-Bentschen", "Neu-Bentschen", "Der Ortsteil Neu-Bentschen");

        Vocabulary vocab = new Vocabulary();
        vocab.setRecords(new ArrayList<>());
        vocab.getRecords().add(neuBentschen);
        vocab.getRecords().add(altBentschen);
        vocab.getRecords().add(bentschen);
        
        String text ="Ich ging von Neu-Bentschen nach Bentschen über AltBentschen. Da hat es lange gedauert bis ich endlich in Bentschen ankam.";
        String expected ="Ich ging von <span>Neu-Bentschen<note><term>Neu-Bentschen</term>Der Ortsteil Neu-Bentschen</note></span> nach <span>Bentschen<note><term>Bentschen</term>Der Ort Namens Bentschen</note></span> über AltBentschen. Da hat es lange gedauert bis ich endlich in <span>Bentschen<note><term>Bentschen</term>Der Ort Namens Bentschen</note></span> ankam.";

        VocabularyEnricher enricher = new VocabularyEnricher(vocab);
        
        String enrichedtext = enricher.enrich(text);
        System.out.println(text);
        System.out.println(enrichedtext);
        Assert.assertEquals(expected, enrichedtext);
    }
    
    private Record createRecord(String label, String keywords, String description) {
        Field title = new Field("Title", label, null);
        Field key = new Field("Keywords", keywords, null);
        Field desc = new Field("Description", description, null);
        List<Field> fields = new ArrayList<>();
        fields.add(title);
        fields.add(key);
        fields.add(desc);
        return new Record("", fields);
    }

}
