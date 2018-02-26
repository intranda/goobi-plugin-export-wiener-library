package de.intranda.goobi.ocr.tei;

import static org.junit.Assert.*;

import org.junit.Test;

public class TEIBuilderTest {

	@Test
	public void testFormatText() {
		String text = "(Sie kam in <span title=\"<p>Ein Ort, an dem vor allem die <strong>j&uuml;dische Bev&ouml;lkerung</strong> gefangen gehalten wurde.</p>\"Auschwitz</span> um, ";
		TEIBuilder builder = new TEIBuilder();
		String result = builder.formatText(text);
		System.out.println(text);
		System.out.println(result);
	}

}
