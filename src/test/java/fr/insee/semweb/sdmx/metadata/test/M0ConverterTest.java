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
import java.util.function.Consumer;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.CodelistModelMaker;
import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.M0Checker;
import fr.insee.semweb.sdmx.metadata.M0Converter;
import fr.insee.semweb.sdmx.metadata.M0Extractor;
import fr.insee.semweb.sdmx.metadata.M0SIMSConverter;
import fr.insee.semweb.sdmx.metadata.OrganizationModelMaker;

public class M0ConverterTest {

	/**
	 * Extracts all non-empty values of a SIMS attribute from the M0 model and writes corresponding triples in a file.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testExtractSIMSAttributeValuesM0() throws IOException {

		String simsAttributeName = "DATA_COMP";

		// Read the source M0 dataset and extract SIMS information
		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0SIMSModel = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documentations");

		// Select attribute values and write to file
		Model extract = M0Extractor.extractAttributeStatements(m0SIMSModel, simsAttributeName);
		extract.write(new FileOutputStream("src/test/resources/m0-sims-" + simsAttributeName.toLowerCase() + "-values.ttl"), "TTL");
	}

	/**
	 * Extracts the values of given series properties from the M0 model and writes corresponding triples in a file.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testExtractSeriesProperties() throws IOException {

		List<String> propertyNames = Arrays.asList("SUMMARY", "ID_DDS");

		// Read the source M0 dataset and extract the model on series
		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0SeriesModel = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "series");

		// Select values for specified properties and write to file
		Model extract = ModelFactory.createDefaultModel();
		String fileName = "src/test/resources/m0-series";
		for (String propertyName : propertyNames) {
			extract.add(M0Extractor.extractAttributeStatements(m0SeriesModel, propertyName));
			fileName += "-" + propertyName.toLowerCase();
		}
		extract.write(new FileOutputStream(fileName + "-values.ttl"), "TTL");
	}

	/**
	 * Reads the list of fixed mappings between M0 identifiers and target URIs for series and saves it to a file.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testGetIdURIFixedMappingsSeries() throws IOException {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Map<Integer, String> mappings = M0Converter.getIdURIFixedMappings(dataset, "serie");

		try (PrintWriter writer = new PrintWriter("src/test/resources/mappings-id-uri-series.txt", "UTF-8")) {
			mappings.entrySet().stream().sorted(Map.Entry.<Integer, String>comparingByKey()).forEach(writer::println);
		}
	}

	/**
	 * Reads the list of fixed mappings between M0 identifiers and target URIs for operations and saves it to a file.
	 * Also inverts the mappings in order to check if there are no duplicates and saves the inverted list to a file.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testGetIdURIFixedMappingsOperations() throws IOException {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Map<Integer, String> mappings = M0Converter.getIdURIFixedMappings(dataset, "operation");

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
	 * @throws IOException
	 */
	@Test
	public void testCreateURIMappings() throws IOException {

		Map<String, String> mappings = M0Converter.createURIMappings();
		M0Converter.checkMappings(mappings);
		Files.write(Paths.get("src/test/resources/mappings-uri.txt"), () -> mappings.entrySet().stream().<CharSequence>map(e -> e.getKey() + "\t" + e.getValue()).iterator());
	}

	/**
	 * Extracts the code lists defined in the M0 model and saves them in a Turtle file.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testConvertM0CodeLists() throws IOException {

		Model extract = M0Converter.convertCodeLists();
		extract.write(new FileOutputStream("src/test/resources/m0-codelists.ttl"), "TTL");
	}

	/**
	 * Creates a RDF dataset containing all families, series, operations and indicators in the target model and saves it to a file.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testConvertAllOperationsAndIndicators() throws IOException {

		Dataset base = M0Converter.convertAllOperationsAndIndicators("http://rdf.insee.fr/graphes/operations", "http://rdf.insee.fr/graphes/produits");
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/all-operations-and-indicators.trig"), base, Lang.TRIG);
	}

	/**
	 * Creates a RDF dataset containing all base resources (code lists, organizations, families, series, operations and indicators) in the target model and saves it to a file.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testConvertAllBaseResources() throws Exception {

		// Code lists from the Excel file
		Dataset dataset = CodelistModelMaker.readCodelistDataset(new File(Configuration.CL_XLSX_FILE_NAME), "http://rdf.insee.fr/graphes/concepts", "http://rdf.insee.fr/graphes/codes");
		// Families, series, operations converted from the M0 model
		dataset.addNamedModel("http://rdf.insee.fr/graphes/operations", M0Converter.convertAllOperations());
		// Indicators converted from the M0 model
		dataset.addNamedModel("http://rdf.insee.fr/graphes/produits", M0Converter.convertIndicators());
		// Organizations from the Excel file
		Workbook orgWorkbook = WorkbookFactory.create(new File(Configuration.ORGANIZATIONS_XLSX_FILE_NAME));
		Model orgModel = OrganizationModelMaker.createSSMModel(orgWorkbook);
		orgModel.add(OrganizationModelMaker.createInseeModel(orgWorkbook));
		dataset.addNamedModel("http://rdf.insee.fr/graphes/organisations", orgModel);
		
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/all-base-resources.trig"), dataset, Lang.TRIG);
	}

	/**
	 * Creates and writes to a file the information about families, series and operations in the target model.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testConvertAllOperations() throws IOException {

		Model extract = M0Converter.convertAllOperations();
		extract.write(new FileOutputStream("src/test/resources/all-operations.ttl"), "TTL");
	}

	/**
	 * Creates and writes to a file the information about families in the target model.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testConvertFamilies() throws IOException {

		Model extract = M0Converter.convertFamilies();
		extract.write(new FileOutputStream("src/test/resources/families.ttl"), "TTL");
	}

	/**
	 * Creates and writes to a file the information about indicators in the target model.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testConvertIndicators() throws IOException {

		Model extract = M0Converter.convertIndicators();
		extract.write(new FileOutputStream("src/test/resources/indicators.ttl"), "TTL");
	}

	/**
	 * Creates and writes to a file the information about operations in the target model.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testConvertOperations() throws IOException {

		Model extract = M0Converter.convertOperations();
		extract.write(new FileOutputStream("src/test/resources/operations.ttl"), "TTL");
	}

	/**
	 * Creates and writes to a file the information about organizations in the target ORG model.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testConvertOrganizations() throws IOException {

		Model extract = M0Converter.convertOrganizations();
		extract.write(new FileOutputStream("src/test/resources/organizations.ttl"), "TTL");
	}

	/**
	 * Creates and writes to a file the information about series in the target model.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testConvertSeries() throws IOException {

		Model extract = M0Converter.convertSeries();
		extract.write(new FileOutputStream("src/test/resources/series.ttl"), "TTL");
	}

	@Test
	public void testExtractDocuments() throws IOException {

		Model extract = M0SIMSConverter.convertDocumentsToSIMS();
		extract.write(new FileOutputStream("src/main/resources/data/documents.ttl"), "TTL");
	}

	@Test
	public void testGetFamilyThemesRelations() {

		Map<String, List<String>> relations = M0Converter.getFamilyThemesRelations();
		for (String family : relations.keySet()) System.out.println(family + " has theme(s) " + relations.get(family));
	}

	@Test
	public void testExtractRelations() throws IOException {

		Map<String, List<String>> relations = M0Converter.extractRelations();
		for (String related : relations.keySet()) System.out.println(related + " is related to " + relations.get(related));
 	}

	@Test
	public void testExtractAttributeLinks() throws IOException {

		// We also list the links that are actually referenced in the relations
		SortedSet<String> referencedLinks = new TreeSet<String>();
		SortedMap<Integer, SortedMap<String, SortedSet<String>>> relations = M0SIMSConverter.extractAttributeReferences("fr", true);
		for (Integer documentationId : relations.keySet()) {
			System.out.println("Documentation " + documentationId + " is associated to the following French links: " + relations.get(documentationId));
			for (String attributeName : relations.get(documentationId).keySet()) referencedLinks.addAll(relations.get(documentationId).get(attributeName));
		}
		relations = M0SIMSConverter.extractAttributeReferences("en", true);
		for (Integer documentationId : relations.keySet()) {
			System.out.println("Documentation " + documentationId + " is associated to the following English links: " + relations.get(documentationId));
			for (String attributeName : relations.get(documentationId).keySet()) referencedLinks.addAll(relations.get(documentationId).get(attributeName));
		}
		System.out.println("The following links are referenced in the associations " + referencedLinks);
 	}

	@Test
	public void testExtractAttributeDocuments() throws IOException {

		// We also list the links that are actually referenced in the relations
		SortedSet<String> referencedLinks = new TreeSet<String>();
		SortedMap<Integer, SortedMap<String, SortedSet<String>>> relations = M0SIMSConverter.extractAttributeReferences("fr", false);
		for (Integer documentationId : relations.keySet()) {
			System.out.println("Documentation " + documentationId + " is associated to the following French documents: " + relations.get(documentationId));
			for (String attributeName : relations.get(documentationId).keySet()) referencedLinks.addAll(relations.get(documentationId).get(attributeName));
		}
		relations = M0SIMSConverter.extractAttributeReferences("en", false);
		for (Integer documentationId : relations.keySet()) {
			System.out.println("Documentation " + documentationId + " is associated to the following English documents: " + relations.get(documentationId));
			for (String attributeName : relations.get(documentationId).keySet()) referencedLinks.addAll(relations.get(documentationId).get(attributeName));
		}
		System.out.println("The following documents are referenced in the associations " + referencedLinks);
 	}

	@Test
	public void testGetLanguageTags() {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<Integer, String> languageTags = M0SIMSConverter.getLanguageTags(m0AssociationModel, true);
		System.out.println("Results for links:");
		for (Integer linkId : languageTags.keySet()) System.out.println("Link " + linkId + " is tagged with language " + languageTags.get(linkId));
		languageTags = M0SIMSConverter.getLanguageTags(m0AssociationModel, false);
		System.out.println("\nResults for documents:");
		for (Integer linkId : languageTags.keySet()) System.out.println("Document " + linkId + " is tagged with language " + languageTags.get(linkId));
	}

	/**
	 * Creates and writes to a file the information about external links of all SIMS in the target model.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testConvertLinksToSIMS() throws IOException {

		Model linksModel = M0SIMSConverter.convertLinksToSIMS();
		linksModel.write(new FileOutputStream("src/main/resources/data/sims-links.ttl"), "TTL");
	}

	@Test
	public void testConvertDocumentsToSIMS() throws IOException {

		Model linksModel = M0SIMSConverter.convertDocumentsToSIMS();
		linksModel.write(new FileOutputStream("src/test/resources/sims-documents.ttl"), "TTL");
	}

	@Test
	public void testGetDocumentDates() {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documents");

		SortedMap<Integer, Date> documentDates = M0SIMSConverter.getDocumentDates(m0Model);
		for (Integer documentNumber : documentDates.keySet()) System.out.println(documentNumber + "\t" + documentDates.get(documentNumber));
	}

	@Test
	public void testConvertAllToSIMS() throws IOException {

		boolean namedGraphs = true;

		Dataset simsDataset = M0SIMSConverter.convertToSIMS(null, namedGraphs);
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/models/sims-all." + (namedGraphs ? "trig" : "ttl")), simsDataset, Lang.TRIG); // TODO Check if Lang.TRIG is OK for both cases
	}

	@Test
	public void testConvertListToSIMS() throws IOException {

		boolean namedGraphs = true;

		Dataset simsDataset = M0SIMSConverter.convertToSIMS(Arrays.asList(1501, 1508), namedGraphs);
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/models/sims-1501." + (namedGraphs ? "trig" : "ttl")), simsDataset, Lang.TRIG); // TODO Check if Lang.TRIG is OK for both cases
	}

	@Test
	public void testConvertOneToSIMS() throws IOException {

		boolean namedGraphs = false;

		Dataset simsDataset = M0SIMSConverter.convertToSIMS(Arrays.asList(1501), namedGraphs);
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/models/sims-1501." + (namedGraphs ? "trig" : "ttl")), simsDataset, Lang.TRIG); // TODO Check if Lang.TRIG is OK for both cases
	}

	@Test
	public void testExtractSIMSAttachments() throws IOException {

		Map<String, String> attachments = M0SIMSConverter.extractSIMSAttachments(true);
		for (String simsSet : attachments.keySet()) System.out.println(attachments.get(simsSet) + " has SIMS metadata set " + simsSet);
		System.out.println(attachments.size() + " attachments");
 	}

	@Test
	public void testGetMaxSequence() {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "familles");

		System.out.println(M0Extractor.getMaxSequence(m0Model));
	}

	@Test
	public void testListModelAttributes() {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "indicateurs");

		System.out.println(M0Checker.listModelAttributes(m0Model));
	}

	@Test
	public void testReadOrganizationURIMappings() throws IOException {

		M0Converter.readOrganizationURIMappings();
	}

	@Test
	public void testM0SplitAndSave() throws IOException {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0SIMSModel = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documentations");
		List<String> m0IdList = Arrays.asList("1502", "1508", "1509");
		M0Extractor.m0SplitAndSave(m0SIMSModel, m0IdList);
	}

	@Test
	public void testExtractM0ResourceModel() throws IOException {

		// Extracts the 'documentations' model from the dataset
		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documentations");
		m0Model.write(new FileOutputStream("src/test/resources/m0-extract.ttl"), "TTL");
	}

	@Test
	public void testExtractModel() throws IOException {

		// Extracts a model from the dataset
		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documentations");
		m0Model.write(new FileOutputStream("src/test/resources/m0-extract-documentations.ttl"), "TTL");
	}

	@Test
	public void testListModel() throws IOException {
	
		// List the names of the models in the dataset
		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		dataset.listNames().forEachRemaining(new Consumer<String>() {
			@Override
			public void accept(String name) {
				System.out.println(name);}
		});
	}
}
