package de.intranda.goobi.ocr.tei;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;

import de.intranda.goobi.ocr.tei.HtmlToTEIConvert.ConverterMode;
import lombok.extern.log4j.Log4j;

@Log4j
public class TEIBuilder {
	

    public static final Namespace TEI = null;//Namespace.getNamespace("http://www.tei-c.org/ns/1.0");
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
            String text = texts.get(0);//new HtmlToTEIConvert(ConverterMode.resource).convert(texts.get(0));
        	content = new SAXBuilder().build(new StringReader("<div>" + unescape(text) + "</div>")).getRootElement().removeContent();
        } else {
        	content = new ArrayList<>();
        	for (String text : texts) {
//        	    text = new HtmlToTEIConvert(ConverterMode.resource).convert(text);
				Element section = new Element(SECTION_ELEMENT_NAME, TEI);
	        	section.addContent(new SAXBuilder().build(new StringReader("<div>" + unescape(text) + "</div>")).getRootElement().removeContent());
	        	content.add(section);
			}
        }
        body.addContent(content);
        return body;
    }
    
    private String unescape(String text) {
        text = text.replace("&lt;", "!!<<!!");
        text = text.replace("&gt;", "!!>>!!");
        text = StringEscapeUtils.unescapeHtml(text);
        text = text.replace("&", "&amp;");
        text = text.replace("!!<<!!", "&lt;");
        text = text.replace("!!>>!!", "&gt;");
        return text;
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
    	
    	text = formatText(text);
    	this.texts.add(text);
    	
    	return this;
    }

	protected String formatText(String text) {
		// bold+italic+underline
        for (MatchResult r : findRegexMatches(
                "title=\"(.*?)\">", text)) {
            text = text.replace(r.group(1), StringEscapeUtils.escapeHtml(r.group(1)));
        }
        return text;
	}
	
    public static Iterable<MatchResult> findRegexMatches(String pattern, CharSequence s) {
        List<MatchResult> results = new ArrayList<>();
        for (Matcher m = Pattern.compile(pattern)
                .matcher(s); m.find();) {
            results.add(m.toMatchResult());
        }
        return results;
    }
}
