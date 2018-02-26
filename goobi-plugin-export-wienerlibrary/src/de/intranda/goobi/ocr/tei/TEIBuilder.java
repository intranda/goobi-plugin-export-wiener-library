package de.intranda.goobi.ocr.tei;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;

import lombok.extern.log4j.Log4j;

@Log4j
public class TEIBuilder {
	

    public static final Namespace TEI = Namespace.getNamespace("http://www.tei-c.org/ns/1.0");
    protected static final Namespace XML = Namespace.getNamespace("xml", "http://www.w3.org/XML/1998/namespace");

    public static final String SECTION_ELEMENT_NAME = "seg";
    
    private List<String> texts = new ArrayList<>();
    private List<Element> headerContent = new ArrayList();
    private String language;
    
    public Document build() throws JDOMException, IOException {
        Document teiDocument = new Document();
        Element teiRoot = new Element("TEI", TEI);
        teiDocument.setRootElement(teiRoot);
//        teiRoot.setAttribute("id", getTeiId(), XML);
//        teiRoot.setAttribute("version", "5.0");

        Element teiHeader = createHeader(language);
        teiRoot.addContent(teiHeader);

        Element eleText = new Element("text", TEI);
        eleText.setAttribute("lang", language, XML);
        teiRoot.addContent(eleText);

        Element body = createBody(texts);
        eleText.addContent(body);

        return teiDocument;
    }
    
    protected Element createHeader(String language) throws JDOMException, IOException {
        Element teiHeader = new Element("teiHeader", TEI);
        return teiHeader;
    }
    
    protected Element createBody(List<String> texts) throws JDOMException, IOException {

        Element body = new Element("body", TEI);
        List<Content> content;
        if(texts.size() == 1) {
        	content = new SAXBuilder().build(new StringReader("<div>" + texts.get(0) + "</div>")).getRootElement().removeContent();
        } else {
        	content = new ArrayList<>();
        	for (String text : texts) {
				Element section = new Element(SECTION_ELEMENT_NAME, TEI);
	        	section.addContent(new SAXBuilder().build(new StringReader("<div>" + text + "</div>")).getRootElement().removeContent());
	        	content.add(section);
			}
        }
        body.addContent(content);
        return body;
    }
    
    public TEIBuilder setLanguage(String language) {
    	this.language = language;
    	return this;
    }
    
    public TEIBuilder addHeaderContent(Element content) {
    	this.headerContent.add(content);
    	return this;
    }
    
    public TEIBuilder addTextSegment(String text) {
    	this.texts.add(text);
    	return this;
    }
}
