package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.util.List;

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
		List<SIMSEntry> simsOriginal = SIMSModelMaker.readSIMSFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME), false);

		// Reads the SIMS embedded in the SIMSFr format
		List<SIMSEntry> simsInSIMSFr = SIMSModelMaker.readSIMSFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME), true);

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

}
