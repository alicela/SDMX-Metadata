package fr.insee.semweb.sdmx.metadata.test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.Configuration.OrganizationRole;
import fr.insee.semweb.sdmx.metadata.M0Extractor;

/**
 * Test and launch methods for class <code>M0Extractor</code>.
 * 
 * @author Franck
 */
class M0ExtractorTest {

	/**
	 * Extracts from the M0 dataset the list of all relations between operation-like resources, and saves it to a file.
	 * 
	 * @throws IOException In case of problems while creating the output file.
	 */
	@Test
	public void testExtractRelations() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<String, List<String>> relations = M0Extractor.extractRelations(m0AssociationModel);
		for (String related : relations.keySet()) System.out.println(related + " is related to " + relations.get(related));
		m0AssociationModel.close();
		m0Dataset.close();
		try (PrintWriter writer = new PrintWriter("src/test/resources/m0-relations.txt", "UTF-8")) {
			relations.entrySet().stream().forEach(writer::println);
		}
 	}

	/**
	 * Extracts from the M0 dataset the list of hierarchical relations between families, series and operations, and saves it to a file.
	 * 
	 * @throws IOException In case of problems while creating the output file.
	 */
	@Test
	public void testExtractHierarchies() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<String, String> hierarchies = M0Extractor.extractHierarchies(m0AssociationModel);
		m0AssociationModel.close();
		m0Dataset.close();
		try (PrintWriter writer = new PrintWriter("src/test/resources/m0-child-parent-relations.txt", "UTF-8")) {
			hierarchies.entrySet().stream().forEach(writer::println);
		}
 	}

	/**
	 * Extracts from the M0 dataset the list of production relations between series/operations and indicators and saves it to a file.
	 * 
	 * @throws IOException In case of problems while creating the output file.
	 */
	@Test
	public void testExtractProductionRelations() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<String, List<String>> relations = M0Extractor.extractProductionRelations(m0AssociationModel);
		m0AssociationModel.close();
		m0Dataset.close();
		try (PrintWriter writer = new PrintWriter("src/test/resources/m0-production-relations.txt", "UTF-8")) {
			relations.entrySet().stream().forEach(writer::println);
		}
	}

	/**
	 * Extracts from the M0 dataset the list of replacement relations between series/operations or indicators and saves it to a file.
	 * 
	 * @throws IOException In case of problems while creating the output file.
	 */
	@Test
	public void testExtractReplacements() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<String, List<String>> replacements = M0Extractor.extractReplacements(m0AssociationModel);
		m0AssociationModel.close();
		m0Dataset.close();
		try (PrintWriter writer = new PrintWriter("src/test/resources/m0-replacement-relations.txt", "UTF-8")) {
			replacements.entrySet().stream().forEach(writer::println);
		}
 	}

	/**
	 * Extracts from the M0 dataset the list of replacement relations between series or indicators and saves it to a file.
	 * 
	 * @throws IOException In case of problems while creating the output file.
	 */
	@Test
	public void testExtractReplacementRelations() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<String, List<String>> relations = M0Extractor.extractProductionRelations(m0AssociationModel);
		m0AssociationModel.close();
		m0Dataset.close();
		try (PrintWriter writer = new PrintWriter("src/test/resources/m0-replacement-relations.txt", "UTF-8")) {
			relations.entrySet().stream().forEach(writer::println);
		}
	}

	/**
	 * Extracts from the M0 dataset the lists of organizational relations between operations and organizations for each role and saves them to files.
	 * 
	 * @throws IOException In case of problems while creating the output file.
	 */
	@Test
	public void testExtractOrganizationalRelations() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<String, List<String>> organizationMappings = M0Extractor.extractOrganizationalRelations(m0AssociationModel, OrganizationRole.PRODUCER);
		try (PrintWriter writer = new PrintWriter("src/test/resources/m0-producer-relations.txt", "UTF-8")) {
			organizationMappings.entrySet().stream().sorted(Map.Entry.<String, List<String>>comparingByKey()).forEach(writer::println);
		}
		organizationMappings = M0Extractor.extractOrganizationalRelations(m0AssociationModel, OrganizationRole.STAKEHOLDER);
		try (PrintWriter writer = new PrintWriter("src/test/resources/m0-stakeholder-relations.txt", "UTF-8")) {
			organizationMappings.entrySet().stream().forEach(writer::println);
		}
		m0AssociationModel.close();
		m0Dataset.close();
	}

	/**
	 * Extracts from the M0 dataset the lists of attachment relations between series/operations and documentations and saves it to files.
	 * 
	 * @throws IOException In case of problems while creating the output file.
	 */
	@Test
	public void testExtractSIMSAttachments() throws IOException {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<String, String> attachments = M0Extractor.extractSIMSAttachments(m0AssociationModel, true);
		m0AssociationModel.close();
		m0Dataset.close();
		try (PrintWriter writer = new PrintWriter("src/test/resources/m0-attachment-relations.txt", "UTF-8")) {
			attachments.entrySet().stream().forEach(writer::println);
		}
 	}

	/**
	 * Extracts all non-empty values of a given SIMS attribute from the M0 model and writes corresponding triples in a file.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testExtractSIMSAttributeValues() throws IOException {

		String simsAttributeName = "DATA_COMP";

		// Read the source M0 dataset and extract SIMS information
		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0SIMSModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documentations");

		// Select attribute values and write to file
		Model m0AttributeModel = M0Extractor.extractAttributeStatements(m0SIMSModel, simsAttributeName);
		m0AttributeModel.write(new FileOutputStream("src/test/resources/m0-sims-" + simsAttributeName.toLowerCase() + "-values.ttl"), "TTL");

		// Close resources
		m0SIMSModel.close();
		m0AttributeModel.close();
		m0Dataset.close();
	}

	/**
	 * Extracts the values of given series properties from the M0 model and writes corresponding triples in a file.
	 * 
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testExtractSeriesPropertyValues() throws IOException {

		List<String> propertyNames = Arrays.asList("SUMMARY", "ID_DDS");

		// Read the source M0 dataset and extract the model on series
		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0SeriesModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "series");

		// Select values for specified properties and write to file
		Model extract = ModelFactory.createDefaultModel();
		String fileName = "src/test/resources/m0-series";
		for (String propertyName : propertyNames) {
			extract.add(M0Extractor.extractAttributeStatements(m0SeriesModel, propertyName));
			fileName += "-" + propertyName.toLowerCase();
		}
		extract.write(new FileOutputStream(fileName + "-values.ttl"), "TTL");

		// Close resources
		m0SeriesModel.close();
		extract.close();
		m0Dataset.close();
	}

	/**
	 * Reads and writes to console the maximum sequence number for an M0 model.
	 */
	@Test
	public void testGetMaxSequence() {

		String m0ModelName = "familles"; // Can be replaced by any other M0 model name
		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + m0ModelName);

		System.out.println("Max sequence number for model " + m0ModelName + " is " + M0Extractor.getMaxSequence(m0Model));
	}
}
