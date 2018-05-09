package fr.insee.semweb.sdmx.metadata.test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.M0Converter;
import fr.insee.semweb.sdmx.metadata.M0Converter.OrganizationRole;
import fr.insee.semweb.sdmx.metadata.M0SIMSConverter;

public class M0ConverterTest {

	@Test
	public void testExtractProperty() throws IOException {

		// Read the source M0 dataset and extract SIMS information
		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0SIMSModel = dataset.getNamedModel(M0Converter.M0_BASE_GRAPH_URI + "documentations");

		Model extract = M0Converter.extractAttributeStatements(m0SIMSModel, "SUMMARY");
		extract.write(new FileOutputStream("src/test/resources/summary-test.ttl"), "TTL");
	}

	@Test
	public void testExtractSeriesProperty() throws IOException {

		// Read the source M0 dataset and extract the model on series
		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0SeriesModel = dataset.getNamedModel(M0Converter.M0_BASE_GRAPH_URI + "series");

		Model extract = M0Converter.extractAttributeStatements(m0SeriesModel, "SUMMARY");
		extract.add(M0Converter.extractAttributeStatements(m0SeriesModel, "ID_DDS"));
		extract.write(new FileOutputStream("src/test/resources/summary-test.ttl"), "TTL");
	}

	@Test
	public void testGetIdURIMappingsSeries() {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Map<Integer, String> mappings = M0Converter.getIdURIFixedMappings(dataset, "serie");

		System.out.println(mappings);
	}

	@Test
	public void testCreateURIMappings() throws IOException {

		Map<String, String> mappings = M0Converter.createURIMappings();
		M0Converter.checkMappings(mappings);
		Files.write(Paths.get("src/test/resources/uri-mappings.txt"), () -> mappings.entrySet().stream().<CharSequence>map(e -> e.getKey() + "\t" + e.getValue()).iterator());
	}

	@Test
	public void testConvert() {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Map<Integer, String> mappings = M0Converter.getIdURIFixedMappings(dataset, "serie");

		System.out.println(mappings);
	}

	@Test
	public void testGetIdURIMappingsOperations() {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Map<Integer, String> mappings = M0Converter.getIdURIFixedMappings(dataset, "operation");

		// "Invert" the map to find duplicates
		Map<String, List<Integer>> inverse = new HashMap<String, List<Integer>>();
		for (int m0Id : mappings.keySet()) {
			if (!inverse.containsKey(mappings.get(m0Id))) inverse.put(mappings.get(m0Id), new ArrayList<Integer>());
			inverse.get(mappings.get(m0Id)).add(m0Id);
		}
		for (String uri : inverse.keySet()) System.out.println(uri + " - " + inverse.get(uri));
	}

	@Test
	public void testExtractCodeLists() throws IOException {

		Model extract = M0Converter.extractCodeLists();
		extract.write(new FileOutputStream("src/test/resources/m0-codes.ttl"), "TTL");
	}

	@Test
	public void testReadAllBaseResources() throws IOException {

		Dataset base = M0Converter.readAllBaseResources("http://rdf.insee.fr/graphes/operations", "http://rdf.insee.fr/graphes/produits");
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/base-resources.trig"), base, Lang.TRIG);
	}

	@Test
	public void testExtractOrganizations() throws IOException {

		Model extract = M0Converter.extractOrganizations();
		extract.write(new FileOutputStream("src/test/resources/m0-organizations.ttl"), "TTL");
	}

	@Test
	public void testExtractOneCodeList() throws IOException {

		Model extract = M0Converter.extractCodeLists();
		Model subExtract = M0SIMSConverter.extractM0ResourceModel(extract, "http://baseUri/codelists/codelist/3");
		subExtract.write(new FileOutputStream("src/test/resources/m0-codelist-3.ttl"), "TTL");
	}

	@Test
	public void testExtractFamilies() throws IOException {

		Model extract = M0Converter.extractFamilies();
		extract.write(new FileOutputStream("src/test/resources/m0-families.ttl"), "TTL");
	}

	@Test
	public void testExtractRelations() throws IOException {

		Map<String, List<String>> relations = M0Converter.extractRelations();
		for (String related : relations.keySet()) System.out.println(related + " is related to " + relations.get(related));
 	}

	@Test
	public void testExtractHierarchies() throws IOException {

		Map<String, String> hierarchies = M0Converter.extractHierarchies();
		for (String child : hierarchies.keySet()) System.out.println(child + " has for parent " + hierarchies.get(child));
		System.out.println(hierarchies.size() + " hierarchies");
 	}

	@Test
	public void testExtractOrganizationalRelations() throws IOException {

		Map<String, List<String>> relations = M0Converter.extractOrganizationalRelations(OrganizationRole.STAKEHOLDER);
		for (String operation : relations.keySet()) System.out.println("operation " + operation + " has for stakeholder(s) " + relations.get(operation));
		System.out.println(relations.size() + " relations");
		relations = M0Converter.extractOrganizationalRelations(OrganizationRole.PRODUCER);
		for (String operation : relations.keySet()) System.out.println("operation " + operation + " has for producers(s) " + relations.get(operation));
		System.out.println(relations.size() + " relations");
 	}

	@Test
	public void testExtractProductionRelations() throws IOException {

		Map<String, List<String>> relations = M0Converter.extractProductionRelations();
		for (String indicator : relations.keySet()) System.out.println("Indicator " + indicator + " produced from " + relations.get(indicator));
		System.out.println(relations.size() + " relations");
 	}

	@Test
	public void testExtractReplacements() throws IOException {

		Map<String, List<String>> replacements = M0Converter.extractReplacements();
		for (String replaces : replacements.keySet()) System.out.println(replacements.get(replaces) + " replaced by " + replaces);
		System.out.println(replacements.size() + " replacements");
 	}

	@Test
	public void testExtractStakeholders() throws IOException {

		Map<String, List<String>> stakeholders = M0Converter.extractOrganizationalRelations(OrganizationRole.STAKEHOLDER);
		for (String operation : stakeholders.keySet()) System.out.println("Operation " + operation + " has stakeholders " + stakeholders.get(operation));
		System.out.println(stakeholders.size() + " operations with stakeholders");
 	}

	@Test
	public void testExtractProducers() throws IOException {

		Map<String, List<String>> producers = M0Converter.extractOrganizationalRelations(OrganizationRole.PRODUCER);
		for (String operation : producers.keySet()) System.out.println("Operation " + operation + " has producers " + producers.get(operation));
		System.out.println(producers.size() + " operations with producers");
 	}

	@Test
	public void testExtractSeries() throws IOException {

		Model extract = M0Converter.extractSeries();
		extract.write(new FileOutputStream("src/test/resources/m0-series.ttl"), "TTL");
	}

	@Test
	public void testExtractOperations() throws IOException {

		Model extract = M0Converter.extractOperations();
		extract.write(new FileOutputStream("src/test/resources/m0-operations.ttl"), "TTL");
	}

	@Test
	public void testExtractIndicators() throws IOException {

		Model extract = M0Converter.extractIndicators();
		extract.write(new FileOutputStream("src/test/resources/m0-indicators.ttl"), "TTL");
	}

	@Test
	public void testExtractAllOperations() throws IOException {

		Model extract = M0Converter.extractAllOperations();
		extract.write(new FileOutputStream("src/test/resources/m0-all-operations.ttl"), "TTL");
	}

	@Test
	public void testConvertToSIMS() throws IOException {

		boolean namedGraphs = false;

		Dataset simsDataset = M0SIMSConverter.convertToSIMS(Arrays.asList(1509), namedGraphs);
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/models/sims-1509" + (namedGraphs ? "trig" : "ttl")), simsDataset, Lang.TRIG); // TODO Check if Lang.TRIG is OK for both cases
	}

	@Test
	public void testExtractSeriesProperty2() throws IOException {

		// Read the source M0 dataset and extract the model on series
		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0SeriesModel = dataset.getNamedModel(M0Converter.M0_BASE_GRAPH_URI + "series");

		Model extract = M0Converter.extractAttributeStatements(m0SeriesModel, "ID_DDS");
		extract.write(new FileOutputStream("src/test/resources/summary-test.ttl"), "TTL");
	}

	@Test
	public void testExtractSIMSAttachments() throws IOException {

		Map<String, String> attachments = M0Converter.extractSIMSAttachments(true);
		for (String simsSet : attachments.keySet()) System.out.println(attachments.get(simsSet) + " has SIMS metadata set " + simsSet);
		System.out.println(attachments.size() + " attachments");
 	}

	@Test
	public void testGetMaxSequence() {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0Converter.M0_BASE_GRAPH_URI + "familles");

		System.out.println(M0Converter.getMaxSequence(m0Model));
	}

	@Test
	public void testListModelAttributes() {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0Converter.M0_BASE_GRAPH_URI + "indicateurs");

		System.out.println(M0Converter.listModelAttributes(m0Model));
	}

	@Test
	public void testReadOrganizationURIMappings() throws IOException {

		M0Converter.readOrganizationURIMappings();
	}

	@Test
	public void testM0SplitAndSave() throws IOException {

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0SIMSModel = dataset.getNamedModel(M0Converter.M0_BASE_GRAPH_URI + "documentations");
		List<String> m0IdList = Arrays.asList("1502", "1508", "1509");
		M0Converter.m0SplitAndSave(m0SIMSModel, m0IdList);
	}

	@Test
	public void testExtractM0ResourceModel() throws IOException {

		// Extracts the 'documentations' model from the dataset
		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0Converter.M0_BASE_GRAPH_URI + "documentations");
		m0Model.write(new FileOutputStream("src/test/resources/m0-extract.ttl"), "TTL");
	}

	@Test
	public void testExtractModel() throws IOException {

		// Extracts a model from the dataset
		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0Converter.M0_BASE_GRAPH_URI + "documentations");
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
