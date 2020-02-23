package fr.insee.semweb.sdmx.metadata.test;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
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

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<String, List<String>> relations = M0Extractor.extractRelations(m0AssociationModel);
		for (String related : relations.keySet()) System.out.println(related + " is related to " + relations.get(related));
		m0AssociationModel.close();
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

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<String, String> hierarchies = M0Extractor.extractHierarchies(m0AssociationModel);
		m0AssociationModel.close();
		try (PrintWriter writer = new PrintWriter("src/test/resources/m0-child-parent-relations.txt", "UTF-8")) {
			hierarchies.entrySet().stream().forEach(writer::println);
		}
 	}

	/**
	 * Extracts from the M0 dataset the list of relations between series/operations and indicators and saves it to a file.
	 * 
	 * @throws IOException In case of problems while creating the output file.
	 */
	@Test
	public void testExtractProductionRelations() throws IOException {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<String, List<String>> relations = M0Extractor.extractProductionRelations(m0AssociationModel);
		m0AssociationModel.close();
		try (PrintWriter writer = new PrintWriter("src/test/resources/m0-production-relations.txt", "UTF-8")) {
			relations.entrySet().stream().forEach(writer::println);
		}
	}

	@Test
	public void testExtractReplacements() throws IOException {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<String, List<String>> replacements = M0Extractor.extractReplacements(m0AssociationModel);
		m0AssociationModel.close();
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

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<String, List<String>> relations = M0Extractor.extractProductionRelations(m0AssociationModel);
		m0AssociationModel.close();
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

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0AssociationModel = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		SortedMap<String, List<String>> organizationMappings = M0Extractor.extractOrganizationalRelations(m0AssociationModel, OrganizationRole.PRODUCER);
		try (PrintWriter writer = new PrintWriter("src/test/resources/m0-producer-relations.txt", "UTF-8")) {
			organizationMappings.entrySet().stream().sorted(Map.Entry.<String, List<String>>comparingByKey()).forEach(writer::println);
		}
		organizationMappings = M0Extractor.extractOrganizationalRelations(m0AssociationModel, OrganizationRole.STAKEHOLDER);
		try (PrintWriter writer = new PrintWriter("src/test/resources/m0-stakeholder-relations.txt", "UTF-8")) {
			organizationMappings.entrySet().stream().forEach(writer::println);
		}
		m0AssociationModel.close();
	}

}
