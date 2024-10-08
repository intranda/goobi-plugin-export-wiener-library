package de.intranda.goobi.utils;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.forms.SpracheForm;
import de.sub.goobi.helper.Helper;
import io.goobi.vocabulary.exchange.FieldDefinition;
import io.goobi.vocabulary.exchange.Vocabulary;
import io.goobi.vocabulary.exchange.VocabularyRecord;
import io.goobi.vocabulary.exchange.VocabularySchema;
import io.goobi.workflow.api.vocabulary.FieldTypeAPI;
import io.goobi.workflow.api.vocabulary.LanguageAPI;
import io.goobi.workflow.api.vocabulary.VocabularyAPI;
import io.goobi.workflow.api.vocabulary.VocabularyAPIManager;
import io.goobi.workflow.api.vocabulary.VocabularyRecordAPI;
import io.goobi.workflow.api.vocabulary.VocabularySchemaAPI;
import io.goobi.workflow.api.vocabulary.hateoas.VocabularyRecordPageResult;
import io.goobi.workflow.api.vocabulary.helper.ExtendedVocabulary;
import io.goobi.workflow.api.vocabulary.helper.ExtendedVocabularyRecord;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigurationHelper.class, VocabularyAPIManager.class, Helper.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*", "jdk.internal.reflect.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*",
        "org.w3c.*", "javax.crypto.*", "javax.crypto.JceSecurity" })
public class VocabularyEnricherTest {

    private static final String TESTIMONY = "<p><span style=\"text-decoration: underline;\">The &ldquo;Polenaktion&rdquo;</span></p>\n"
            + "<p>On 28 October I was in Elberfeld on business. I went to the aliens&rsquo; branch of the police to have my stay, which was going to expire on 1 November extended, to be able to continue with my preparations for emigrating to North America. The officer returned after 5 minutes and supposedly sent me to the Polizeipr&auml;sident<a href=\"#_ftn1\" name=\"_ftnref1\">[1]</a> accompanied by one other officer. However, I was brought to the police prison. The prison officer explained &ldquo;You are going to be held in custody pending deportation&rdquo;. When checked for weapons and money, I was allowed to keep 45 Reichsmark, and I was also permitted to smoke. I was locked in a cell and asked the officer to notify my parents. After one hour, there were already ten Poles in the cell. The personal details were only taken then. The passports were retained.</p>\n"
            + "<p>Around 200 Poles were arrested in Elberfeld, men, women and children. An officer came to our cell after about 2 hours with forms, which in summary stated that we agreed to be deported, that we had a right to complain within 14 days, which however would not repeal immediate deportation. Everyone agreed to say no to this. I also said no as the last person, as I had an appointment at the American consulate in Stuttgart on 15 November. After a quarter of an hour, the Oberkommissar arrived with another three gentlemen, who tried to exert pressure. Our answer was again no. The officers then walked away while saying: &ldquo;We will show you anyway&rdquo;. The women were then separated from the men, they got back their passports and were released. They were told that the men would be deported about 7 o&rsquo; clock. They could bring them clothes etc.</p>\n"
            + "<p>The men were then called up again individually and it was said that we were going to be deported to Poland, but not where exactly. We were then transported to the train station in buses which were closely guarded. I met my father and brother there. 40 persons were then put into each 3rd class wagon. We were treated decently by the officers. Each wagon received sausage sandwiches and a large pot of coffee. The wagons were then locked. No window could be opened at the stations we travelled through. We travelled through Hanover, Berlin, Frankfurt an der Oder to Neu-Bentschen. We were unloaded there and put onto a Polish train. Before this, our money was checked, but the officers were very generous. For example, I was permitted to keep RM 42.-, but another had to hand over RM 520. of RM 530.. We then travelled to Alt-Bentschen (Zbaszyn).</p>\n"
            + "<p>On the way from Neu-Bentschen to Alt-Bentschen, we saw around 1000 people on the country road, among them the very old, young children, pushchairs etc. This was the transport from Hamburg, which had arrived at the Polish border</p>\n"
            + "<hr class=\"wienerlibrary-pagebreak\" />\n" + "<p>- 2 -</p>\n"
            + "<p>at 7.00 am and sent them on their onward journey by foot. These people had been received by the Polish border officials with fixed bayonet, and when they wanted to return were clubbed back by the German uniformed police and SS with the words: &ldquo;You can carry on, they are too cowardly to shoot after all.&rdquo; The Polish officers then gave the command to lie down; and everyone had to throw themselves onto the wet country road. Three warning shots were then fired, and everyone was waved through.</p>\n"
            + "<p>We arrived in Alt-Bentschen on Saturday, 29 October at 7.30 pm. A customs check had been announced, which did not take place in the end due probably to the general confusion. A train from Nuremberg was already standing at the platform of Alt-Bentschen train station, which had arrived one hour previously but was still locked. These people were only released on Saturday at 6.00<a href=\"#_ftn2\" name=\"_ftnref2\">[2]</a>.</p>\n"
            + "<p>11,000 people were supposed to be in Alt-Bentschen at first, from Berlin, D&uuml;sseldorf, Wuppertal, Remscheidt, Stuttgart, Dortmund, Essen, Duisburg, Hamburg, Hanover, Cologne and some from Vienna. Those who had enough financial means were able to journey on first into the country. About 6,000 stayed behind. But on Monday, 31 October, a train from Alt-Bentschen, which intended to continue on, was stopped in Poznan. About 4,000 people were said to be in Poznan at the time.</p>\n"
            + "<p>There was no help, as Poland had apparently not been informed about the German &ldquo;Aktion&rdquo;. We were all led to a large square. At 5.00 pm we were told we had to register and specify where we had relatives in Poland, as we could probably travel there as early as the next day.</p>\n"
            + "<p>There were 6 Polish officers to record our personal data. But there was such a rush that the table with the officers fell over, and no more details were taken. The population was very sympathetic. People brought straw, and everyone tried to get some in order to sleep in the horse barracks. The rest who did not go into the barracks stayed behind in the train station waiting rooms or the station concourse. Registration continued on Sunday, Monday and Monday night, and we still had no help and tried to sort things out a little ourselves.</p>\n"
            + "<p>On Monday morning we were told that those with children and old people could travel to their relatives. These were called up again and had to leave the train station around 2.00 pm to get their tickets, as they were supposed to leave around 4.00 pm. At 3.30 pm, a phone call was received from Warsaw, stating that everyone had to stay in Alt-Bentschen.</p>\n"
            + "<p>The population was very helpful and also took in refugees,</p>\n" + "<hr class=\"wienerlibrary-pagebreak\" />\n" + "<p>- 3 -</p>\n"
            + "<p>in part without any payment and for little compensation. The Jewish support committee then paid for this. I found a room with a lady together with my father and my brother.</p>\n"
            + "<p>The population is very anti-German. The German radio station was only played most of the time in one bar. The Polish officers were also decent, but angry about the fact that no Polish was spoken.</p>\n"
            + "<p>By Saturday 13th November, 7 people had died, among them one young girl aged 19. Two children were born.</p>\n"
            + "<hr class=\"wienerlibrary-pagebreak\" />\n"
            + "<p><a href=\"#_ftnref1\" name=\"_ftn1\">[1]</a> I am not sure if this should be kept in German as instructed (retain original names and titles), or translated?</p>\n"
            + "<p><a href=\"#_ftnref2\" name=\"_ftn2\">[2]</a> It is not clear if this is am or pm</p>";


    private long idCounter;
    private VocabularySchema vocabularySchema;

    @Before
    public void setUp() throws Exception {
        SpracheForm spracheForm = EasyMock.createMock(SpracheForm.class);
        EasyMock.expect(spracheForm.getLocale()).andReturn(Locale.ENGLISH).anyTimes();
        EasyMock.replay(spracheForm);

        PowerMock.mockStatic(Helper.class);
        EasyMock.expect(Helper.getCurrentUser()).andReturn(null).anyTimes();
        EasyMock.expect(Helper.getLanguageBean()).andReturn(spracheForm).anyTimes();
        PowerMock.replay(Helper.class);

        VocabularySchemaAPI vocabularySchemaAPI = EasyMock.createMock(VocabularySchemaAPI.class);
        vocabularySchema = new VocabularySchema();
        vocabularySchema.setId(idCounter++);
        vocabularySchema.setDefinitions(prepareSchemaDefinitions());
        vocabularySchema.getDefinitions().forEach(d ->
                EasyMock.expect(vocabularySchemaAPI.getDefinition(d.getId())).andReturn(d).anyTimes()
        );
        EasyMock.expect(vocabularySchemaAPI.get(vocabularySchema.getId())).andReturn(vocabularySchema).anyTimes();
        EasyMock.expect(vocabularySchemaAPI.getSchema((VocabularyRecord) EasyMock.anyObject())).andReturn(vocabularySchema).anyTimes();
        EasyMock.replay(vocabularySchemaAPI);

        LanguageAPI languageAPI = EasyMock.createMock(LanguageAPI.class);
        EasyMock.replay(languageAPI);

        FieldTypeAPI fieldTypeAPI = EasyMock.createMock(FieldTypeAPI.class);
        EasyMock.replay(fieldTypeAPI);

        VocabularyAPI vocabularyAPI = EasyMock.createMock(VocabularyAPI.class);
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setName("Glossary");
        vocabulary.setId(idCounter++);
        vocabulary.setSchemaId(vocabularySchema.getId());
        EasyMock.expect(vocabularyAPI.findByName("Glossary")).andReturn(new ExtendedVocabulary(vocabulary)).anyTimes();
        EasyMock.replay(vocabularyAPI);

        VocabularyRecordAPI vocabularyRecordAPI = EasyMock.createMock(VocabularyRecordAPI.class);

        PowerMock.mockStatic(VocabularyAPIManager.class);
        VocabularyAPIManager vocabularyAPIManager = EasyMock.createMock(VocabularyAPIManager.class);
        EasyMock.expect(VocabularyAPIManager.getInstance()).andReturn(vocabularyAPIManager).anyTimes();
        EasyMock.expect(vocabularyAPIManager.languages()).andReturn(languageAPI).anyTimes();
        EasyMock.expect(vocabularyAPIManager.fieldTypes()).andReturn(fieldTypeAPI).anyTimes();
        EasyMock.expect(vocabularyAPIManager.vocabularies()).andReturn(vocabularyAPI).anyTimes();
        EasyMock.expect(vocabularyAPIManager.vocabularySchemas()).andReturn(vocabularySchemaAPI).anyTimes();
        EasyMock.expect(vocabularyAPIManager.vocabularyRecords()).andReturn(vocabularyRecordAPI).anyTimes();
        PowerMock.replay(VocabularyAPIManager.class);
        EasyMock.replay(vocabularyAPIManager);

        ExtendedVocabularyRecord bentschen = createRecord("Bentschen", "Bentschen", "Der Ort Namens Bentschen");
        ExtendedVocabularyRecord altBentschen = createRecord("Alt-Bentschen", "Alt-Bentschen", "Der Ortsteil Alt-Bentschen");
        ExtendedVocabularyRecord neuBentschen = createRecord("Neu-Bentschen", "Neu-Bentschen", "Der Ortsteil Neu-Bentschen");

        VocabularyRecordPageResult resultPage = new VocabularyRecordPageResult();
        resultPage.setContent(List.of(bentschen, altBentschen, neuBentschen));
        VocabularyRecordAPI.VocabularyRecordQueryBuilder query = EasyMock.createMock(VocabularyRecordAPI.VocabularyRecordQueryBuilder.class);
        EasyMock.expect(query.search(EasyMock.anyString())).andReturn(query).anyTimes();
        EasyMock.expect(query.all()).andReturn(query).anyTimes();
        EasyMock.expect(query.request()).andReturn(resultPage).anyTimes();
        EasyMock.replay(query);
        EasyMock.expect(vocabularyRecordAPI.list(EasyMock.anyLong())).andReturn(query).anyTimes();
        EasyMock.replay(vocabularyRecordAPI);
    }

    @Test
    public void test() {
        idCounter = 0;

        String text = "Ich ging von Neu-Bentschen nach Bentschen über AltBentschen. Da hat es lange gedauert bis ich endlich in Bentschen ankam.";
        String expected =
                "Ich ging von <span>Neu-Bentschen<note><term>Neu-Bentschen</term>Der Ortsteil Neu-Bentschen</note></span> nach <span>Bentschen<note><term>Bentschen</term>Der Ort Namens Bentschen</note></span> über AltBentschen. Da hat es lange gedauert bis ich endlich in <span>Bentschen<note><term>Bentschen</term>Der Ort Namens Bentschen</note></span> ankam.";

        VocabularyEnricher enricher = new VocabularyEnricher();
        enricher.load("Glossary");

        String enrichedtext = enricher.enrich(text);
        System.out.println(text);
        System.out.println(enrichedtext);
        Assert.assertEquals(expected, enrichedtext);

        String enrichedTestimony = enricher.enrich(TESTIMONY);
        System.out.println(enrichedTestimony);
    }

    private List<FieldDefinition> prepareSchemaDefinitions() {
        List<FieldDefinition> result = new LinkedList<>();
        result.add(createFieldDefinition("Title", true, true));
        result.add(createFieldDefinition("Keywords", false, true));
        result.add(createFieldDefinition("Description", false, false));
        return result;
    }

    private FieldDefinition createFieldDefinition(String name, boolean mainValue, boolean unique) {
        FieldDefinition result = new FieldDefinition();
        result.setId(idCounter++);
        result.setSchemaId(vocabularySchema.getId());
        result.setName(name);
        result.setMainEntry(mainValue);
        result.setTitleField(mainValue);
        result.setUnique(unique);
        result.setTranslationDefinitions(Collections.emptySet());
        return result;
    }

    private ExtendedVocabularyRecord createRecord(String label, String keywords, String description) {
        ExtendedVocabularyRecord result = newEmptyRecord();
        result.getFieldForDefinitionName("Title").orElseThrow().setFieldValue(label);
        result.getFieldForDefinitionName("Keywords").orElseThrow().setFieldValue(keywords);
        result.getFieldForDefinitionName("Description").orElseThrow().setFieldValue(description);
        return result;
    }

    private ExtendedVocabularyRecord newEmptyRecord() {
        VocabularyRecord record = new VocabularyRecord();
        record.setVocabularyId(1L);
        record.setParentId(null);
        record.setMetadata(false);
        record.setFields(new HashSet<>());
        return new ExtendedVocabularyRecord(record);
    }
}
