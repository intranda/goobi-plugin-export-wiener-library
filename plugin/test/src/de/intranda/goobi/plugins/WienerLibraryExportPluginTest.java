package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Project;
import org.goobi.beans.Ruleset;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.ExportFileException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.helper.exceptions.UghHelperException;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;

public class WienerLibraryExportPluginTest {
    
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    File sampleProcessFolder = new File("test/resources/sample_4/");
    File rulesetFile = new File("test/resources/ruleset.xml");
    File exportFolder = new File("test/output");
    Process process;
    WienerLibraryExportPlugin plugin;

    @Before
    public void setUp() throws Exception {
                
        FileUtils.cleanDirectory(exportFolder);
        Prefs prefs = new Prefs();
        prefs.loadPrefs(rulesetFile.getAbsolutePath());
        
        Project project = Mockito.spy(new Project());
        Mockito.when(project.getFileFormatDmsExport()).thenReturn("Mets");
        Ruleset ruleset = Mockito.spy(new Ruleset());
        process = Mockito.spy(new Process());
        process.setRegelsatz(ruleset);
        process.setTitel("sample_process");
        process.setId(1);
        Mockito.doReturn(sampleProcessFolder.getAbsolutePath() + "/").when(process).getProcessDataDirectory();
//        Mockito.when(process.getProcessDataDirectory()).thenReturn(sampleProcessFolder.getAbsolutePath());
        Mockito.when(process.getProjekt()).thenReturn(project);
        Mockito.when(project.getDmsImportRootPath()).thenReturn(exportFolder.getAbsolutePath());
        Mockito.when(ruleset.getPreferences()).thenReturn(prefs);
        
//        process.getRegelsatz().setDatei("wienerlibrary.xml");
//        String meta = process.getMetadataFilePath();
//        process.getProjekt().setDmsImportRootPath(exportFolder.getAbsolutePath());
        
        plugin = new WienerLibraryExportPlugin();
        plugin.setExportImages(false);
        plugin.setExportFulltext(true);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testStartExport() throws DocStructHasNoTypeException, PreferencesException, WriteException, MetadataTypeNotAllowedException, ReadException, TypeNotAllowedForParentException, IOException, InterruptedException, ExportFileException, UghHelperException, SwapException, DAOException {
        Assert.assertTrue(plugin.startExport(process));
    }
    


public static void set(Map<String, String> newenv) throws Exception {
    Class[] classes = Collections.class.getDeclaredClasses();
    Map<String, String> env = System.getenv();
    for(Class cl : classes) {
        if("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            Object obj = field.get(env);
            Map<String, String> map = (Map<String, String>) obj;
            map.clear();
            map.putAll(newenv);
        }
    }
}



}
