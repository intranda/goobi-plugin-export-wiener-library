package de.intranda.goobi.ocr.tei;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

public class HtmlToTEIConvert {

    private static final Logger logger = Logger.getLogger(HtmlToTEIConvert.class);

    private static final int HEADER_HIERARCHY_DEPTH = 9;
    private static final String HEADER_DIV_REGEX = "(<hx[\\S\\s]*?)(?=((<h\\d)|$))";

    private ConverterMode mode;

    public HtmlToTEIConvert(ConverterMode mode) {
        this.mode = mode;
    }

    public String convert(String text) {
        text = text.replace("&nbsp;", "");
        text = removeUrlEncoding(text);
        text = HtmlToTEIConvert.removeComments(text);
        text = "<div xmlns=\"http://www.tei-c.org/ns/1.0\">" + text + "</div>";

        for (int i = HEADER_HIERARCHY_DEPTH; i > 0; i--) {
            String regex = HEADER_DIV_REGEX.replace("x", Integer.toString(i));
            for (MatchResult r : findRegexMatches(regex, text)) {
                String group = r.group();
                text = text.replace(r.group(), "<div>" + r.group() + "</div>");
            }
            // replace header
            for (MatchResult r : findRegexMatches("<h" + i + ".*?>(.*?)</h" + i + ">", text)) {
                text = text.replace(r.group(), "<head>" + r.group(1) + "</head>");
            }
        }

        // remove empty <p>'s
        text = text.replace("<p />", "")
                .replace("<p/>", "")
                .replace("<p></p>", "");

        // replace bold
        boolean found = false;
        do {
            found = false;
            for (MatchResult r : findRegexMatches("<strong>((.*?(<strong>.*?<\\/strong>)?.*?)*?)</strong>", text)) {
                String group = r.group();
                text = text.replace(r.group(), "<hi rend=\"bold\">" + r.group(1) + "</hi>");
                found = true;
            }
        } while (found);

        // replace italic
        do {
            found = false;
            for (MatchResult r : findRegexMatches("<em>((.*?(<em>.*?<\\/em>)?.*?)*?)</em>", text)) {
                text = text.replace(r.group(), "<hi rend=\"italic\">" + r.group(1) + "</hi>");
                found = true;
            }
        } while (found);

        // replace underline
        do {
            found = false;
            for (MatchResult r : findRegexMatches(
                    "<span style=\"text-decoration: underline;\">((.*?(<span style=\"text-decoration: underline;\">.*?<\\/span>)?.*?)*?)</span>",
                    text)) {
                text = text.replace(r.group(), "<hi rend=\"underline\">" + r.group(1) + "</hi>");
                found = true;
            }
        } while (found);

        // replace anm
        for (MatchResult r : findRegexMatches("\\[anm\\](.*?)\\[/anm\\]", text)) {
            text = text.replace(r.group(), "<note type=\"editorial\"><p>" + r.group(1) + "</p></note>");
        }

        //Footnotes 
        List<Footnote> footnoteTypes = getAllFootnoteTypes();
        text = replaceFootnotes(text, footnoteTypes);

        // tables
        text = text.replaceAll("<table.*?>", "<table>")
                .replace("<tbody>", "")
                .replace("</tbody>", "");
        text = text.replace("<caption>", "<head>")
                .replace("</caption>", "</head>");
        text = text.replace("<tbody>", "")
                .replace("</tbody>", "");
        text = text.replace("<thead>", "")
                .replace("</thead>", "");
        text = text.replaceAll("<tr.*?>", "<row>")
                .replace("<tr>", "<row>")
                .replace("</tr>", "</row>");
        text = text.replaceAll("<td.*?>", "<cell>")
                .replace("</td>", "</cell>");

        // lists
        if (mode.equals(ConverterMode.annotation)) {
            text = text.replaceAll("<ul.*?>", "<list rend=\"bulleted\">")
                    .replace("</ul>", "</list>");
            text = text.replace("<li>", "<item>")
                    .replace("</li>", "</item>");
            text = text.replaceAll("<ol.*?style=\".*?-alpha.*?>", "<list rend=\"alphabetical\">")
                    .replace("</ol>", "</list>");
            text = text.replaceAll("<ol.*?style=\".*?-greek.*?>", "<list rend=\"alphabetical\">")
                    .replace("</ol>", "</list>");
            text = text.replaceAll("<ol.*?>", "<list rend=\"numbered\">")
                    .replace("</ol>", "</list>");
        } else {
            text = text.replaceAll("<ul.*?>", "<list>")
                    .replace("</ul>", "</list>");
            text = text.replace("<li>", "<item>")
                    .replace("</li>", "</item>");
            //			text = text.replace("<ol>", "<list>").replace("</ol>", "</list>");
            text = text.replaceAll("<ol.*?>", "<list>")
                    .replace("</ol>", "</list>");
        }

        // images
        // <img src="none" alt="Bildbeschriftung" />
        for (MatchResult r : findRegexMatches("<img src=\"(.*?)\" alt=\"(.*?)\" />", text)) {
            text = text.replace(r.group(), "<figure><head>" + r.group(2) + "</head><graphic url=\"" + r.group(1) + "\"/></figure>");
        }

        // Blockquote (old)
        for (MatchResult r : findRegexMatches("<blockquote>\\s*<p>\\[Q=(.*?)\\](.*?)\\[/Q\\]</p>\\s*</blockquote>", text)) {
            text = text.replace(r.group(), "<cit><q source=\"#" + r.group(1) + "\">" + r.group(2) + "</q></cit>");
        }

        // Blockquote (with reference)
        int quoteRefCounter = 1;
        for (MatchResult r : findRegexMatches("<blockquote\\s+cite=\"(.*?)\">\\s*([\\s\\S]*?)\\s*<\\/blockquote>", text)) {
            StringBuilder replacement = new StringBuilder();
            replacement.append("<cit> ")
                    .append(mode.equals(ConverterMode.resource) ? "<q" : "<quote")
                    .append(" source=\"#quoteref")
                    .append(quoteRefCounter)
                    .append("\">")
                    .append(r.group(2))
                    .append(mode.equals(ConverterMode.resource) ? "</q>" : "</quote>")
                    .append(" <ref type=\"bibl\" xml:id=\"quoteref")
                    .append(quoteRefCounter)
                    .append("\" target=\"#ref")
                    .append(quoteRefCounter)
                    .append("\">")
                    .append(r.group(1))
                    .append("</ref>")
                    .append("</cit>");
            text = text.replace(r.group(), replacement.toString());
            quoteRefCounter++;
        }

        // Blockquote (no reference)
        //		for (MatchResult r : findRegexMatches("<blockquote>\\s*(<p>)*([\\s\\S]*?)(<\\/p>)*\\s*<\\/blockquote>",
        for (MatchResult r : findRegexMatches("<blockquote>\\s*([\\s\\S]*?)\\s*<\\/blockquote>", text)) {
            StringBuilder replacement = new StringBuilder();
            replacement.append("<cit>")
                    .append(mode.equals(ConverterMode.resource) ? "<q>" : "<quote source=\"#\">")
                    //				.append( " source=\"#\"")
                    //				.append(">")
                    .append(r.group(1))
                    .append(mode.equals(ConverterMode.resource) ? "</q>" : "</quote>")
                    .append("</cit>");
            text = text.replace(r.group(), replacement.toString());
        }

        for (MatchResult r : findRegexMatches("\\[Q=(.*?)\\](.*?)\\[/Q\\]", text)) {
            text = text.replace(r.group(), "<q source=\"#" + r.group(1) + "\">" + r.group(2) + "</q>");
        }

        //q with cite
        for (MatchResult r : findRegexMatches("<q\\s+cite=\"(.*?)\">([\\s\\S]*?)<\\/q>", text)) {
            if (mode.equals(ConverterMode.annotation)) {
                text = text.replace(r.group(),
                        "<quote source=\"#quoteref" + quoteRefCounter + "\" type=\"direct\">" + r.group(2) + "</quote>"
                                + "(<ref type=\"bibl\" xml:id=\"quoteref" + quoteRefCounter + "\" target=\"#ref" + quoteRefCounter + "\">"
                                + r.group(1) + "</ref>)");
            } else {
                text = text.replace(r.group(),
                        "<q source=\"#quoteref" + quoteRefCounter + "\" type=\"direct\">" + r.group(2) + "</q>"
                                + "(<ref type=\"bibl\" xml:id=\"quoteref" + quoteRefCounter + "\" target=\"#ref" + quoteRefCounter + "\">"
                                + r.group(1) + "</ref>)");
            }
            quoteRefCounter++;
        }

        for (MatchResult r : findRegexMatches("\\[q\\](.*?)\\[/q\\]", text)) {
            text = text.replace(r.group(), "<q>" + r.group(1) + "</q>");
        }

        for (MatchResult r : findRegexMatches("<a.*?href=\"(.*?)\".*?>(.*?)<\\/a>", text)) {
            if (ConverterMode.annotation.equals(mode)) {
                text = text.replace(r.group(), "<ref target=\"" + r.group(1) + "\">" + r.group(2) + "</ref>");
            } else {
                text = text.replace(r.group(), "<ref target=\"" + r.group(1) + "\" type=\"url\">" + r.group(2) + "</ref>");
            }
        }

        for (MatchResult r : findRegexMatches("<a\\s*(\\w+=\".*\"\\s*)*>(.*?)</a>", text)) {
            text = text.replace(r.group(), r.group(2));
        }

        //add <head></head> to every div that doesn't start with head
        //		if(mode.equals(ConverterMode.annotation)) {		
        //		    
        //		    while(true) {
        //		        Iterable<MatchResult> results = findRegexMatches("<div[^>]*>\\s*(?!<head>)", text);
        //		        if(results.iterator().hasNext()) {
        //		            MatchResult result = results.iterator().next();
        //		            String group = result.group();
        //		            text = text.substring(0, result.start()) + group + "<head></head>" + text.substring(result.end());
        //		        } else {
        //		            break;
        //		        }
        //		    }
        //		}

        text = text.replace("<br />", "");
        text = text.replace("<p />", "");
        text = text.replaceAll("<div[^>]*/>", "");

        return text.trim();
    }

    /**
     * @param text
     * @param footnoteTypes
     * @return
     */
    protected String replaceFootnotes(String text, List<Footnote> footnoteTypes) {
        int index = 1;
        for (Footnote footnote : footnoteTypes) {
            String regex = footnote.getReferenceRegex();
            for (MatchResult r : findRegexMatches(regex, text)) {
                try {
                    String number = r.groupCount() > 1 ? r.group(2) : Integer.toString(index); 
                    if (StringUtils.isNotBlank(number)) {
                        String noteRegex = footnote.getNoteRegex(number);
                        MatchResult result = findRegexMatches(noteRegex, text).iterator()
                                .next();
                        String note = result.group(1);
                        text = text.replace(result.group(), "");
                        if (!note.trim()
                                .startsWith("<p>")) {
                            note = "<p>" + note;
                        }
                        if (!note.trim()
                                .endsWith("</p>")) {
                            note = note + "</p>";
                        }
                        String reference = r.group(1);
                        text = text.replace(reference, " <note>" + note + "</note> ");
                    }
                } catch (NoSuchElementException | NullPointerException e) {
                    logger.error("Cannot find footnote to reference " + r.group() + ". Removing reference");
                    text = text.replace(r.group(1), " ");
                }
            }
            index++;
        }
        return text;
    }

    public static String removeComments(String text) {
        text = text.replaceAll("<!--[\\w\\W]*?-->", "");
        return text;
    }

    /**
     * @param text
     * @return
     * @throws IOException
     */
    public static String removeUrlEncoding(String text) {
        StringWriter writer = new StringWriter();
        try {
            text = text.replace("&amp;", "&amp;amp;")
                    .replace("&gt;", "&amp;gt;")
                    .replace("&lt;", "&amp;lt;");
            StringEscapeUtils.unescapeHtml(writer, text);
            //            text = writer.toString();
            //            writer = new StringWriter();
            //            StringEscapeUtils.escapeXml(writer, text);
            return writer.toString();
        } catch (IOException e) {
            logger.error(e.toString(), e);
        }
        return text;

        //		text = text.replace("&amp;", "&");
        //		text = text.replace("&Auml;", "Ä");
        //		text = text.replace("&Ouml;", "Ö");
        //		text = text.replace("&Uuml;", "Ü");
        //
        //		text = text.replace("&auml;", "ä");
        //		text = text.replace("&ouml;", "ö");
        //		text = text.replace("&uuml;", "ü");
        //
        //		text = text.replace("&szlig;", "ß");
        //		text = text.replace("&nbsp;", "");
        //		text = text.replace("&shy;", "-");
        //		return text;
    }

    public static Iterable<MatchResult> findRegexMatches(String pattern, CharSequence s) {
        List<MatchResult> results = new ArrayList<MatchResult>();
        for (Matcher m = Pattern.compile(pattern)
                .matcher(s); m.find();) {
            results.add(m.toMatchResult());
        }
        return results;
    }

    public static enum ConverterMode {
        annotation,
        resource
    }

    public List<Footnote> getAllFootnoteTypes() {
        List<Footnote> list = new ArrayList<>();
        list.add(new SimpleFootnote("<span title=\"[^>]*\">([^<]*)<\\/span>", "<span title=\"([^>]*)\">[^<]*<\\/span>"));
//        list.add(new SimpleFootnote("(<a.*?#_ftn\\d.*?\\[(\\d+)\\].*?<\\/a>)",
//                "<p><a.*?#_ftnref\\d.*?\\[§\\].*?<\\/a>(.*?)<\\/p>(?=(\\s*<p><a.*?#_ftnref\\d)|\\s*$|\\s*<\\/div>)"));
//        list.add(new SimpleFootnote(
//                "(?<!<p>)(<a class=\"sdfootnoteanc\" href=\"#sdfootnote\\d+sym\" name=\"sdfootnote\\d+anc\"><sup>(\\d+)<\\/sup></a>)",
//                "<p>\\s*<a class=\"sdfootnotesym\" href=\"#sdfootnote§anc\" name=\"sdfootnote§sym\">§<\\/a>(.*?)<\\/p>(?=(\\s*<p>\\s*<a class=\"sdfootnotesym)|\\s*$|\\s*<\\/div>)"));
//        list.add(new SimpleFootnote("(?<!<p>)(<a href=\"#_ftn\\d+\"\\s+name=\"_ftnref\\d+\">\\[(\\d+)\\]<\\/a>)",
//                "<p><a href=\"#_ftnref\\d+\"\\s+name=\"_ftn\\d+\">\\[§\\]<\\/a>\\s*(.*?)<\\/p>(?=(\\s*<p><a href=\"#_ftnref\\d+)|\\s*$|\\s*<\\/div>)"));
//        list.add(new SimpleFootnote("(?<!<p>)(<sup>(\\d+)<\\/sup>)",
//                "<p>\\s*<sup>§<\\/sup>\\s*(.*?)<\\/p>(?=(\\s*<p>\\s*<sup>\\d+<\\/sup>)|\\s*$|\\s*<\\/div>)"));
//        list.add(new SimpleFootnote("(?<!<p>)(\\[(\\d+)\\]\\s*<#_ftn\\d+>)",
//                "<p>\\s*\\[§\\]\\s*<#_ftnref§>\\s*(.*?)<\\/p>(?=(\\s*<p>\\s*\\[\\d+\\])|\\s*$|\\s*<\\/div>)"));
//        list.add(new SimpleFootnote("(?<!<p>)(\\[(\\d+)\\])", "<p>\\s*\\[§\\]\\s*([\\w\\W]*?)(?=(\\s*<p>\\s*\\[\\d+\\])|\\s*$|\\s*<\\/div>)"));
        return list;
    }

}
