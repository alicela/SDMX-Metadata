package fr.insee.semweb.sdmx.metadata.test;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
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
	
		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0FamiliesModel = dataset.getNamedModel("http://rdf.insee.fr/graphe/familles");
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

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0SeriesModel = dataset.getNamedModel("http://rdf.insee.fr/graphe/series");
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

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0OperationsModel = dataset.getNamedModel("http://rdf.insee.fr/graphe/operations");
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

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0OperationsModel = dataset.getNamedModel("http://rdf.insee.fr/graphe/operations");
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

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentationsModel = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documentations");
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
	public void testStudyDocuments() throws IOException {
		PrintStream outStream = new PrintStream("src/test/resources/documents.txt");
		M0Checker.studyDocuments(new File("src/test/resources/documents.xslx"), outStream);
		outStream.close();
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
	 */
	@Test
	public void testCheckSIMSAttributes() {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentationsModel = m0Dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations");

		M0Checker.checkSIMSAttributes(m0DocumentationsModel);
		m0DocumentationsModel.close();
		m0Dataset.close();
	}

	@Test
	public void testCheckCoherence() {

		M0Checker.checkCoherence(true);
	}

	/**
	 * Prints to console the list of values of an attribute used in a M0 model.
	 */
	@Test
	public void testListAttributeValues() {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentationsModel = m0Dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations");

		Set<String> attributeValues = M0Checker.listAttributeValues(m0DocumentationsModel, "REF_AREA");
		System.out.println(attributeValues);

		m0DocumentationsModel.close();
		m0Dataset.close();
	}

	@Test
	public void testCheckPropertyValues() {

		// For CL_FREQ/CL_FREQ_FR
		// Set<String> validValues = new HashSet<String>(Arrays.asList("A", "S", "Q", "M", "W", "D", "H", "B", "N", "I", "P", "C", "U", "L", "T"));
		// For CL_SOURCE_CATEGORY
		// Set<String> validValues = new HashSet<String>(Arrays.asList("S", "A", "C", "I", "M", "P", "R"));
		// For CL_UNIT_MEASURE
		// Set<String> validValues = new HashSet<String>(Arrays.asList("DAYS", "EUR", "FRF1", "HOURS", "KILO", "KLITRE", "LITRES", "MAN-DY", "MAN_YR", "MONTHS", "NATCUR", "OUNCES", "PC", "PCPA", "PERS", "PM", "POINTS", "PURE_NUMB", "SQ_M", "TONNES", "UNITS", "USD", "XDR", "XEU"));
		// For CL_SURVEY_STATUS/CL_STATUS
		// Set<String> validValues = new HashSet<String>(Arrays.asList("T", "Q", "C", "G"));
		// For CL_SURVEY_UNIT
		// Set<String> validValues = new HashSet<String>(Arrays.asList("I", "H", "E", "P", "L", "G", "C", "T", "A", "O"));
		// For CL_COLLECTION_MODE
		// Set<String> validValues = new HashSet<String>(Arrays.asList("F", "M", "P", "I", "O"));
		// For CL_AREAL
		Set<String> validValues = new HashSet<String>(Arrays.asList("FR", "FMET", "FPRV", "COM", "DOM", "FR10", "FRB0", "FRC1", "FRC2", "FRD1", "FRD2", "FRE1", "FRE2", "FRF1", "FRF2", "FRF3", "FRG0", "FRH0", "FRI1", "FRI2", "FRI3", "FRJ1", "FRJ2", "FRK1", "FRK2", "FRL0", "FRM0", "FRY1", "FRY2", "FRY3", "FRY4", "FRY5", "FRZZ", "FR101", "FR102", "FR103", "FR104", "FR105", "FR106", "FR107", "FR108", "FRB01", "FRB02", "FRB03", "FRB04", "FRB05", "FRB06", "FRC11", "FRC12", "FRC13", "FRC14", "FRC21", "FRC22", "FRC23", "FRC24", "FRD11", "FRD12", "FRD13", "FRD21", "FRD22", "FRE11", "FRE12", "FRE21", "FRE22", "FRE23", "FRF11", "FRF12", "FRF21", "FRF22", "FRF23", "FRF24", "FRF31", "FRF32", "FRF33", "FRF34", "FRG01", "FRG02", "FRG03", "FRG04", "FRG05", "FRH01", "FRH02", "FRH03", "FRH04", "FRI11", "FRI12", "FRI13", "FRI14", "FRI15", "FRI22", "FRI23", "FRI31", "FRI32", "FRI33", "FRI34", "FRJ11", "FRJ12", "FRJ13", "FRJ14", "FRJ15", "FRJ21", "FRJ22", "FRJ23", "FRJ24", "FRJ25", "FRJ26", "FRJ27", "FRJ28", "FRK11", "FRK12", "FRK13", "FRK14", "FRK21", "FRK22", "FRK23", "FRK24", "FRK25", "FRK26", "FRK27", "FRK28", "FRL01", "FRL02", "FRL04", "FRL05", "FRL06", "FRM01", "FRM02", "FRY10", "FRY20", "FRY30", "FRY40", "FRY50", "FRZZZ", "OTHER"));

		Model invalidStatements = M0Checker.checkPropertyValues("REF_AREA", validValues);
		if (invalidStatements.size() == 0) System.out.println("No invalid statements found");
		else {
			StmtIterator iterator = invalidStatements.listStatements();
			List<Statement> statementList = iterator.toList();
			for (Statement statement : statementList) {
				System.out.println(statement.getSubject() + " - " + statement.getObject());
			}
		}
	}

	/**
	 * Prints to console the list of attributes used in a M0 model.
	 */
	@Test
	public void testCheckModelAttributes() {

		String m0ModelName = "indicateurs";

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + m0ModelName);

		System.out.print("Attributes used in the " + m0ModelName + " M0 model: ");
		System.out.println(M0Checker.checkModelAttributes(m0Model));
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
}
