package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.OWL;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import fr.insee.semweb.utils.Utils;

/**
 * Creates RDF models and datasets containing the code lists used in the SIMSFr.
 * 
 * @author Franck Cotton
 */
public class CodelistModelMaker {

	public static Logger logger = LogManager.getLogger(CodelistModelMaker.class);

	/**
	 * Reads all the code lists from the dedicated Excel file into a Jena dataset.
	 * The 'Themes' scheme will be put in a 'concepts' graph (unless excluded), the other lists in a 'codes' graph.
	 * 
	 * @param xlxsFile The Excel file containing the code lists (<code>File</code> object).
	 * @param conceptGraph The URI to use for the 'concepts' graph.
	 * @param codeGraph The URI to use for the 'codes' graph.
	 * @param exclusions Identifiers (notations, e.g. CL_AREA) of code list that will be excluded from the output.
	 * @return A Jena <code>Dataset</code> containing the code lists as SKOS concept schemes in two graphs (or one if CL_TOPICS excluded).
	 */
	public static Dataset readCodelistDataset(File xlxsFile, String conceptGraph, String codeGraph,Model modelToAdd, String... exclusions) {

		Workbook clWorkbook;
		try {
			clWorkbook = WorkbookFactory.create(xlxsFile);
		} catch (Exception e) {
			logger.fatal("Error while opening Excel file " + xlxsFile.getAbsolutePath() + " - " + e.getMessage());
			return null;
		}

		logger.info("Reading code lists from Excel file " + Configuration.CL_XLSX_FILE_NAME);
		List<String> exclusionList = Arrays.asList(exclusions);
		if (!exclusionList.isEmpty()) {
            logger.info("The following code lists are excluded " + exclusionList);
        }
		Model concepts = ModelFactory.createDefaultModel();
		Model codes = modelToAdd == null ? ModelFactory.createDefaultModel() : modelToAdd;

		// Each code list should be on a dedicated sheet of the spreadsheet
		Iterator<Sheet> sheets = clWorkbook.sheetIterator();
		while (sheets.hasNext()) {
			Sheet sheet = sheets.next();
			String sheetName = sheet.getSheetName().trim();
			if (exclusionList.contains(sheetName)) {
                continue;
            }
			logger.info("Reading " + sheetName + " code list");
			if (sheet.getSheetName().equals("CL_TOPICS")) {
                concepts.add(readThemesConceptScheme(sheet));
            }
            else {
                codes.add(readCodelist(sheet));
            }
		}
		try { clWorkbook.close(); } catch (IOException ignored) { }

		Dataset dataset = DatasetFactory.create();
		if (concepts.size() > 0) {
            dataset.addNamedModel(conceptGraph, concepts);
        }
		if (codes.size() > 0) {
            dataset.addNamedModel(codeGraph, codes);
        }

		concepts.close();
		codes.close();
		return dataset;
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
		String notation;
		while (rows.hasNext()) {
			Row row = rows.next();
			notation = row.getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			if (notation.length() == 0) {
                continue;
            }
			englishLabel = row.getCell(1, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			frenchLabel = row.getCell(2, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			Resource code = codeList.createResource(Configuration.inseeCodeURI(notation, Utils.camelCase(pathElement, false, false)), SKOS.Concept);
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
		logger.debug("Generating concept scheme " + scheme.getURI() + " from sheet: 'CL_TOPICS'");
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
	 * Creates a mapping between the notation of each code list (e.g. CL_COLLECTION_MODE) and the corresponding concept (e.g. http://id.insee.fr/codes/concept/ModeCollecte)
	 * 
	 * @return A <code>Map</code> between the code list notations and the corresponding concept expressed as a <code>Resource</code>.
	 */
	public static Map<String, Resource> getNotationConceptMappings() {

		Workbook clWorkbook;
		try {
			clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		} catch (Exception e) {
			logger.fatal("Error while opening Excel file - " + e.getMessage());
			return null;
		}

		Map<String, Resource> mappings = new HashMap<String, Resource>();

		Iterator<Sheet> sheets = clWorkbook.sheetIterator();
		while (sheets.hasNext()) {
			Sheet sheet = sheets.next();
			String clNotation = sheet.getSheetName();
			if (clNotation.contains("CL_TOPICS"))
             {
                continue; // We exclude the category list
            }

			Iterator<Row> rows = sheet.rowIterator();
			rows.next();
			Row csRow = rows.next(); // Get French label on the second line
			String frenchLabel = csRow.getCell(2, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			Resource codeClass = ResourceFactory.createResource(Configuration.codeConceptURI(frenchLabel));
			mappings.put(clNotation, codeClass);
		}
		try { clWorkbook.close(); } catch (IOException ignored) { }

		return mappings;
	}

	/**
	 * Creates a list of language codes from ISO 639 Oasis web site.
	 *
	 */
	public static Model createLanguageCodeList(String iso639PageURL, List<String> codesToSelect) throws IOException {

		// Create model, concept scheme and concept
		Model clModel = ModelFactory.createDefaultModel();
		clModel.setNsPrefix("skos", SKOS.getURI());
		clModel.setNsPrefix("rdfs", RDFS.getURI());
		clModel.setNsPrefix("owl", OWL.getURI());

		String frenchLabel = "Langue";
		String englishLabel = "Language";
		String codeListURI = Configuration.codelistURI(frenchLabel);
		Resource scheme = clModel.createResource(codeListURI, SKOS.ConceptScheme);
		logger.debug("Generating code list " + codeListURI + " from URL: '" + iso639PageURL + "'");
		scheme.addProperty(SKOS.notation, "ISO-639");
		scheme.addProperty(SKOS.prefLabel, clModel.createLiteral(englishLabel, "en"));
		scheme.addProperty(SKOS.prefLabel, clModel.createLiteral(frenchLabel, "fr"));
		// Create also the class representing the code values (see Data Cube §8.1)
		Resource codeClass = clModel.createResource(Configuration.codeConceptURI(frenchLabel), RDFS.Class);
		codeClass.addProperty(RDFS.label, clModel.createLiteral(englishLabel, "en"));
		codeClass.addProperty(RDFS.label, clModel.createLiteral(frenchLabel, "fr"));
		codeClass.addProperty(RDFS.seeAlso, scheme);  // Add a reference from the concept class to the scheme

		// Connect to the web page and browse the DOM to get the desired languages
		Document iso639Page = Jsoup.connect(iso639PageURL).proxy("proxy-rie.http.insee.fr",8080).get();
		Element languageTable = iso639Page.body().getElementsByTag("table").last().getElementsByTag("tbody").first();

		Elements rows = languageTable.getElementsByTag("tr");
		rows.remove(0); // Remove table header
		for (Element row : rows) {
			// Structure is (example): <tr><td><a name="ara">Arabic</a></td><td>arabe</td>
			// <td><nobr><a href="http://psi.oasis-open.org/iso/639/#ara">http://psi.oasis-open.org/iso/639/#ara</a></nobr></td>
			// <td>ar</td><td>ara</td><td>ara</td></tr>
			Elements cells = row.getElementsByTag("td");
			String alpha2 = cells.get(3).text();
			if ((alpha2.length() > 0) &&  codesToSelect.contains(alpha2)) {
				englishLabel = cells.get(0).text();
				frenchLabel = StringUtils.capitalize(cells.get(1).text());
				// HACK Simplify complex labels for Spanish
				if ("es".equals(alpha2)) {
					frenchLabel = frenchLabel.split(";")[0];
					englishLabel = englishLabel.split(";")[0];
				}
				String uri = cells.get(2).text();
				Resource code = clModel.createResource(Configuration.inseeCodeURI(alpha2, "Langue"), SKOS.Concept);
				code.addProperty(RDF.type, codeClass); // The codes are instances of the code concept class
				code.addProperty(SKOS.notation, alpha2);
				code.addProperty(SKOS.prefLabel, clModel.createLiteral(englishLabel, "en"));
				code.addProperty(SKOS.prefLabel, clModel.createLiteral(frenchLabel, "fr"));
				code.addProperty(SKOS.inScheme, scheme);
				code.addProperty(OWL.sameAs, clModel.createResource(uri));
				scheme.addProperty(SKOS.hasTopConcept, code);
				logger.debug("Added code '" + alpha2 + "' to code list");
				System.out.println("'" + alpha2 + "'\t" + englishLabel + "\t" + frenchLabel + "\t" + uri);
			}
		}
		return clModel;
	}

	/**
	 * Strips a label form its right-most parenthesis and its content when it exists.
	 * 
	 * @param label The label to process.
	 * @return The label stripped from its parenthesis.
	 */
	private static String stripLastParenthesis(String label) {
		if (!label.endsWith(")")) {
            return label;
        }
		return label.substring(0, label.lastIndexOf('('));
	}
}
