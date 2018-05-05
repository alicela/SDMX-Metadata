package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Checks that the SIMS part of the SIMSFr model and the original SIMS model are identical.
 */
public class SIMSChecker {

	public static void main(String[] args) {

		System.out.println(checkCoherence());
	}

	/**
	 * Produces a coherence report between SIMS and SIMSFr.
	 */
	public static String checkCoherence() {

		// Reads the SIMS in the original format
		List<SIMSEntry> simsOriginal = readSIMSFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME), false);

		// Reads the SIMS embedded in the SIMSFr format
		List<SIMSEntry> simsInSIMSFr = readSIMSFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME), true);

		StringBuilder report = new StringBuilder();

		// Compare on common fields
		int index = 0;
		for (SIMSEntry entry : simsOriginal) {
			SIMSEntry compareEntry = simsInSIMSFr.get(index);
			// First compare on notation
			if (!entry.getNotation().equals(compareEntry.getNotation())) {
				report.append("\n\nDifference on notation:\n . original: " + entry + "\n . compared:" + compareEntry);
			} else {
				// If notations are equal, check the other fields
				StringBuilder subReport = new StringBuilder();
				if (!entry.getCode().equals(compareEntry.getCode()))
					subReport.append("\nDifference on code:\n . original: " + entry.getCode() + "\n . compared: " + compareEntry.getCode());
				if (!entry.getName().equals(compareEntry.getName()))
					subReport.append("\nDifference on name:\n . original: " + entry.getName() + "\n . compared: " + compareEntry.getName());
				if ((entry.getDescription() != null) && (compareEntry.getDescription() != null) && (!entry.getDescription().equals(compareEntry.getDescription())))
					subReport.append("\nDifference on description:\n . original: " + entry.getName() + "\n . compared: " + compareEntry.getName());
				if ((entry.getRepresentation() != null) && (compareEntry.getRepresentation() != null) && (!entry.getRepresentation().equals(compareEntry.getRepresentation())))
					subReport.append("\nDifference on representation:\n . original: " + entry.getName() + "\n . compared: " + compareEntry.getName());
				if (subReport.length() > 0) report.append("\n\nDifferences found for concept ").append(entry.getNotation()).append(subReport);
			}
			index++;
		}
		if (report.length() == 0) return "No differences found";
		else return report.toString();
	}

	/**
	 * Reads the SIMS from an Excel file.
	 * 
	 * @param xlsxFile The Excel file containing the SIMS.
	 * @param fromFr Indicates if the data should be read in the "SIMS Fr" sheet.
	 * @return The SIMS as a <code>List<SIMSEntry></code> object, or <code>null</code> in case of problem.
	 */
	public static List<SIMSEntry> readSIMSFromExcel(File xlsxFile, boolean fromFr) {
	
		// The SIMS Excel file contains the original SIMS on the first sheet and the SIMSFr data on the second sheet.
	
		int sheetNumber = (fromFr ? 1 : 0);
		
		Workbook simsWorkbook = null;
		Sheet simsSheet = null;
		try {
			simsWorkbook = WorkbookFactory.create(xlsxFile);
			simsSheet = simsWorkbook.getSheetAt(sheetNumber);
		} catch (Exception e) {
			SIMSModelMaker.logger.fatal("Error while opening Excel file - " + e.getMessage());
			return null;
		}
	
		List<SIMSEntry> sims = new ArrayList<SIMSEntry>();
	
		Iterator<Row> rows = simsSheet.rowIterator();
		rows.next(); // Skip the title line
		if (fromFr) rows.next(); // There is a second title line in the SIMSFr format
		while (rows.hasNext()) {
			Row row = rows.next();
			// Additional lines in the SIMSFr format are identified by a non blank K column ("Origine")
			if ((fromFr) && (row.getCell(10, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim().length() > 0)) continue;
	
			SIMSEntry simsEntry = SIMSEntry.readFromRow(row, fromFr);
			if (simsEntry == null) continue;
	
			System.out.println(simsEntry);
			sims.add(simsEntry);
		}
		try { simsWorkbook.close(); } catch (IOException ignored) { }
		return sims;
	}

}
