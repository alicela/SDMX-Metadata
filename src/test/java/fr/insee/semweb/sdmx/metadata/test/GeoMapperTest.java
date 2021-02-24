package fr.insee.semweb.sdmx.metadata.test;

import java.util.SortedMap;
import java.util.SortedSet;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.GeoMapper;

class GeoMapperTest {

	/**
	 * Gets the mappings between target territory names and URIs and prints it to the console.
	 */
	@Test
	public void testQueryNameURIMappings() {

		SortedMap<String, String> labelURIMappings = GeoMapper.queryNameURIMappings();
		for (String name : labelURIMappings.keySet()) System.out.println(name + " - " + labelURIMappings.get(name));
	}

	/**
	 * Gets the mappings between the M0 names and codes of territories and prints it to the console.
	 */
	@Test
	public void testCreateM0GeoNameCodeMappings() {

		SortedMap<String, String> labelURIMappings = GeoMapper.createM0GeoNameCodeMappings();
		for (String name : labelURIMappings.keySet()) System.out.println(name + " - " + labelURIMappings.get(name));
	}

	/**
	 * Gets the list of area codes actually used in the SIMS and prints it to the console.
	 */
	@Test
	void testGetUsedAreaCodes() {

		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documentations");

		SortedSet<String> usedAreaCodes = GeoMapper.getUsedAreaCodes(m0DocumentationsModel);
		System.out.println(usedAreaCodes);
		m0DocumentationsModel.close();
		m0Dataset.close();
		// TODO Remove 'OTHER'
	}

	/**
	 * Gets the list of M0 documentations statements for which REF_AREA value is 'OTHER' and prints it to the console.
	 */
	@Test
	public void testGetDocumentationsWithOherAreaCode() {
		
		Dataset m0Dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documentations");

		SortedSet<String> documentationsWithOherAreaCode = GeoMapper.getDocumentationsWithOherAreaCode(m0DocumentationsModel);
		for (String statement : documentationsWithOherAreaCode) System.out.println(statement);
		m0DocumentationsModel.close();
		m0Dataset.close();
	}
}
