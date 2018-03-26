package de.intranda.goobi.ocr.tei;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.output.XMLOutputter;
import org.junit.Test;

import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.fileformats.mets.MetsMods;

public class TEIBuilderTest {

	@Test
	public void testFormatText() {
		String text = "(Sie kam in <span title=\"<p>Ein Ort, an dem vor allem die <strong>j&uuml;dische Bev&ouml;lkerung</strong> gefangen gehalten wurde.</p>\"Auschwitz</span> um, ";
		TEIBuilder builder = new TEIBuilder();
		String result = builder.formatText(text);
		System.out.println(text);
		System.out.println(result);
	}

	@Test
	public void testReadMets() throws PreferencesException, ReadException, JDOMException, IOException {
	    File meta = new File("test/resources/meta_2.xml");
	    File ruleset = new File("test/resources/ruleset.xml");
	    File output = new File("test/output");
	    if(output.exists()) {
	        FileUtils.cleanDirectory(output);
	    } else {
	        output.mkdir();
	    }
	    Prefs prefs = new Prefs();
	    prefs.loadPrefs(ruleset.getAbsolutePath());
	    Fileformat ff = new MetsMods(prefs);
	    ff.read(meta.getAbsolutePath());
	    
	    List<? extends Metadata> mdList = ff.getDigitalDocument().getLogicalDocStruct().getAllChildren().stream().flatMap(ds -> ds.getAllMetadata().stream()).filter(md -> md.getType().getName().startsWith("Translation") || md.getType().getName().startsWith("Transcription")).collect(Collectors.toList());
	    mdList.forEach(md -> {
	        System.out.println(md.getType().getName() + "\t" + md.getValue());
	    });
	    
	    TEIBuilder builder = new TEIBuilder();
	    String language = mdList.get(0).getType().getName().substring(mdList.get(0).getType().getName().lastIndexOf("_")+1);
	    System.out.println("Language = " + language);
	    builder.setLanguage(language);
	    mdList.forEach(md -> {
	        builder.addTextSegment(md.getValue());
	    });
	    Document doc = builder.build();
	    XMLOutputter writer = new XMLOutputter();
        writer.output(builder.build(), new FileWriter(new File(output, "tei_" + language + ".xml")));
	}
}
