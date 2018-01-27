package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public class SIMSFRScheme {

	private String name = null;
	private String source = null;
	private List<SIMSFREntry> entries = null;

	public SIMSFRScheme() {
		this.entries = new ArrayList<SIMSFREntry>();
	}

	/**
	 * Checks if the scheme contains an entry with a given notation.
	 * 
	 * @param notation The notation to look for in the scheme entries.
	 * @return <code>true</code> if the notation was found, <code>false</code> otherwise.
	 */
	public boolean containsNotation(String notation) {
		for (SIMSFREntry entry : this.entries) {
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

		for (SIMSFREntry entry : this.entries) {
			String parentNotation = entry.getParentNotation();
			// The SIMSFR actually does not contain highest level concepts 'I' and 'S', hence the second test
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
	public SIMSFREntry getParent(String notation) {

		String parentNotation = SIMSFREntry.getParentNotation(notation);
		if (parentNotation!= null) {
			for (SIMSFREntry entry : this.entries) {
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
	public SIMSFREntry getParent(SIMSEntry entry) {

		if ((entry == null) || (entry.getNotation() == null)) return null;
		return getParent(entry.getNotation());
	}

	/**
	 * Reads the SIMSFR scheme from an Excel file.
	 * @param xlsxFile The Excel file specifying the SIMSFR.
	 * @return The SIMSFR as a <code>SIMSFRScheme</code> object, or <code>null</code> in case of problem.
	 */
	public static SIMSFRScheme readSIMSFRFromExcel(File xlsxFile) {
	
		Workbook simsWorkbook = null;
		Sheet simsFRSheet = null;
		try {
			simsWorkbook = WorkbookFactory.create(xlsxFile);
			simsFRSheet = simsWorkbook.getSheetAt(1); // SIMSFR is on the second sheet
		} catch (Exception e) {
			SIMSModelMaker.logger.fatal("Error while opening Excel file - " + e.getMessage());
			return null;
		}
	
		SIMSFRScheme simsFR = new SIMSFRScheme();
		simsFR.setName(Configuration.simsConceptSchemeName(false, false));
		simsFR.setSource(xlsxFile.getPath());
	
		Iterator<Row> rows = simsFRSheet.rowIterator();
		rows.next(); rows.next(); // Skip two title lines
		while (rows.hasNext()) {
			Row row = rows.next();
			SIMSFREntry simsFREntry = SIMSFREntry.readFromRow(row);
			if (simsFREntry == null) continue;
	
			simsFR.addEntry(simsFREntry);
			SIMSModelMaker.logger.debug("Entry read: " + simsFREntry);
		}
		try { simsWorkbook.close(); } catch (IOException ignored) { }
	
		return simsFR;
	}

	/**
	 * Returns a string representation of the SIMSFR entry.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Scheme ").append(name).append(" (source: '").append(source).append("')\n");
		for (SIMSFREntry entry : this.getEntries()) {
			builder.append("\nProperty ").append(entry.getNotation()).append(" (").append(entry.getCode()).append("): ");
			builder.append(entry.getName()).append(" (").append(entry.getFrenchName()).append(")\n");
			builder.append("Associated concept: ").append(Configuration.simsConceptURI(entry)).append("\n");
			if (entry.getNotation().equals("1.1") || entry.getNotation().startsWith("I.1.")) {
				// Direct property, not part of metadata report
				builder.append("Direct RDF property: ").append(Configuration.propertyMappings.get(entry.getCode()).getURI()).append("\n");
			} else {
				if (entry.isOriginal()) { // SIMS entry
					if (!entry.isAddedOrModified()) { // entry not modified in SIMSFR
						builder.append("SIMS original attribute, unchanged in SIMSFR\n");
						builder.append("Associated metadata attribute specification: ").append(Configuration.simsAttributeSpecificationURI(entry, true)).append("\n");
						builder.append("Associated metadata attribute property: ").append(Configuration.simsAttributePropertyURI(entry, true));
						builder.append(" (range: ").append(SIMSModelMaker.getRange(entry, true)).append(")\n");
					} else { // entry modified in SIMSFR
						builder.append("SIMS original attribute, modified in SIMSFR\n");
						builder.append("Associated metadata attribute specification (SIMS): ").append(Configuration.simsAttributeSpecificationURI(entry, true)).append("\n");
						builder.append("Associated metadata attribute property (SIMS): ").append(Configuration.simsAttributePropertyURI(entry, true));
						builder.append(" (range: ").append(SIMSModelMaker.getRange(entry, true)).append(")\n");
						builder.append("Associated metadata attribute specification (SIMSFR): ").append(Configuration.simsAttributeSpecificationURI(entry, false)).append("\n");
						builder.append("Associated metadata attribute property (SIMSFR): ").append(Configuration.simsAttributePropertyURI(entry, false));
						builder.append(" (range: ").append(SIMSModelMaker.getRange(entry, false)).append(")\n");
					}
				} else { // Property added in SIMSFR
					builder.append("Attribute added in SIMSFR\n");
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

	public List<SIMSFREntry> getEntries() {
		return entries;
	}

	public void setEntries(List<SIMSFREntry> entries) {
		this.entries = entries;
	}

	public void addEntry(SIMSFREntry entry) {
		this.entries.add(entry);
	}

}
