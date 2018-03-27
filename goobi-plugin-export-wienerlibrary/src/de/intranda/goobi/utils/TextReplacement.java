package de.intranda.goobi.utils;

import lombok.Data;

@Data
public class TextReplacement implements Comparable<TextReplacement> {

    private final int start;
    private final int end;
    private final String replacement;
    
    @Override
    public int compareTo(TextReplacement o) {
        return Integer.compare(this.start, o.start);
    }
    
}
