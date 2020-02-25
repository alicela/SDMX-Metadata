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
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
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
	public void testConvertM0CodeLists() throws IOException {

		Model m0CodeListsModel = M0Converter.convertCodeLists();
		m0CodeListsModel.write(new FileOutputStream("src/test/resources/m0-codelists.ttl"), "TTL");
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
	public void testGetDocumentDates() {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentsModel = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documents");

		SortedMap<Integer, Date> documentDates = M0SIMSConverter.getDocumentDates(m0DocumentsModel);
		for (Integer documentNumber : documentDates.keySet()) System.out.println(documentNumber + "\t" + documentDates.get(documentNumber));
	}

	/**
	 * Converts all SIMS to the target model and writes the result as a TriG or Turtle file.
	 * 
	 * @throws IOException In case of problems while writing the output file.
	 */
	@Test
	public void testConvertAllToSIMS() throws IOException {

		boolean namedGraphs = true;

		Dataset simsDataset = M0SIMSConverter.convertToSIMS(null, namedGraphs);
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
		Dataset simsDataset = M0SIMSConverter.convertToSIMS(simsNumbers, namedGraphs);
		RDFDataMgr.write(new FileOutputStream(fileName), simsDataset, (namedGraphs ? Lang.TRIG : Lang.TURTLE));
	}

	/**
	 * Converts one SIMS to the target model and writes the result as a TriG or Turtle file.
	 * 
	 * @throws IOException In case of problems while writing the output file.
	 */
	@Test
	public void testConvertOneToSIMS() throws IOException {

		boolean namedGraphs = false;
		List<Integer> simNumber = Arrays.asList(1501);

		Dataset simsDataset = M0SIMSConverter.convertToSIMS(simNumber, namedGraphs);
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/models/sims-1501." + (namedGraphs ? "trig" : "ttl")), simsDataset, (namedGraphs ? Lang.TRIG : Lang.TURTLE)); // TODO Check if Lang.TRIG is OK for both cases
	}

	@Test
	public void testReadOrganizationURIMappings() throws IOException {

		M0Converter.readOrganizationURIMappings();
	}

}
