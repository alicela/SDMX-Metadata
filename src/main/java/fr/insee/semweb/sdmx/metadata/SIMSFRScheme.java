package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public class SIMSFrScheme {

	private String name = null;
	private String source = null;
	private List<SIMSFrEntry> entries = null;

	private static Logger logger = LogManager.getLogger(SIMSFrScheme.class);

	public SIMSFrScheme() {
		this.entries = new ArrayList<SIMSFrEntry>();
	}

	/**
	 * Checks if the scheme contains an entry with a given notation.
	 * 
	 * @param notation The notation to look for in the scheme entries.
	 * @return <code>true</code> if the notation was found, <code>false</code> otherwise.
	 */
	public boolean containsNotation(String notation) {
		for (SIMSFrEntry entry : this.entries) {
			if (entry.getNotation().equals(notation)) return true;
		}
		return false;
	}

	/**
	 * Checks if the parent of every entry belongs to the scheme.
	 * 
	 * @return A string reporting the problems (empty string if hierarchy is complete).
	 */
	public String checkHierarchy() {

		StringBuilder report = new StringBuilder();

		for (SIMSFrEntry entry : this.entries) {
			String parentNotation = entry.getParentNotation();
			// The SIMSFr actually does not contain highest level concepts 'I' and 'S', hence the second test
			if ((parentNotation != null) && (parentNotation.length() > 1) && !this.containsNotation(parentNotation))
				report.append("No parent found for entry with notation " + entry.getNotation() + "\n");
		}
		return report.toString();
	}

	/**
	 * Gets the parent entry of the entry with a given notation.
	 * 
	 * @param notation The notation of the entry whose parent is sought.
	 * @return The parent entry (<code>SIMSEntry</code> object) or <code>null</code> if no parent is found.
	 */
	public SIMSFrEntry getParent(String notation) {

		String parentNotation = SIMSFrEntry.getParentNotation(notation);
		if (parentNotation!= null) {
			for (SIMSFrEntry entry : this.entries) {
				if (entry.getNotation().equals(parentNotation)) return entry;
			}
		}
		return null;
	}

	/**
	 * Gets the parent entry of a given entry.
	 * 
	 * @param notation The entry (<code>SIMSEntry</code> object) whose parent is sought.
	 * @return The parent entry (<code>SIMSEntry</code> object) or <code>null</code> if no parent is found.
	 */
	public SIMSFrEntry getParent(SIMSEntry entry) {

		if ((entry == null) || (entry.getNotation() == null)) return null;
		return getParent(entry.getNotation());
	}

	/**
	 * Reads the SIMSFr scheme from an Excel file.
	 * @param xlsxFile The Excel file specifying the SIMSFr.
	 * @return The SIMSFr as a <code>SIMSFrScheme</code> object, or <code>null</code> in case of problem.
	 */
	public static SIMSFrScheme readSIMSFrFromExcel(File xlsxFile) {
	
		Workbook simsWorkbook = null;
		Sheet simsFrSheet = null;
		try {
			logger.info("Reading SIMSFr scheme from Excel file " + xlsxFile.getAbsolutePath());
			simsWorkbook = WorkbookFactory.create(xlsxFile);
			simsFrSheet = simsWorkbook.getSheetAt(1); // SIMSFr is on the second sheet
		} catch (Exception e) {
			logger.fatal("Error while opening Excel file - " + e.getMessage());
			return null;
		}
	
		SIMSFrScheme simsFr = new SIMSFrScheme();
		simsFr.setName(Configuration.simsConceptSchemeName(false, false));
		simsFr.setSource(xlsxFile.getPath());
	
		Iterator<Row> rows = simsFrSheet.rowIterator();
		rows.next(); rows.next(); // Skip two title lines
		while (rows.hasNext()) {
			Row row = rows.next();
			SIMSFrEntry simsFrEntry = SIMSFrEntry.readFromRow(row);
			if (simsFrEntry == null) continue;
			simsFr.addEntry(simsFrEntry);
			logger.debug("Entry read: " + simsFrEntry);
		}
		try { simsWorkbook.close(); } catch (IOException ignored) { }
		logger.info("Finished reading SIMSFr scheme, number of entries in the scheme: " + simsFr.getEntries().size());
	
		return simsFr;
	}

	/**
	 * Returns a string representation of the SIMSFr scheme.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Scheme ").append(name).append(" (source: '").append(source).append("')\n");
		for (SIMSFrEntry entry : this.getEntries()) {
			builder.append("\nProperty ").append(entry.getNotation()).append(" (").append(entry.getCode()).append("): ");
			builder.append(entry.getName()).append(" (").append(entry.getFrenchName()).append(")\n");
			builder.append("Associated concept: ").append(Configuration.simsConceptURI(entry)).append("\n");
			if (entry.isDirect()) {
				// Direct property, not part of metadata report
				if (entry.isPresentational()) builder.append("No associated RDF property, identity attribute is presentational\n");
				else builder.append("Direct RDF property: ").append(Configuration.propertyMappings.get(entry.getCode()).getURI()).append("\n");
			} else {
				if (entry.isOriginal()) { // SIMS entry
					if (!entry.isAddedOrModified()) { // entry not modified in SIMSFr
						builder.append("SIMS original attribute, unchanged in SIMSFr\n");
						builder.append("Associated metadata attribute specification: ").append(Configuration.simsAttributeSpecificationURI(entry, true)).append("\n");
						builder.append("Associated metadata attribute property: ").append(Configuration.simsAttributePropertyURI(entry, true));
						builder.append(" (range: ").append(SIMSModelMaker.getRange(entry, true)).append(")\n");
					} else { // entry modified in SIMSFr
						builder.append("SIMS original attribute, modified in SIMSFr\n");
						builder.append("Associated metadata attribute specification (SIMS): ").append(Configuration.simsAttributeSpecificationURI(entry, true)).append("\n");
						builder.append("Associated metadata attribute property (SIMS): ").append(Configuration.simsAttributePropertyURI(entry, true));
						builder.append(" (range: ").append(SIMSModelMaker.getRange(entry, true)).append(")\n");
						builder.append("Associated metadata attribute specification (SIMSFr): ").append(Configuration.simsAttributeSpecificationURI(entry, false)).append("\n");
						builder.append("Associated metadata attribute property (SIMSFr): ").append(Configuration.simsAttributePropertyURI(entry, false));
						builder.append(" (range: ").append(SIMSModelMaker.getRange(entry, false)).append(")\n");
					}
				} else { // Property added in SIMSFr
					builder.append("Attribute added in SIMSFr\n");
					builder.append("Associated metadata attribute specification: ").append(Configuration.simsAttributeSpecificationURI(entry, false)).append("\n");
					builder.append("Associated metadata attribute property: ").append(Configuration.simsAttributePropertyURI(entry, false));
					builder.append(" (range: ").append(SIMSModelMaker.getRange(entry, false)).append(")\n");					
				}
			}
		}
		return builder.toString();
	}

	// Getters and setters

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public List<SIMSFrEntry> getEntries() {
		return entries;
	}

	public void setEntries(List<SIMSFrEntry> entries) {
		this.entries = entries;
	}

	public void addEntry(SIMSFrEntry entry) {
		this.entries.add(entry);
	}
}
