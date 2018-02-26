package de.intranda.goobi.plugins;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.goobi.beans.Process;
import org.goobi.beans.ProjectFileGroup;
import org.goobi.beans.User;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.PluginLoader;
import org.goobi.production.plugin.interfaces.IExportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.output.XMLOutputter;

import de.intranda.goobi.ocr.tei.TEIBuilder;
import de.intranda.goobi.plugins.vocabulary.Field;
import de.intranda.goobi.plugins.vocabulary.Record;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.export.download.ExportMets;
import de.sub.goobi.helper.FilesystemHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.ExportFileException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.helper.exceptions.UghHelperException;
import de.sub.goobi.metadaten.MetadatenHelper;
import lombok.Data;
import lombok.extern.log4j.Log4j;
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
@Log4j
@PluginImplementation
public class WienerLibraryExportPlugin extends ExportMets implements IExportPlugin, IPlugin {
	private static final String PLUGIN_NAME = "intranda_export_wienerlibrary";
	
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
	public boolean startExport(Process process, String destination)
			throws IOException, InterruptedException, DocStructHasNoTypeException, PreferencesException, WriteException,
			MetadataTypeNotAllowedException, ExportFileException, UghHelperException, ReadException, SwapException,
			DAOException, TypeNotAllowedForParentException {
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
	public boolean startExport(Process process) throws IOException, InterruptedException, DocStructHasNoTypeException,
			PreferencesException, WriteException, MetadataTypeNotAllowedException, ExportFileException,
			UghHelperException, ReadException, SwapException, DAOException, TypeNotAllowedForParentException {
		String imageDirectorySuffix = "_tif";

		myPrefs = process.getRegelsatz().getPreferences();
		String atsPpnBand = process.getTitel();

		Fileformat gdzfile;
		ExportFileformat newfile = MetadatenHelper
				.getExportFileformatByName(process.getProjekt().getFileFormatDmsExport(), process.getRegelsatz());
		File exportfolder = new File(ConfigPlugins.getPluginConfig(this).getString("exportFolder"));

		try {
			gdzfile = process.readMetadataFile();
			newfile.setDigitalDocument(gdzfile.getDigitalDocument());
			gdzfile = newfile;

		} catch (Exception e) {
			Helper.setFehlerMeldung(Helper.getTranslation("exportError") + process.getTitel(), e);
			logger.error("Export abgebrochen, xml-LeseFehler", e);
			return false;
		}

		DocStruct logical = gdzfile.getDigitalDocument().getLogicalDocStruct();
		if (logical.getType().isAnchor()) {
			logical = logical.getAllChildren().get(0);
		}
		// run through all docstructs
		if (logical.getAllChildren() != null && !logical.getAllChildren().isEmpty()) {
			for (DocStruct ds : logical.getAllChildren()) {
				log.debug("docstruct is " + ds.getType().getName());
				for (Metadata md : ds.getAllMetadata()) {
					System.out.println("-- " + md.getType().getName());
					if (md.getType().getName().equals("Transcription_de")
							|| md.getType().getName().equals("Transcription_en")
							|| md.getType().getName().equals("Transcription_fr")
							|| md.getType().getName().equals("Transcription_nl")
							|| md.getType().getName().equals("Transcription_al")
							|| md.getType().getName().equals("Transcription_it")
							|| md.getType().getName().equals("Translation_de")
							|| md.getType().getName().equals("Translation_en")
							|| md.getType().getName().equals("Translation_fr")
							|| md.getType().getName().equals("Translation_nl")
							|| md.getType().getName().equals("Translation_al")
							|| md.getType().getName().equals("Translation_it")) {
						String value = md.getValue();
						String newValue = enrichMetadataWithVocabulary(value);
						md.setValue(newValue);
					}
				}
			}
		}

		// start export of images and fulltext
		try {
			if (this.exportWithImages) {
				imageDownload(process, exportfolder, atsPpnBand, imageDirectorySuffix);
				fulltextDownload(process, exportfolder, atsPpnBand);
			} else if (this.exportFulltext) {
				fulltextDownload(process, exportfolder, atsPpnBand);
			}
		} catch (Exception e) {
			Helper.setFehlerMeldung("Export canceled, Process: " + process.getTitel(), e);
			return false;
		}

		// now export the Mets file
		writeOcrFiles(process, exportfolder + File.separator + atsPpnBand, atsPpnBand, logical);
		writeMetsFile(process, exportfolder + File.separator + atsPpnBand + ".xml", gdzfile, false);
		return true;
	}

	private void writeOcrFiles(Process process, String exportFolderPath, String title, DocStruct logical) {
		Path exportFolder = Paths.get(exportFolderPath);
		
		Pattern fulltextMetadataPattern = Pattern.compile(FULLTEXT_METADATA_REGEX);
		
		if (logical.getAllChildren() != null && !logical.getAllChildren().isEmpty()) {
			Map<String, List<String>> texts = new HashMap<>();
			for (DocStruct ds : logical.getAllChildren()) {
				log.debug("docstruct is " + ds.getType().getName());
				log.debug("docstruct id is " + ds.getIdentifier());
				for (Metadata md : ds.getAllMetadata()) {
					Matcher matcher = fulltextMetadataPattern.matcher(md.getType().getName());
					if(matcher.matches()) {
						String language = matcher.group(1);
						List<String> list = texts.get(language);
						if(list == null) {
							list = new ArrayList<>();
							texts.put(language, list);
						}
						list.add(md.getValue());
					}
				}
			}
			for (String language : texts.keySet()) {
				String filename = title + "_tei_" + language + ".xml";
				try {
					writeTEIFile(exportFolder.resolve(filename), texts.get(language), language);
				} catch (JDOMException | IOException e) {
					log.error("Error writing tei file '" + exportFolder.resolve(filename) + "'", e);
				}
				
			}
		}

	}

	private void writeTEIFile(Path filepath, List<String> list, String language) throws JDOMException, IOException {
		 TEIBuilder builder = new TEIBuilder().setLanguage(language);
		 for (String text : list) {
			builder.addTextSegment(text);
		}
		 XMLOutputter writer = new XMLOutputter();
		 writer.output(builder.build(), new FileWriter(filepath.toFile()));
	}


    
	/**
	 * Start the export the fulltext results and the source files into the target
	 * directory
	 * 
	 * @param process
	 * @param exportfolder
	 * @param atsPpnBand
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws SwapException
	 * @throws DAOException
	 */
	public void fulltextDownload(Process process, File exportfolder, String atsPpnBand)
			throws IOException, InterruptedException, SwapException, DAOException {

		// download sources
		Path sources = Paths.get(process.getSourceDirectory());
		if (Files.exists(sources) && !NIOFileUtils.list(process.getSourceDirectory()).isEmpty()) {
			Path destination = Paths.get(exportfolder.toString(), atsPpnBand + "_src");
			if (!Files.exists(destination)) {
				Files.createDirectories(destination);
			}
			List<Path> dateien = NIOFileUtils.listFiles(process.getSourceDirectory());
			for (Path dir : dateien) {
				Path meinZiel = Paths.get(destination.toString(), dir.getFileName().toString());
				Files.copy(dir, meinZiel, NIOFileUtils.STANDARD_COPY_OPTIONS);
			}
		}

		Path ocr = Paths.get(process.getOcrDirectory());
		if (Files.exists(ocr)) {

			List<Path> folder = NIOFileUtils.listFiles(process.getOcrDirectory());
			for (Path dir : folder) {
				if (Files.isDirectory(dir) && !NIOFileUtils.list(dir.toString()).isEmpty()) {
					String suffix = dir.getFileName().toString()
							.substring(dir.getFileName().toString().lastIndexOf("_"));
					Path destination = Paths.get(exportfolder.toString(), atsPpnBand + suffix);
					if (!Files.exists(destination)) {
						Files.createDirectories(destination);
					}
					List<Path> files = NIOFileUtils.listFiles(dir.toString());
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
	public void imageDownload(Process process, File exportfolder, String atsPpnBand, final String ordnerEndung)
			throws IOException, InterruptedException, SwapException, DAOException {

		File tifOrdner = new File(process.getImagesTifDirectory(true));
		File zielTif = new File(exportfolder + File.separator + atsPpnBand + ordnerEndung);
		if (tifOrdner.exists() && tifOrdner.list().length > 0) {

			if (process.getProjekt().isUseDmsImport()) {
				if (!zielTif.exists()) {
					zielTif.mkdir();
				}
			} else {
				User myBenutzer = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
				try {
					FilesystemHelper.createDirectoryForUser(zielTif.getAbsolutePath(), myBenutzer.getLogin());
				} catch (Exception e) {
					Helper.setFehlerMeldung("Export canceled, error", "could not create destination directory");
					logger.error("could not create destination directory", e);
				}
			}

			/* jetzt den eigentlichen Kopiervorgang */
			List<Path> files = NIOFileUtils.listFiles(process.getImagesTifDirectory(true), NIOFileUtils.DATA_FILTER);
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
						if (folder != null && java.nio.file.Files.exists(folder)
								&& !NIOFileUtils.list(folder.toString()).isEmpty()) {
							List<Path> files = NIOFileUtils.listFiles(folder.toString());
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
			List<Path> subdir = NIOFileUtils.listFiles(process.getExportDirectory());
			for (Path dir : subdir) {
				if (Files.isDirectory(dir) && !NIOFileUtils.list(dir.toString()).isEmpty()) {
					if (!dir.getFileName().toString().matches(".+\\.\\d+")) {
						String suffix = dir.getFileName().toString()
								.substring(dir.getFileName().toString().lastIndexOf("_"));
						Path destination = Paths.get(exportfolder.toString(), atsPpnBand + suffix);
						if (!Files.exists(destination)) {
							Files.createDirectories(destination);
						}
						List<Path> files = NIOFileUtils.listFiles(dir.toString());
						for (Path file : files) {
							Path target = Paths.get(destination.toString(), file.getFileName().toString());
							Files.copy(file, target, NIOFileUtils.STANDARD_COPY_OPTIONS);
						}
					}
				}
			}
		}
	}

	/**
	 * Open the wiener library vocabulary and find all terms to enrich these with
	 * explanation as html popup
	 * 
	 * @param value
	 * @return
	 */
	private String enrichMetadataWithVocabulary(String value) {
		try {
			VocabularyPlugin vocabularyPlugin = (VocabularyPlugin) PluginLoader
					.getPluginByTitle(PluginType.Administration, "intranda_admin_vocabulary");
			vocabularyPlugin.getVm().loadVocabulary("Wiener Library Glossary");
			String newvalue = value;
			for (Record record : vocabularyPlugin.getVm().getVocabulary().getRecords()) {
				System.out.println(record.getTitle());
				List<String> keywords = null;
				String description = "";
				// first get the right fields from the record
				for (Field field : record.getFields()) {
					if (field.getLabel().equals("Keywords")) {
						keywords = Arrays.asList(field.getValue().split("\\r?\\n"));
					}
					if (field.getLabel().equals("Description")) {
						description = field.getValue();
					}
				}
				// now run through all words of string and extend keywords with description
				StringBuilder sb = new StringBuilder();
				for (String word : newvalue.split("\\s+")) {
					if (keywords.contains(word)) {
						sb.append(" <span title=\"" + description + "\"" + word + "</span>");
					} else {
						sb.append(" " + word.trim());
					}
				}
				newvalue = sb.toString();
			}
			return newvalue;
		} catch (Exception e) {
			logger.error("Can't load admin vocabulary plugin", e);
			return value;
		}
	}
}
