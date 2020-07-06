package fr.insee.semweb.sdmx.metadata.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.CodelistModelMaker;
import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.M0Converter;
import fr.insee.semweb.sdmx.metadata.M0SIMSConverter;
import fr.insee.semweb.sdmx.metadata.OrganizationModelMaker;

/**
 * Test and launch methods for class <code>M0Converter</code>.
 * 
 * @author Franck
 */
public class M0ConverterTest {

	/**
	 * Reads the list of fixed mappings between M0 identifiers and target URIs for series and saves it to a file.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testGetIdURIFixedMappingsSeries() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Map<Integer, String> mappings = M0Converter.getIdURIFixedMappings(m0Dataset, "serie");
		m0Dataset.close();

		try (PrintWriter writer = new PrintWriter("src/test/resources/mappings-id-uri-series.txt", "UTF-8")) {
			mappings.entrySet().stream().sorted(Map.Entry.<Integer, String>comparingByKey()).forEach(writer::println);
		}
	}

	/**
	 * Reads the list of fixed mappings between M0 identifiers and target URIs for operations and saves it to a file.
	 * Also inverts the mappings in order to check if there are no duplicates and saves the inverted list to a file.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testGetIdURIFixedMappingsOperations() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Map<Integer, String> mappings = M0Converter.getIdURIFixedMappings(m0Dataset, "operation");

		try (PrintWriter writer = new PrintWriter("src/test/resources/mappings-id-uri-operations.txt", "UTF-8")) {
			mappings.entrySet().stream().sorted(Map.Entry.<Integer, String>comparingByKey()).forEach(writer::println);
		}

		// "Invert" the map to find duplicates (cases where multiple M0 identifiers map to the same target URI)
		Map<String, List<Integer>> inverse = new HashMap<String, List<Integer>>();
		for (int m0Id : mappings.keySet()) {
			if (!inverse.containsKey(mappings.get(m0Id))) inverse.put(mappings.get(m0Id), new ArrayList<Integer>());
			inverse.get(mappings.get(m0Id)).add(m0Id);
		}
		try (BufferedWriter writer = new BufferedWriter(new FileWriter("src/test/resources/mappings-uri-id-operations.txt"))) {
			for (String uri : inverse.keySet()) writer.write(uri + " - " + inverse.get(uri) + System.lineSeparator());
		}
	}

	/**
	 * Creates and writes to a file the mappings between M0 URIs and target URIs for families, series, operations and indicators.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testCreateURIMappings() throws IOException {

		Map<String, String> mappings = M0Converter.createURIMappings();
		Files.write(Paths.get("src/test/resources/mappings-uri.txt"), () -> mappings.entrySet().stream().<CharSequence>map(e -> e.getKey() + "\t" + e.getValue()).iterator());
	}

	/**
	 * Extracts the code lists defined in the M0 model and saves them in a Turtle file.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testConvertM0CodeListsTurtle() throws IOException {

		Model m0CodeListsModel = M0Converter.convertCodeLists();
		m0CodeListsModel.write(new FileOutputStream("src/test/resources/m0-codelists.ttl"), "TTL");
		m0CodeListsModel.close();
	}

	/**
	 * Extracts the code lists defined in the M0 model and saves them in an Excel file.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testConvertM0CodeListsExcel() throws IOException {

		Model m0CodeListsModel = M0Converter.convertCodeLists();
		Workbook codeListsWorkbook = WorkbookFactory.create(true); // Create XSSF workbook
		// Main loop on concept schemes
		m0CodeListsModel.listStatements(null, RDF.type, SKOS.ConceptScheme).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement schemeStatement) {
				Resource codeList = schemeStatement.getSubject();
				Sheet currentSheet = codeListsWorkbook.createSheet(codeList.getProperty(SKOS.notation).getObject().toString());
				currentSheet.createRow(0).createCell(0, CellType.STRING).setCellValue(codeList.getProperty(SKOS.prefLabel, "fr").getObject().asLiteral().getLexicalForm());
				SortedMap<String, String> codeMap = new TreeMap<>();
				// Inner loop on concepts of a scheme, with intermediate storage in a tree map for sorting purposes
				m0CodeListsModel.listStatements(null, SKOS.inScheme, codeList).forEachRemaining(new Consumer<Statement>() {
					@Override
					public void accept(Statement codeStatement) {
						Resource codeResource = codeStatement.getSubject();
						String code = codeResource.getRequiredProperty(SKOS.notation).getObject().asLiteral().getLexicalForm();
						String label = codeResource.getRequiredProperty(SKOS.prefLabel, "fr").getObject().asLiteral().getLexicalForm();
						codeMap.put(code, label);
					}
				});
				// Write the sorted map in the Excel sheet
				int rowIndex = 1;
				for (String code : codeMap.keySet()) {
					Row currentRow = currentSheet.createRow(rowIndex++);
					currentRow.createCell(0, CellType.STRING).setCellValue(code);
					currentRow.createCell(1, CellType.STRING).setCellValue(codeMap.get(code));
				}
			}
		});
		try (FileOutputStream outputStream = new FileOutputStream("src/test/resources/m0-codelists.xlsx")) {
			codeListsWorkbook.write(outputStream);
			codeListsWorkbook.close();
		}
		m0CodeListsModel.close();
	}

	/**
	 * Creates a RDF dataset containing all families, series, operations and indicators in the target model and saves it to a TriG file.
	 * 
	 * @throws Exception In case of problem while writing the output file.
	 */
	@Test
	public void testConvertAllOperationsAndIndicators() throws IOException {

		Dataset allOperationsAndIndicatorsDataset = M0Converter.convertAllOperationsAndIndicators("http://rdf.insee.fr/graphes/operations", "http://rdf.insee.fr/graphes/produits");
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/all-operations-and-indicators.trig"), allOperationsAndIndicatorsDataset, Lang.TRIG);
		allOperationsAndIndicatorsDataset.close();
	}

	/**
	 * Creates a RDF dataset containing all base resources (code lists, organizations, families, series, operations and indicators) in the target model and saves it to a TriG file.
	 * 
	 * @throws Exception In case of problem while writing the output file.
	 */
	@Test
	public void testConvertAllBaseResources() throws Exception {

		// Code lists from the Excel file
		Dataset allBaseResourcesDataset = CodelistModelMaker.readCodelistDataset(new File(Configuration.CL_XLSX_FILE_NAME), "http://rdf.insee.fr/graphes/concepts", "http://rdf.insee.fr/graphes/codes");
		// Families, series, operations converted from the M0 model
		allBaseResourcesDataset.addNamedModel("http://rdf.insee.fr/graphes/operations", M0Converter.convertAllOperations());
		// Indicators converted from the M0 model
		allBaseResourcesDataset.addNamedModel("http://rdf.insee.fr/graphes/produits", M0Converter.convertIndicators());
		// Organizations from the Excel file
		Workbook orgWorkbook = WorkbookFactory.create(new File(Configuration.ORGANIZATIONS_XLSX_FILE_NAME));
		Model orgModel = OrganizationModelMaker.createSSMModel(orgWorkbook);
		orgModel.add(OrganizationModelMaker.createInseeModel(orgWorkbook));
		orgModel.close();
		allBaseResourcesDataset.addNamedModel("http://rdf.insee.fr/graphes/organisations", orgModel);
		
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/all-base-resources.trig"), allBaseResourcesDataset, Lang.TRIG);
		allBaseResourcesDataset.close();
	}

	/**
	 * Creates and writes to a Turtle file the information about families, series and operations in the target model.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testConvertAllOperations() throws IOException {

		Model allOPerationsModel = M0Converter.convertAllOperations();
		allOPerationsModel.write(new FileOutputStream("src/test/resources/all-operations.ttl"), "TTL");
		allOPerationsModel.close();
	}

	/**
	 * Creates and writes to a Turtle file the information about families in the target model.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testConvertFamilies() throws IOException {

		Model familiesModel = M0Converter.convertFamilies();
		familiesModel.write(new FileOutputStream("src/test/resources/families.ttl"), "TTL");
		familiesModel.close();
	}

	/**
	 * Creates and writes to a Turtle file the information about indicators in the target model.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testConvertIndicators() throws IOException {

		Model indicatorsModel = M0Converter.convertIndicators();
		indicatorsModel.write(new FileOutputStream("src/test/resources/indicators.ttl"), "TTL");
		indicatorsModel.close();
	}

	/**
	 * Creates and writes to a Turtle file the information about operations in the target model.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testConvertOperations() throws IOException {

		Model operationsModel = M0Converter.convertOperations();
		operationsModel.write(new FileOutputStream("src/test/resources/operations.ttl"), "TTL");
		operationsModel.close();
	}

	/**
	 * Creates and writes to a Turtle file the information about organizations in the target ORG model.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testConvertOrganizations() throws IOException {

		Model organizationsModel = M0Converter.convertOrganizations();
		organizationsModel.write(new FileOutputStream("src/test/resources/organizations.ttl"), "TTL");
		organizationsModel.close();
	}

	/**
	 * Creates and writes to a Turtle file the information about series in the target model.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testConvertSeries() throws IOException {

		Model seriesModel = M0Converter.convertSeries();
		seriesModel.write(new FileOutputStream("src/test/resources/series.ttl"), "TTL");
		seriesModel.close();
	}

	/**
	 * Reads from an external spreadsheet the relations between the families and the statistical themes and writes them in a file.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testGetFamilyThemesRelations() throws IOException {

		SortedMap<String, List<String>> relations = M0Converter.getFamilyThemesRelations();
		for (String family : relations.keySet()) System.out.println(family + " has theme(s) " + relations.get(family));
		try (PrintWriter writer = new PrintWriter("src/test/resources/family-themes-relations.txt", "UTF-8")) {
			relations.entrySet().stream().forEach(writer::println);
		}
	}

	/**
	 * Reads from the M0 dataset and write to different files the list of links associated to each documentation number and SIMS attribute.
	 * Files created are: (documentation id, attribute name, link id) triples for French and English, and list of all links referenced in the model.
	 * 
	 * @throws IOException In case of problem while writing an output file.
	 */
	@Test
	public void testGetAttributeLinks() throws IOException {

		// We also list all the links that are actually referenced in the documentations
		SortedSet<String> referencedLinks = new TreeSet<String>();
		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<Integer, SortedMap<String, SortedSet<String>>> relations = M0SIMSConverter.getAttributeReferences(m0AssociationsModel, "fr", true);
		try (PrintWriter writer = new PrintWriter("src/test/resources/links-references-french.txt", "UTF-8")) {
			for (Integer documentationId : relations.keySet()) {
				writer.println(documentationId + "\t" + relations.get(documentationId));
				for (String attributeName : relations.get(documentationId).keySet()) referencedLinks.addAll(relations.get(documentationId).get(attributeName));
			}			
		}
		relations = M0SIMSConverter.getAttributeReferences(m0AssociationsModel, "en", true);
		try (PrintWriter writer = new PrintWriter("src/test/resources/links-references-english.txt", "UTF-8")) {
			for (Integer documentationId : relations.keySet()) {
				writer.println(documentationId + "\t" + relations.get(documentationId));
				for (String attributeName : relations.get(documentationId).keySet()) referencedLinks.addAll(relations.get(documentationId).get(attributeName));
			}
		}
		try (PrintWriter writer = new PrintWriter("src/test/resources/links-referenced.txt", "UTF-8")) {
			referencedLinks.stream().forEach(writer::println);
		}
		m0AssociationsModel.close();
		m0Dataset.close();
 	}

	/**
	 * Reads from the M0 dataset and writes to different files the list of documents associated to each documentation number and SIMS attribute.
	 * Files created are: (documentation id, attribute name, document id) triples for French and English, and list of all documents referenced in the model.
	 * 
	 * @throws IOException In case of problem while writing an output file.
	 */
	@Test
	public void testGetAttributeDocuments() throws IOException {

		// We also list the documents that are actually referenced in the relations
		SortedSet<String> referencedDocuments = new TreeSet<String>();
		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<Integer, SortedMap<String, SortedSet<String>>> relations = M0SIMSConverter.getAttributeReferences(m0AssociationsModel, "fr", false);
		try (PrintWriter writer = new PrintWriter("src/test/resources/documents-references-french.txt", "UTF-8")) {
			for (Integer documentationId : relations.keySet()) {
				writer.println(documentationId + "\t" + relations.get(documentationId));
				for (String attributeName : relations.get(documentationId).keySet()) referencedDocuments.addAll(relations.get(documentationId).get(attributeName));
			}
		}
		relations = M0SIMSConverter.getAttributeReferences(m0AssociationsModel, "en", false);
		try (PrintWriter writer = new PrintWriter("src/test/resources/documents-references-english.txt", "UTF-8")) {
			for (Integer documentationId : relations.keySet()) {
				writer.println(documentationId + "\t" + relations.get(documentationId));
				for (String attributeName : relations.get(documentationId).keySet()) referencedDocuments.addAll(relations.get(documentationId).get(attributeName));
			}			
		}
		try (PrintWriter writer = new PrintWriter("src/test/resources/links-referenced.txt", "UTF-8")) {
			referencedDocuments.stream().forEach(writer::println);
		}
		m0AssociationsModel.close();
		m0Dataset.close();
 	}

	/**
	 * Reads from the M0 dataset and writes to files the lists of language tags associated with each documents and link identifier.
	 * Files created are: (documentation id, language tag) and (link identifier, language tag).
	 * 
	 * @throws IOException In case of problem while writing an output file.
	 */
	@Test
	public void testGetLanguageTags() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<Integer, String> languageTags = M0SIMSConverter.getLanguageTags(m0AssociationModel, true);
		try (PrintWriter writer = new PrintWriter("src/test/resources/links-languages.txt", "UTF-8")) {
			languageTags.forEach((key, value) -> writer.println(key + "\t" + value));
		}
		languageTags = M0SIMSConverter.getLanguageTags(m0AssociationModel, false);
		try (PrintWriter writer = new PrintWriter("src/test/resources/documents-languages.txt", "UTF-8")) {
			languageTags.forEach((key, value) -> writer.println(key + "\t" + value));
		}
		m0AssociationModel.close();
		m0Dataset.close();
	}

	/**
	 * Converts the information about external links from the M0 dataset into the target format and writes the model to a Turtle file.
	 * 
	 * @throws IOException In case of problems while writing the output file.
	 */
	@Test
	public void testConvertLinksToSIMS() throws IOException {

		Model linksModel = M0SIMSConverter.convertLinksToSIMS();
		linksModel.write(new FileOutputStream("src/main/resources/data/sims-links.ttl"), "TTL");
		linksModel.close();
	}

	/**
	 * Converts the information about external documents from the M0 dataset into the target format and writes the model to a Turtle file.
	 * 
	 * @throws IOException In case of problems while writing the output file.
	 */
	@Test
	public void testConvertDocumentsToSIMS() throws IOException {

		Model documentsModel = M0SIMSConverter.convertDocumentsToSIMS();
		documentsModel.write(new FileOutputStream("src/test/resources/sims-documents.ttl"), "TTL");
		documentsModel.close();
	}

	/**
	 * Reads from the M0 dataset and writes to a file the list of documents with their publication dates.
	 * 
	 * @throws IOException In case of problems while writing the output file.
	 */
	@Test
	public void testGetDocumentDates() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documents");

		SortedMap<Integer, Date> documentDates = M0SIMSConverter.getDocumentDates(m0DocumentsModel);
		try (PrintWriter writer = new PrintWriter("src/test/resources/document-dates.txt", "UTF-8")) {
			for (Integer documentNumber : documentDates.keySet()) writer.println(documentNumber + "\t" + documentDates.get(documentNumber));
		}
		m0DocumentsModel.close();
		m0Dataset.close();
	}

	/**
	 * Reads from the M0 dataset and writes to a file the correspondence between M0 documentation identifiers and the URIs of the documented resources.
	 * 
	 * @throws IOException In case of problems while writing the output file.
	 */
	@Test
	public void testGetSIMSAttachments() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");

		SortedMap<Integer, String> simsAttachments = M0SIMSConverter.getSIMSAttachments(m0AssociationsModel);
		try (PrintWriter writer = new PrintWriter("src/test/resources/sims-attachments.txt", "UTF-8")) {
			for (Integer documentNumber : simsAttachments.keySet()) writer.println(documentNumber + "\t" + simsAttachments.get(documentNumber));
		}
		m0AssociationsModel.close();
		m0Dataset.close();
	}

	/**
	 * Converts all SIMS to the target model and writes the result as a TriG or Turtle file.
	 * 
	 * @throws IOException In case of problems while writing the output file.
	 */
	@Test
	public void testConvertAllToSIMS() throws IOException {

		boolean namedGraphs = true;

		Dataset simsDataset = M0SIMSConverter.convertToSIMS(null, namedGraphs, true, false);
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/models/sims-all." + (namedGraphs ? "trig" : "ttl")), simsDataset, (namedGraphs ? Lang.TRIG : Lang.TURTLE));
	}

	/**
	 * Converts a list of SIMS to the target model and writes the result as a TriG or Turtle file.
	 * 
	 * @throws IOException In case of problems while writing the output file.
	 */
	@Test
	public void testConvertListToSIMS() throws IOException {

		boolean namedGraphs = true;
		List<Integer> simsNumbers = Arrays.asList(1501, 1508);

		List<String> simsNumberStrings = simsNumbers.stream().map(Object::toString).collect(Collectors.toList());
		String fileName = "src/main/resources/data/models/sims-" + String.join("-", simsNumberStrings) + "." + (namedGraphs ? "trig" : "ttl");
		Dataset simsDataset = M0SIMSConverter.convertToSIMS(simsNumbers, namedGraphs, false, false);
		RDFDataMgr.write(new FileOutputStream(fileName), simsDataset, (namedGraphs ? Lang.TRIG : Lang.TURTLE));
	}

	/**
	 * Converts one SIMS to the target model and writes the result as a TriG or Turtle file.
	 * 
	 * @throws IOException In case of problems while writing the output file.
	 */
	@Test
	public void testConvertOneToSIMS() throws IOException {

		boolean namedGraphs = true;
		List<Integer> simNumber = Arrays.asList(1893);

		String fileName = "src/main/resources/data/models/sims-" + simNumber.get(0) + "." + (namedGraphs ? "trig" : "ttl");
		Dataset simsDataset = M0SIMSConverter.convertToSIMS(simNumber, namedGraphs, false, false);
		RDFDataMgr.write(new FileOutputStream(fileName), simsDataset, (namedGraphs ? Lang.TRIG : Lang.TURTLE)); // TODO Check if Lang.TRIG is OK for both cases
	}

	/**
	 * Extracts from the M0 dataset and prints to console the mappings between the M0 and target URIs for organizations.
	 */
	@Test
	public void testReadOrganizationURIMappings() {

		SortedMap<String, String> mappings = M0Converter.readOrganizationURIMappings();
		for (String m0URI : mappings.keySet()) System.out.println(m0URI + " - " + mappings.get(m0URI));
	}

	/**
	 * Extracts from the M0 dataset and prints to console the mappings between SIMS numbers and organizational attributes.
	 */
	@Test
	public void testGetOrganizationValues() {
		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<Integer, SortedMap<String, SortedSet<String>>> organizationValues = M0SIMSConverter.getOrganizationValues(m0AssociationModel);

		for (Integer documentationId : organizationValues.keySet()) {
			System.out.println("\nOrganizations related to documentation " + documentationId);
			SortedMap<String, SortedSet<String>> docMappings = organizationValues.get(documentationId);
			for (String attribute : docMappings.keySet())
				System.out.println("\t" + attribute + "\t" + docMappings.get(attribute));
		}
	}
}
