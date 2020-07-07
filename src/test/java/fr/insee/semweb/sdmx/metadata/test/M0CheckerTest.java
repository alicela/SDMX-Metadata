package fr.insee.semweb.sdmx.metadata.test;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.M0Checker;

/**
 * Test and launch methods for class <code>M0Checker</code>.
 * 
 * @author Franck
 */
public class M0CheckerTest {

	/**
	 * Runs the checks on the M0 families model and prints the report to a file.
	 * 
	 * @throws IOException In case of problem writing the report.
	 */
	@Test
	public void testCheckFamilies() throws IOException {
	
		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0FamiliesModel = m0Dataset.getNamedModel("http://rdf.insee.fr/graphe/familles");
		String report = M0Checker.checkSeries(m0FamiliesModel);
		PrintStream outStream = new PrintStream("src/test/resources/reports/m0-families.txt");
		outStream.print(report);
		outStream.close();
		m0FamiliesModel.close();
	}

	/**
	 * Runs the checks on the M0 series model and prints the report to a file.
	 * 
	 * @throws IOException In case of problem writing the report.
	 */
	@Test
	public void testCheckSeries() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0SeriesModel = m0Dataset.getNamedModel("http://rdf.insee.fr/graphe/series");
		String report = M0Checker.checkSeries(m0SeriesModel);
		PrintStream outStream = new PrintStream("src/test/resources/reports/m0-series.txt");
		outStream.print(report);
		outStream.close();
		m0SeriesModel.close();
	}

	/**
	 * Runs the basic checks on the M0 operations model and prints the report to a file.
	 * 
	 * @throws IOException In case of problem writing the report.
	 */
	@Test
	public void testCheckOperations() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0OperationsModel = m0Dataset.getNamedModel("http://rdf.insee.fr/graphe/operations");
		String report = M0Checker.checkOperations(m0OperationsModel);
		PrintStream outStream = new PrintStream("src/test/resources/reports/m0-operations.txt");
		outStream.print(report);
		outStream.close();
		m0OperationsModel.close();
	}

	/**
	 * Runs the basic checks on the M0 operations model and prints the report to a file.
	 * 
	 * @throws IOException In case of problem writing the report.
	 */
	@Test
	public void testCheckOperationsWithAttributes() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0OperationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "operations");
		String report = M0Checker.checkOperations(m0OperationsModel, "ID_DDS");
		PrintStream outStream = new PrintStream("src/test/resources/reports/m0-operations-id_dds.txt");
		outStream.print(report);
		outStream.close();
		m0OperationsModel.close();
	}

	/**
	 * Runs the checks on the M0 documentation model and prints the report to a file.
	 * 
	 * @throws IOException In case of problem writing the report.
	 */
	@Test
	public void testCheckDocumentations() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documentations");
		String report = M0Checker.checkDocumentations(m0DocumentationsModel);
		PrintStream outStream = new PrintStream("src/test/resources/reports/m0-documentations.txt");
		outStream.print(report);
		outStream.close();
		m0DocumentationsModel.close();
	}

	/**
	 * Runs the counts and export on the 'documents' M0 model.
	 * 
	 * @throws IOException In case of problem writing the report.
	 */
	@Test
	public void testCheckDocuments() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documents");
		Model m0AssociationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");

		String report = M0Checker.checkDocuments(m0DocumentsModel, m0AssociationsModel, new File("src/test/resources/documents.xslx"));
		PrintStream outStream = new PrintStream("src/test/resources/m0-documents.txt");
		outStream.print(report);
		outStream.close();
		m0DocumentsModel.close();
		m0AssociationsModel.close();
		m0Dataset.close();
	}

	/**
	 * Runs the counts and export on the 'links' M0 model.
	 * 
	 * @throws IOException In case of problem writing the report.
	 */
	@Test
	public void testCheckLinks() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0LinksModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "liens");
		Model m0AssociationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		String report = M0Checker.checkLinks(m0LinksModel, m0AssociationsModel, new File("src/test/resources/links.xslx"), Arrays.asList("TITLE", "TYPE", "URI", "SUMMARY"));
		PrintStream outStream = new PrintStream("src/test/resources/m0-links.txt");
		outStream.print(report);
		outStream.close();
		m0LinksModel.close();
		m0AssociationsModel.close();
		m0Dataset.close();
	}

	/**
	 * Checks that attributes present in the 'documentations' M0 model are defined in SIMSFr.
	 * Problems are reported in the log file.
	 */
	@Test
	public void testCheckSIMSAttributes() {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documentations");
		M0Checker.checkSIMSAttributes(m0DocumentationsModel);

		m0DocumentationsModel.close();
		m0Dataset.close();
	}

	/**
	 * Runs the checks on the DATE and DATE_PUBLICATION attributes of documents.
	 */
	@Test
	public void testCheckDocumentDates() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documents");
		String report = M0Checker.checkDocumentDates(m0DocumentsModel);
		PrintStream outStream = new PrintStream("src/test/resources/m0-documents.txt");
		outStream.print(report);
		outStream.close();
		m0DocumentsModel.close();
		m0Dataset.close();
	}

	/**
	 * Runs the comparison between the values of the direct attributes of series or operations and those in the 'documentations' model.
	 */
	@Test
	public void testCheckModelCoherence() {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		M0Checker.checkModelCoherence(m0Dataset, true);
		m0Dataset.close();
	}

	/**
	 * Prints to console the list of values of an attribute used in a M0 model.
	 */
	@Test
	public void testListAttributeValues() {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documentations");

		Set<String> attributeValues = M0Checker.listAttributeValues(m0DocumentationsModel, "REF_AREA");
		System.out.println(attributeValues);

		m0DocumentationsModel.close();
		m0Dataset.close();
	}

	/**
	 * Runs the check on coded attributes values.
	 */
	@Test
	public void testCheckCodedAttributeValues() {

		SortedMap<String, SortedSet<String>> codeLists = new TreeMap<String, SortedSet<String>>();
		// For CL_FREQ/CL_FREQ_FR
		codeLists.put("CL_FREQ", new TreeSet<String>(Arrays.asList("A", "S", "Q", "M", "W", "D", "H", "B", "N", "I", "P", "C", "U", "L", "T")));
		// For CL_SOURCE_CATEGORY
		codeLists.put("CL_SOURCE_CATEGORY", new TreeSet<String>(Arrays.asList("S", "A", "C", "I", "M", "P", "R")));
		// For CL_UNIT_MEASURE
		codeLists.put("CL_UNIT_MEASURE", new TreeSet<String>(Arrays.asList("DAYS", "EUR", "FRF1", "HOURS", "KILO", "KLITRE", "LITRES", "MAN-DY", "MAN_YR", "MONTHS", "NATCUR", "OUNCES", "PC", "PCPA", "PERS", "PM", "POINTS", "PURE_NUMB", "SQ_M", "TONNES", "UNITS", "USD", "XDR", "XEU")));
		// For CL_SURVEY_STATUS/CL_STATUS
		codeLists.put("CL_STATUS", new TreeSet<String>(Arrays.asList("T", "Q", "C", "G")));
		// For CL_SURVEY_UNIT
		codeLists.put("CL_SURVEY_UNIT", new TreeSet<String>(Arrays.asList("I", "H", "E", "P", "L", "G", "C", "T", "A", "O")));
		// For CL_COLLECTION_MODE
		codeLists.put("CL_COLLECTION_MODE", new TreeSet<String>(Arrays.asList("F", "M", "P", "I", "O")));
		// For CL_AREA
		codeLists.put("CL_AREA", new TreeSet<String>(Arrays.asList("FHM", "FR", "FR10", "FR101", "FR102", "FR103", "FR104", "FR105", "FR106", "FR107", "FR108", "FRB0", "FRB01", "FRB02", "FRB03", "FRB04", "FRB05", "FRB06", "FRC1", "FRC11", "FRC12", "FRC13", "FRC14", "FRC2", "FRC21", "FRC22", "FRC23", "FRC24", "FRD1", "FRD11", "FRD12", "FRD13", "FRD2", "FRD21", "FRD22", "FRE1", "FRE11", "FRE12", "FRE2", "FRE21", "FRE22", "FRE23", "FRF1", "FRF11", "FRF12", "FRF2", "FRF21", "FRF22", "FRF23", "FRF24", "FRF3", "FRF31", "FRF32", "FRF33", "FRF34", "FRG0", "FRG01", "FRG02", "FRG03", "FRG04", "FRG05", "FRH0", "FRH01", "FRH02", "FRH03", "FRH04", "FRHDF01", "FRI1", "FRI11", "FRI12", "FRI13", "FRI14", "FRI15", "FRI2", "FRI22", "FRI23", "FRI3", "FRI31", "FRI32", "FRI33", "FRI34", "FRJ1", "FRJ11", "FRJ12", "FRJ13", "FRJ14", "FRJ15", "FRJ2", "FRJ21", "FRJ22", "FRJ23", "FRJ24", "FRJ25", "FRJ26", "FRJ27", "FRJ28", "FRK1", "FRK11", "FRK12", "FRK13", "FRK14", "FRK2", "FRK21", "FRK22", "FRK23", "FRK24", "FRK25", "FRK26", "FRK27", "FRK28", "FRL0", "FRL01", "FRL02", "FRL04", "FRL05", "FRL06", "FRM0", "FRM01", "FRM02", "FRP", "FRY1", "FRY10", "FRY2", "FRY20", "FRY3", "FRY30", "FRY4", "FRY5", "FRY50", "FRZZ", "FRZZZ", "GS", "MF", "OTHER")));

		SortedMap<String, String> codedAttributes = new TreeMap<>();
		codedAttributes.put("STATUS", "CL_STATUS");
		codedAttributes.put("REF_AREA", "CL_AREA");
		codedAttributes.put("FREQ_DISS", "CL_FREQ");
		codedAttributes.put("FREQ_COLL", "CL_FREQ");
		codedAttributes.put("COLLECTION_MODE", "CL_COLLECTION_MODE");
		codedAttributes.put("SURVEY_UNIT", "CL_SURVEY_UNIT");
		// NB: SOURCE_CATEGORY is also coded (with CL_SOURCE_CATEGORY), but it is a direct attribute and thus not checked by checkCodedAttributeValues

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentationsModel = m0Dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations");

		for (String attributeToCheck : codedAttributes.keySet()) {
			Set<String> attributeValues = codeLists.get(codedAttributes.get(attributeToCheck));
			Model invalidStatements = M0Checker.checkCodedAttributeValues(m0DocumentationsModel, attributeToCheck, attributeValues);
			if (invalidStatements.size() == 0) System.out.println("No invalid statements found for attribute " + attributeToCheck);
			else {
				System.out.println(invalidStatements.size() + " invalid values found for attribute " + attributeToCheck);
				StmtIterator iterator = invalidStatements.listStatements();
				List<Statement> statementList = iterator.toList();
				for (Statement statement : statementList) {
					System.out.println(statement.getSubject() + " - " + statement.getObject());
				}
			}
			Set<String> unusedValues = M0Checker.checkCodedAttributeUnusedValues(m0DocumentationsModel, attributeToCheck, attributeValues);
			if (unusedValues.size() == 0) System.out.println("All valid values are used for attribute " + attributeToCheck);
			else {
				System.out.println("The following valid values are not actually used for attribute " + attributeToCheck + ":\n\t" + unusedValues);
				attributeValues.removeAll(unusedValues);
				System.out.println("Values actually used:\n\t" + attributeValues);
			}
			System.out.println();
		}
	}

	/**
	 * Prints to console the list of attributes used in a M0 model, sorted alphabetically.
	 */
	@Test
	public void testGetModelAttributes() {

		String m0ModelName = "indicateurs";

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + m0ModelName);

		System.out.print("Attributes used in the " + m0ModelName + " M0 model: ");
		System.out.println(M0Checker.getModelAttributes(m0Model));
	}

	/**
	 * Prints to console the list of named graphs contained in the M0 dataset, sorted alphabetically.
	 */
	@Test
	public void testListModels() {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		SortedSet<String> graphNames = new TreeSet<String>();
		m0Dataset.listNames().forEachRemaining(new Consumer<String>() {
			@Override
			public void accept(String graphName) {
				graphNames.add(graphName);

				}
		});
		m0Dataset.close();
		System.out.println("Named graphs in " + Configuration.M0_FILE_NAME + "\n");
		for (String graphName : graphNames) System.out.println(graphName);
	}

	/**
	 * Runs the basic checks on the M0 organization model and prints the report to a file.
	 * 
	 * @throws IOException In case of problem writing the report.
	 */
	@Test
	public void testCheckOrganizations() throws IOException {

		// Attributes to report. Notes: 'ORGANISATION' always empty
		String[] attributesToCheck = new String[]{"ID_CODE", "ALT_LABEL", "TITLE", "ORIGINE", "STAKEHOLDERS", "TYPE", "UNIT_OF"};

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0rganizationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "organismes");
		String report = M0Checker.checkOrganizations(m0rganizationsModel, attributesToCheck);
		PrintStream outStream = new PrintStream("src/test/resources/reports/m0-organizations.txt");
		outStream.print(report);
		outStream.close();
		m0rganizationsModel.close();
	}

	/**
	 * Runs the basic checks on the M0 organization model and prints the report to a file.
	 * 
	 * @throws IOException In case of problem writing the report.
	 */
	@Test
	public void testCheckUsedOrganizations() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		String report = M0Checker.checkUsedOrganizations(m0AssociationsModel);
		PrintStream outStream = new PrintStream("src/test/resources/reports/m0-organizations-usage.txt");
		outStream.print(report);
		outStream.close();
		m0AssociationsModel.close();
	}

	/**
	 * Runs the basic checks on the M0 organizations mappings and prints the report to the console.
	 * 
	 * @throws IOException In case of problem reading the Excel file or writing the report.
	 */
	@Test
	public void testCheckOrganizationMappings() throws IOException {
		
		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Workbook orgWorkbook = WorkbookFactory.create(new File(Configuration.ORGANIZATIONS_XLSX_FILE_NAME));
		System.out.println(M0Checker.checkOrganizationMappings(m0Dataset, orgWorkbook));
		m0Dataset.close();
	}
}
