package de.intranda.goobi.ocr.tei;

public interface Footnote {

    public String getReferenceRegex();
    public String getNoteRegex(String number);
        
}
