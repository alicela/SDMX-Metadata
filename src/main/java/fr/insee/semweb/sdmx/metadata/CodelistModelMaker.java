package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public class CodelistModelMaker {

	public static Logger logger = LogManager.getLogger(CodelistModelMaker.class);

	/**
	 * Reads all the code lists from the dedicated Excel file into a Jena dataset.
	 * The 'Themes' scheme will be put in a 'concepts' graph, the other lists in a 'codes' graph.
	 * 
	 * @param xlsxFile The Excel file containing the code lists (<code>File</code> object).
	 * @param conceptGraph The URI to use for the 'concepts' graph.
	 * @param codeGraph The URI to use for the 'codes' graph.
	 * @return A Jena <code>Dataset</code> containing the code lists as SKOS concept schemes in two graphs.
	 */
	public static Dataset readCodelistDataset(File xlxsFile, String conceptGraph, String codeGraph) {

		Workbook clWorkbook = null;
		try {
			clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		} catch (Exception e) {
			logger.fatal("Error while opening Excel file - " + e.getMessage());
			return null;
		}

		Model concepts = ModelFactory.createDefaultModel();
		Model codes = ModelFactory.createDefaultModel();

		// Each code list should be on a dedicated sheet of the spreadsheet
		Iterator<Sheet> sheets = clWorkbook.sheetIterator();
		while (sheets.hasNext()) {
			Sheet sheet = sheets.next();
			if (sheet.getSheetName().contains("CL_TOPICS")) concepts.add(readThemesConceptScheme(sheet));
			else codes.add(readCodelist(sheet));
		}
		try { clWorkbook.close(); } catch (IOException ignored) { }

		Dataset dataset = DatasetFactory.create();
		dataset.addNamedModel(conceptGraph, concepts);
		dataset.addNamedModel(codeGraph, codes);

		concepts.close();
		codes.close();
		return dataset;
	}

	/**
	 * Reads all the code lists from the dedicated Excel file into a Jena model.
	 * 
	 * @param xlsxFile The Excel file containing the code lists (<code>File</code> object).
	 * @return A Jena <code>Model</code> containing the code lists as SKOS concept schemes.
	 */
	public static Model readCodelists(File xlsxFile) {

		Workbook clWorkbook = null;
		try {
			clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		} catch (Exception e) {
			logger.fatal("Error while opening Excel file - " + e.getMessage());
			return null;
		}

		Model codeLists = ModelFactory.createDefaultModel();

		// Each code list should be on a dedicated sheet of the spreadsheet
		Iterator<Sheet> sheets = clWorkbook.sheetIterator();
		while (sheets.hasNext()) {
			Sheet sheet = sheets.next();
			if (sheet.getSheetName().contains("CL_TOPICS")) codeLists.add(readThemesConceptScheme(sheet));
			else codeLists.add(readCodelist(sheet));
		}
		try { clWorkbook.close(); } catch (IOException ignored) { }

		return codeLists;
	}

	/**
	 * Reads one code list from a sheet of the dedicated Excel file into a Jena model.
	 * 
	 * @param sheet A sheet of the Excel file containing the code lists (<code>Sheet</code> object).
	 * @return A Jena <code>Model</code> containing the code list as a SKOS concept scheme.
	 */
	public static Model readCodelist(Sheet sheet) {

		Model codeList = ModelFactory.createDefaultModel();
		codeList.setNsPrefix("skos", SKOS.getURI());
		codeList.setNsPrefix("rdfs", RDFS.getURI());

		Iterator<Row> rows = sheet.rowIterator();
		rows.next(); // Skip the title line

		// The first line after the title contains the code and labels of the code list itself
		Row csRow = rows.next();
		String sheetName = csRow.getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		// HACK: STATUS ends with a non-breakable space
		sheetName = sheetName.replaceAll("\u00A0", "");
		String englishLabel = csRow.getCell(1, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		String frenchLabel = csRow.getCell(2, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		String pathElement = frenchLabel;

		String codeListURI = Configuration.codelistURI(frenchLabel);
		Resource scheme = codeList.createResource(codeListURI, SKOS.ConceptScheme);
		logger.debug("Generating code list " + codeListURI + " from sheet: '" + sheetName + "'");
		scheme.addProperty(SKOS.notation, sheetName);
		scheme.addProperty(SKOS.prefLabel, codeList.createLiteral(englishLabel, "en"));
		scheme.addProperty(SKOS.prefLabel, codeList.createLiteral(frenchLabel, "fr"));
		// Create also the class representing the code values (see Data Cube §8.1)
		Resource codeClass = codeList.createResource(Configuration.codeConceptURI(frenchLabel), RDFS.Class);
		codeClass.addProperty(RDFS.label, codeList.createLiteral(englishLabel, "en"));
		codeClass.addProperty(RDFS.label, codeList.createLiteral(frenchLabel, "fr"));
		codeClass.addProperty(RDFS.seeAlso, scheme);  // Add a reference from the concept class to the scheme

		// The next lines list the code values
		String notation = null;
		while (rows.hasNext()) {
			Row row = rows.next();
			notation = row.getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			if (notation.length() == 0) continue;
			englishLabel = row.getCell(1, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			frenchLabel = row.getCell(2, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			Resource code = codeList.createResource(Configuration.inseeCodeURI(notation, Configuration.camelCase(pathElement, false, false)), SKOS.Concept);
			code.addProperty(RDF.type, codeClass); // The codes are instances of the code concept class
			code.addProperty(SKOS.notation, notation);
			code.addProperty(SKOS.prefLabel, codeList.createLiteral(englishLabel, "en"));
			code.addProperty(SKOS.prefLabel, codeList.createLiteral(frenchLabel, "fr"));
			code.addProperty(SKOS.inScheme, scheme);
			scheme.addProperty(SKOS.hasTopConcept, code);
		}

		return codeList;
	}

	/**
	 * Reads the concept scheme of statistical themes from a sheet of the dedicated Excel file.
	 * 
	 * @param sheet The sheet of the Excel file which contains the theme list (<code>Sheet</code> object).
	 * @return A Jena <code>Model</code> containing the themes code lists as a SKOS concept scheme.
	 */
	public static Model readThemesConceptScheme(Sheet sheet) {

		Model themes = ModelFactory.createDefaultModel();
		themes.setNsPrefix("skos", SKOS.getURI());
		themes.setNsPrefix("rdfs", RDFS.getURI());

		Resource scheme = themes.createResource(Configuration.themeSchemeURI(), SKOS.ConceptScheme);
		scheme.addProperty(SKOS.prefLabel, themes.createLiteral("Thèmes statistiques", "fr"));
		scheme.addProperty(SKOS.prefLabel, themes.createLiteral("Statistical themes", "en"));
		// Create also the class representing the code values (see Data Cube §8.1)
		Resource themeClass = themes.createResource(Configuration.themeClassURI(), RDFS.Class);
		themeClass.addProperty(RDFS.label, themes.createLiteral("Thème statistique", "fr"));
		themeClass.addProperty(RDFS.label, themes.createLiteral("Statistical theme", "en"));

		Iterator<Row> rows = sheet.rowIterator();
		rows.next(); rows.next(); // The list begins on the third row
		Resource topConcept = null;
		while (rows.hasNext()) {
			Row themeRow = rows.next();
			String notation = themeRow.getCell(0).toString().trim();
			String labelFR = themeRow.getCell(2).toString().trim();
			String labelEN = themeRow.getCell(1).toString().trim();
			if (notation.length() == 3) { // Top-level concept
				topConcept = themes.createResource(Configuration.themeURI(notation), SKOS.Concept);
				topConcept.addProperty(RDF.type, themeClass);
				topConcept.addProperty(SKOS.notation, notation);
				topConcept.addProperty(SKOS.prefLabel, themes.createLiteral(stripLastParenthesis(labelFR), "fr"));
				topConcept.addProperty(SKOS.prefLabel, themes.createLiteral(stripLastParenthesis(labelEN), "en"));
				topConcept.addProperty(SKOS.inScheme, scheme);
				topConcept.addProperty(SKOS.topConceptOf, scheme);
				scheme.addProperty(SKOS.hasTopConcept, topConcept);
			} else {
				Resource theme = themes.createResource(Configuration.themeURI(notation), SKOS.Concept);
				theme.addProperty(RDF.type, themeClass);
				theme.addProperty(SKOS.notation, notation);
				theme.addProperty(SKOS.prefLabel, themes.createLiteral(stripLastParenthesis(labelFR), "fr"));
				theme.addProperty(SKOS.prefLabel, themes.createLiteral(stripLastParenthesis(labelEN), "en"));
				theme.addProperty(SKOS.inScheme, scheme);
				theme.addProperty(SKOS.broader, topConcept);
				topConcept.addProperty(SKOS.narrower, theme);
			}
		}

		return themes;
	}

	/**
	 * Strips a label form its righ-most parenthesis and its content when it exists.
	 * 
	 * @param label The label to process.
	 * @return The label stripped from its parenthesis.
	 */
	private static String stripLastParenthesis(String label) {
		if (!label.endsWith(")")) return label;
		return label.substring(0, label.lastIndexOf('('));
	}

}
