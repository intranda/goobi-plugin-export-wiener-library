package de.intranda.goobi.plugins;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.goobi.beans.Process;
import org.goobi.beans.ProjectFileGroup;
import org.goobi.beans.User;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IExportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import de.intranda.goobi.ocr.tei.TEIBuilder;
import de.intranda.goobi.utils.VocabularyEnricher;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.export.download.ExportMets;
import de.sub.goobi.helper.FilesystemHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.ExportFileException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.helper.exceptions.UghHelperException;
import de.sub.goobi.metadaten.MetadatenHelper;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.ExportFileformat;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;

@Data
@Log4j2
@PluginImplementation
public class WienerLibraryExportPlugin extends ExportMets implements IExportPlugin, IPlugin {
    private static final String PLUGIN_NAME = "intranda_export_wienerlibrary";
    private static final Namespace metsNamespace = Namespace.getNamespace("mets", "http://www.loc.gov/METS/");
    private static final Namespace xlink = Namespace.getNamespace("xlink", "http://www.w3.org/1999/xlink");
    private static final String FULLTEXT_METADATA_REGEX = "(?:Transcription|Translation)_(\\w{1,3})";

    private boolean exportWithImages = true;
    private boolean exportFulltext = true;

    @Override
    public PluginType getType() {
        return PluginType.Export;
    }

    @Override
    public String getTitle() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean startExport(Process process, String destination) throws IOException, InterruptedException, DocStructHasNoTypeException,
    PreferencesException, WriteException, MetadataTypeNotAllowedException, ExportFileException, UghHelperException, ReadException,
    SwapException, DAOException, TypeNotAllowedForParentException {
        return startExport(process);
    }

    @Override
    public void setExportFulltext(boolean exportFulltext) {
        this.exportFulltext = exportFulltext;
    }

    @Override
    public void setExportImages(boolean exportImages) {
        exportWithImages = exportImages;
    }

    /**
     * Start the entire export of images, fulltext and the metadata
     */
    @Override
    public boolean startExport(Process process) throws IOException, InterruptedException, DocStructHasNoTypeException, PreferencesException,
    WriteException, MetadataTypeNotAllowedException, ExportFileException, UghHelperException, ReadException, SwapException, DAOException,
    TypeNotAllowedForParentException {
        String imageDirectorySuffix = "_tif";

        myPrefs = process.getRegelsatz().getPreferences();
        String atsPpnBand = process.getTitel();

        Fileformat gdzfile;

        ExportFileformat newfile = MetadatenHelper.getExportFileformatByName(process.getProjekt().getFileFormatDmsExport(), process.getRegelsatz());

        try {
            gdzfile = process.readMetadataFile();
            newfile.setDigitalDocument(gdzfile.getDigitalDocument());
            gdzfile = newfile;

        } catch (Exception e) {
            Helper.setFehlerMeldung(Helper.getTranslation("exportError") + process.getTitel(), e);
            log.error("Export abgebrochen, xml-LeseFehler", e);
            return false;
        }

        VariableReplacer replacer = new VariableReplacer(gdzfile.getDigitalDocument(), this.myPrefs, process, null);
        String path = replacer.replace(process.getProjekt().getDmsImportRootPath());
        File exportfolder = new File(path);
        //      File exportfolder = new File(ConfigPlugins.getPluginConfig(PLUGIN_NAME).getString("exportFolder"));

        DocStruct logical = gdzfile.getDigitalDocument().getLogicalDocStruct();
        if (logical.getType().isAnchor()) {
            logical = logical.getAllChildren().get(0);
        }
        // run through all docstructs
        List<DocStruct> dsList = new ArrayList<>();
        dsList.add(logical);
        if (logical.getAllChildren() != null) {
            dsList.addAll(logical.getAllChildren());
        }
        for (DocStruct ds : dsList) {
            boolean foundEnglishTranscription = false;
            if (ds.getAllMetadata() != null) {

                for (Metadata md : ds.getAllMetadata()) {
                    if (md.getType().getName().equals("Transcription_en") || md.getType().getName().equals("Translation_en")) {
                        //                        if (md.getType().getName().equals("Transcription_de") || md.getType().getName().equals("Transcription_en")
                        //                                || md.getType().getName().equals("Transcription_fr") || md.getType().getName().equals("Transcription_nl")
                        //                                || md.getType().getName().equals("Transcription_al") || md.getType().getName().equals("Transcription_it")
                        //                                || md.getType().getName().equals("Translation_de") || md.getType().getName().equals("Translation_en")
                        //                                || md.getType().getName().equals("Translation_fr") || md.getType().getName().equals("Translation_nl")
                        //                                || md.getType().getName().equals("Translation_al") || md.getType().getName().equals("Translation_it")) {
                        String value = md.getValue();
                        String newValue = enrichMetadataWithVocabulary(value);
                        md.setValue(newValue);
                        foundEnglishTranscription = true;
                    }
                }
            }
            if ("Testimony".equals(ds.getType().getName())) {
                try {
                    Metadata engl = new Metadata(myPrefs.getMetadataTypeByName("_hasEnglishTranslation"));
                    if (foundEnglishTranscription) {
                        engl.setValue("true");
                    } else {
                        engl.setValue("false");
                    }
                    logical.getAllMetadata().add(engl);
                } catch (Exception e) {
                    log.error(e);
                }
            }

        }

        // start export of images and fulltext
        Path tempFolder = Files.createTempDirectory(atsPpnBand + "__");
        try {
            if (this.exportWithImages) {
                imageDownload(process, tempFolder.toFile(), atsPpnBand, imageDirectorySuffix);
                fulltextDownload(process, tempFolder.toFile(), atsPpnBand);
            } else if (this.exportFulltext) {
                fulltextDownload(process, tempFolder.toFile(), atsPpnBand);
            }
            writeOcrFiles(process, tempFolder.toString(), atsPpnBand, logical);
            removeOcrMetadata(logical);
        } catch (Exception e) {
            log.error(e.toString(), e);
            Helper.setFehlerMeldung("Export canceled, Process: " + process.getTitel(), e);
            return false;
        }
        moveContent(tempFolder.toFile(), exportfolder);

        // now export the Mets file
        File exportFile = new File(exportfolder + File.separator + atsPpnBand + ".xml");
        File tempFile = Files.createTempFile(atsPpnBand + "__", ".xml").toFile();
        writeMetsFile(process, tempFile.getAbsolutePath(), gdzfile, false);
        if (tempFile.exists()) {
            log.debug("Temporary export mets file written: " + tempFile.getAbsolutePath());
        } else {
            Helper.setFehlerMeldung("Export canceled, Process: " + process.getTitel(), "Failed to write temporary export mets file");
            return false;
        }
        addFileGroup(tempFile.getAbsolutePath(), getTEIFiles(exportfolder + File.separator + atsPpnBand + "_tei"), getFileGroupName(),
                getFileGroupFolder(), getFileGroupMimeType());
        log.debug("Moving temporary file " + tempFile + " to export file location " + exportFile);
        FileUtils.moveFile(tempFile, exportFile);
        return true;
    }

    /**
     * @param exportfolder
     * @param tempFolder
     * @throws IOException
     */
    public void moveContent(File source, File target) throws IOException {
        File[] tempDirs = source.listFiles(new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });
        for (File file : tempDirs) {
            FileUtils.moveDirectory(file, new File(target, file.getName()));
        }
    }

    private File[] getTEIFiles(String teiFolder) {
        try {
            return new File(teiFolder).listFiles(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".xml");
                }
            });
        } catch (Throwable e) {
            log.error("No TEI files for process");
            return new File[0];
        }
    }

    private boolean addFileGroup(String exportFile, File[] teiFiles, String name, String path, String mimeType) {

        SAXBuilder parser = new SAXBuilder();
        Document metsDoc = null;
        try {
            metsDoc = parser.build(exportFile);
        } catch (JDOMException | IOException e) {
            Helper.setFehlerMeldung("error while parsing amd file");
            log.error("error while parsing amd file", e);
            return false;
        }
        Element logFileGroup = new Element("fileGrp", metsNamespace);
        Element fileSec = metsDoc.getRootElement().getChild("fileSec", metsNamespace);
        if (fileSec == null) {
            fileSec = new Element("fileSec", metsNamespace);
            metsDoc.getRootElement().addContent(fileSec);
        }
        fileSec.addContent(logFileGroup);

        logFileGroup.setAttribute("USE", name);

        int index = 0;
        for (File teiFile : teiFiles) {

            Element file = new Element("file", metsNamespace);
            file.setAttribute("MIMETYPE", mimeType);
            file.setAttribute("ID", "FILE_" + new DecimalFormat("0000").format(index) + "_" + name);
            logFileGroup.addContent(file);

            Element flocat = new Element("FLocat", metsNamespace);
            flocat.setAttribute("href", path + teiFile.getName(), xlink);
            flocat.setAttribute("LOCTYPE", "URL");
            file.addContent(flocat);

            index++;
        }

        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());

        try {
            FileOutputStream output = new FileOutputStream(exportFile);
            outputter.output(metsDoc, output);
            return true;
        } catch (IOException e) {
            Helper.setFehlerMeldung("error while writing mets file");
            log.error("error while writing mets file", e);
            return false;
        }

    }

    private String getFileGroupFolder() {
        return ConfigPlugins.getPluginConfig(getTitle()).getString("fullText.fileGroup.location", "file:///opt/digiverso/viewer/tei/");
    }

    private String getFileGroupName() {
        return ConfigPlugins.getPluginConfig(getTitle()).getString("fullText.fileGroup.name", "TEI");
    }

    private String getFileGroupMimeType() {
        return ConfigPlugins.getPluginConfig(getTitle()).getString("fullText.fileGroup.mimeType", "text/xml");
    }

    private void removeOcrMetadata(DocStruct logical) {
        Pattern fulltextMetadataPattern = Pattern.compile(FULLTEXT_METADATA_REGEX);

        List<DocStruct> dsList = new ArrayList<>();
        dsList.add(logical);
        if (logical.getAllChildren() != null) {
            dsList.addAll(logical.getAllChildren());
        }
        for (DocStruct ds : dsList) {
            log.debug("docstruct is " + ds.getType().getName());
            log.debug("docstruct id is " + ds.getIdentifier());
            if (ds.getAllMetadata() != null) {
                List<Metadata> mdList = new ArrayList<>(ds.getAllMetadata());
                for (Metadata md : mdList) {
                    Matcher matcher = fulltextMetadataPattern.matcher(md.getType().getName());
                    if (matcher.matches()) {
                        ds.removeMetadata(md, true);
                    }
                }
            }
        }

    }

    private void writeOcrFiles(Process process, String exportFolderPath, String title, DocStruct logical) throws WriteException, IOException {
        Path exportFolder = Paths.get(exportFolderPath, title + "_tei");
        if (!Files.exists(exportFolder)) {
            Files.createDirectory(exportFolder);
        }
        Pattern fulltextMetadataPattern = Pattern.compile(FULLTEXT_METADATA_REGEX);

        Map<String, List<String>> texts = new HashMap<>();
        List<DocStruct> dsList = new ArrayList<>();
        dsList.add(logical);
        if (logical.getAllChildren() != null) {
            dsList.addAll(logical.getAllChildren());
        }
        for (DocStruct ds : dsList) {
            log.debug("docstruct is " + ds.getType().getName());
            log.debug("docstruct id is " + ds.getIdentifier());
            if (ds.getAllMetadata() != null) {
                for (Metadata md : ds.getAllMetadata()) {
                    Matcher matcher = fulltextMetadataPattern.matcher(md.getType().getName());
                    if (matcher.matches()) {
                        String language = matcher.group(1);
                        List<String> list = texts.get(language);
                        if (list == null) {
                            list = new ArrayList<>();
                            texts.put(language, list);
                        }
                        list.add(md.getValue());
                    }
                }
            }
        }
        for (String language : texts.keySet()) {
            String filename = title + "_tei_" + language + ".xml";
            try {
                writeTEIFile(exportFolder.resolve(filename), texts.get(language), language);
            } catch (JDOMException e) {
                FileUtils.deleteDirectory(exportFolder.toFile());
                throw new WriteException("Error writing tei file '" + exportFolder.resolve(filename) + "'", e);
                //					log.error("Error writing tei file '" + exportFolder.resolve(filename) + "'", e);
            } catch (IOException e) {
                FileUtils.deleteDirectory(exportFolder.toFile());
                throw e;

            }

        }

    }

    private void writeTEIFile(Path filepath, List<String> list, String language) throws JDOMException, IOException {
        TEIBuilder builder = new TEIBuilder().setLanguage(language);
        for (String text : list) {
            builder.addTextSegment(text);
        }
        XMLOutputter writer = new XMLOutputter();
        log.debug("Write tei file to " + filepath);
        writer.output(builder.build(), new FileWriter(filepath.toFile()));
        log.debug(filepath + " written");
    }

    /**
     * Start the export the fulltext results and the source files into the target directory
     * 
     * @param process
     * @param exportfolder
     * @param atsPpnBand
     * @throws IOException
     * @throws InterruptedException
     * @throws SwapException
     * @throws DAOException
     */
    public void fulltextDownload(Process process, File exportfolder, String atsPpnBand) throws IOException, InterruptedException, SwapException,
    DAOException {

        // download sources
        Path sources = Paths.get(process.getSourceDirectory());
        if (Files.exists(sources) && !StorageProvider.getInstance().list(process.getSourceDirectory()).isEmpty()) {
            Path destination = Paths.get(exportfolder.toString(), atsPpnBand + "_src");
            if (!Files.exists(destination)) {
                Files.createDirectories(destination);
            }
            List<Path> dateien = StorageProvider.getInstance().listFiles(process.getSourceDirectory());
            for (Path dir : dateien) {
                Path meinZiel = Paths.get(destination.toString(), dir.getFileName().toString());
                Files.copy(dir, meinZiel, NIOFileUtils.STANDARD_COPY_OPTIONS);
            }
        }

        Path ocr = Paths.get(process.getOcrDirectory());
        if (Files.exists(ocr)) {

            List<Path> folder = StorageProvider.getInstance().listFiles(process.getOcrDirectory());
            for (Path dir : folder) {
                if (Files.isDirectory(dir) && !StorageProvider.getInstance().list(dir.toString()).isEmpty()) {
                    String suffix = dir.getFileName().toString().substring(dir.getFileName().toString().lastIndexOf("_"));
                    Path destination = Paths.get(exportfolder.toString(), atsPpnBand + suffix);
                    if (!Files.exists(destination)) {
                        Files.createDirectories(destination);
                    }
                    List<Path> files = StorageProvider.getInstance().listFiles(dir.toString());
                    for (Path file : files) {
                        Path target = Paths.get(destination.toString(), file.getFileName().toString());
                        Files.copy(file, target, NIOFileUtils.STANDARD_COPY_OPTIONS);
                    }
                }
            }
        }
    }

    /**
     * Start the export of the images into the target directory
     * 
     * @param process
     * @param exportfolder
     * @param atsPpnBand
     * @param ordnerEndung
     * @throws IOException
     * @throws InterruptedException
     * @throws SwapException
     * @throws DAOException
     */
    public void imageDownload(Process process, File exportfolder, String atsPpnBand, final String ordnerEndung) throws IOException,
    InterruptedException, SwapException, DAOException {

        File tifOrdner = new File(process.getImagesTifDirectory(true));
        File zielTif = new File(exportfolder + File.separator + atsPpnBand + ordnerEndung);

        try {
            if (tifOrdner.exists() && tifOrdner.list().length > 0) {

                if (process.getProjekt().isUseDmsImport()) {
                    if (!zielTif.exists()) {
                        zielTif.mkdir();
                    }
                } else {
                    User myBenutzer = Helper.getCurrentUser();
                    try {
                        FilesystemHelper.createDirectoryForUser(zielTif.getAbsolutePath(), myBenutzer.getLogin());
                    } catch (Exception e) {
                        Helper.setFehlerMeldung("Export canceled, error", "could not create destination directory");
                        log.error("could not create destination directory", e);
                    }
                }

                /* jetzt den eigentlichen Kopiervorgang */
                List<Path> files = StorageProvider.getInstance().listFiles(process.getImagesTifDirectory(true), NIOFileUtils.DATA_FILTER);
                for (Path file : files) {
                    Path target = Paths.get(zielTif.toString(), file.getFileName().toString());
                    Files.copy(file, target, NIOFileUtils.STANDARD_COPY_OPTIONS);
                }
            }

            if (ConfigurationHelper.getInstance().isExportFilesFromOptionalMetsFileGroups()) {
                List<ProjectFileGroup> myFilegroups = process.getProjekt().getFilegroups();
                if (myFilegroups != null && myFilegroups.size() > 0) {
                    for (ProjectFileGroup pfg : myFilegroups) {
                        // check if source files exists
                        if (pfg.getFolder() != null && pfg.getFolder().length() > 0) {
                            Path folder = Paths.get(process.getMethodFromName(pfg.getFolder()));
                            if (folder != null && java.nio.file.Files.exists(folder) && !StorageProvider.getInstance().list(folder.toString())
                                    .isEmpty()) {
                                List<Path> files = StorageProvider.getInstance().listFiles(folder.toString());
                                for (Path file : files) {
                                    Path target = Paths.get(zielTif.toString(), file.getFileName().toString());

                                    Files.copy(file, target, NIOFileUtils.STANDARD_COPY_OPTIONS);
                                }
                            }
                        }
                    }
                }
            }
            Path exportFolder = Paths.get(process.getExportDirectory());
            if (Files.exists(exportFolder) && Files.isDirectory(exportFolder)) {
                List<Path> subdir = StorageProvider.getInstance().listFiles(process.getExportDirectory());
                for (Path dir : subdir) {
                    if (Files.isDirectory(dir) && !StorageProvider.getInstance().list(dir.toString()).isEmpty()) {
                        if (!dir.getFileName().toString().matches(".+\\.\\d+")) {
                            String suffix = dir.getFileName().toString().substring(dir.getFileName().toString().lastIndexOf("_"));
                            Path destination = Paths.get(exportfolder.toString(), atsPpnBand + suffix);
                            if (!Files.exists(destination)) {
                                Files.createDirectories(destination);
                            }
                            List<Path> files = StorageProvider.getInstance().listFiles(dir.toString());
                            for (Path file : files) {
                                Path target = Paths.get(destination.toString(), file.getFileName().toString());
                                Files.copy(file, target, NIOFileUtils.STANDARD_COPY_OPTIONS);
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            FileUtils.deleteDirectory(zielTif);
            throw e;
        }
    }

    /**
     * Open the wiener library vocabulary and find all terms to enrich these with explanation as html popup
     * 
     * @param value
     * @return
     */
    private String enrichMetadataWithVocabulary(String value) {


        String vocabulary = "Wiener Library Glossary";

        VocabularyEnricher enricher = new VocabularyEnricher(vocabulary);

        return enricher.enrich(value);

    }

}
