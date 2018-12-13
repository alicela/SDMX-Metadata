package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Selector;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.DC;
import org.apache.jena.vocabulary.RDFS;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Methods for consistency checking and reporting on SIMS constructs.
 */
public class SIMSChecker {

	public static void main(String[] args) {

		//System.out.println(checkCoherence());
		System.out.println(simsFrMSDReport());
	}

	/**
	 * Produces a coherence report between SIMS and SIMSFr.
	 * 
	 * @return The report as a String.
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
	 * Produces a report on the SIMSFr MSD.
	 * 
	 * @return A string containing the report.
	 */
	public static String simsFrMSDReport() {

		SIMSFrScheme simsFrScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		Model simsFrMSDModel = SIMSModelMaker.createMetadataStructureDefinition(simsFrScheme, false, true);

		StringBuilder report = new StringBuilder();
		report.append("Report on MSD produced from file " + Configuration.SIMS_XLSX_FILE_NAME);
		// First get the ordered list of MAP/MAS identifiers
		Selector selector = new SimpleSelector(null, DC.identifier, (RDFNode) null) {
			@Override
			public boolean selects(Statement statement) {
				// Retain only attribute specifications to avoid duplication on identifiers and be exhaustive
				return (statement.getSubject().getURI().contains("pecification"));
			}
			
		};
		SortedMap<String, StringBuffer> indexes = new TreeMap<>(new SIMSComparator());
		simsFrMSDModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String identifier = statement.getObject().toString();
				String index = identifier.substring(2);
				if (indexes.containsKey(index)) {
					if (report.length() == 0) report.append("\n\nDuplicate attribute indexes:");
					report.append("\n - " + index + " corresponds both to ").append(indexes.get(index) + " and " + identifier);
				} else indexes.put(index, new StringBuffer(identifier));
			}
		});

		// Go through the model to get the concept names and property ranges
		int prefixLength = "Spécification d'attribut de métadonnées pour le concept ".length();
		selector = new SimpleSelector(null, RDFS.label, (RDFNode) null);
		simsFrMSDModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				// Keep only French labels of the MAS
				if (!statement.getSubject().getURI().contains("pecification")) return;
				if (!statement.getObject().asLiteral().getLanguage().equalsIgnoreCase("fr")) return;
				String index = StringUtils.substringAfterLast(statement.getSubject().getURI(), "/").substring(2);
				indexes.get(index).append("\t" + statement.getObject().asLiteral().getLexicalForm().substring(prefixLength)).append("\t");
			}
		});
		selector = new SimpleSelector(null, RDFS.range, (RDFNode) null); // Only MAPs have a range (and all should have one)
		simsFrMSDModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String index = StringUtils.substringAfterLast(statement.getSubject().getURI(), "/").substring(2); // Attribute index
				String shortType = StringUtils.substringAfterLast(statement.getObject().toString(), "#");
				if (shortType.length() == 0) shortType = StringUtils.substringAfterLast(statement.getObject().toString(), "/");
				indexes.get(index).append(shortType);
			}
		});

		report.append("\n\nList of identifiers and corresponding concepts:");
		for (String index : indexes.keySet()) report.append("\n" + indexes.get(index));
		simsFrMSDModel.close();

		return report.toString();
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

	/**
	 * Compares SIMSFr identifiers with structure X.n.p.q....
	 * X is ignored, then the order is made on n, then p, then q (etc.), supposed to be integers.
	 * If the arguments do not conform to the expected format, various exceptions can be thrown and results are unpredictable.
	 * 
	 * @author Franck
	 */
	private static class SIMSComparator implements Comparator<String> {

		@Override
		public int compare(String leftIndex, String rightIndex) {

			if (leftIndex.equals(rightIndex)) return 0;
			if (leftIndex.startsWith(rightIndex)) return 1;
			if (rightIndex.startsWith(leftIndex)) return -1;
			// Now we are sure that there is a difference on the common components
			String[] leftComponents = leftIndex.split("\\.");
			String[] rightComponents = rightIndex.split("\\.");
			int maxCompare = Math.min(leftComponents.length, rightComponents.length);
			for (int compareIndex = 0; compareIndex < maxCompare; compareIndex++) {
				Integer leftInteger = Integer.parseInt(leftComponents[compareIndex]);
				Integer rightInteger = Integer.parseInt(rightComponents[compareIndex]);
				if (leftInteger == rightInteger) continue;
				return leftInteger.compareTo(rightInteger);
			}
			return 0; // Should not be reached
		}
		
	}
}
