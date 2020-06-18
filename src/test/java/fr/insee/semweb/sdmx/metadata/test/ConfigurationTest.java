package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;

/**
 * Test and launch methods for class <code>Configuration</code>.
 * 
 * @author Franck
 */
public class ConfigurationTest {

	@Test
	public void testCodeListNameToConceptName() {
		assertEquals(Configuration.codeListNameToConceptName("CL_FREQ"), "Freq");
		assertEquals(Configuration.codeListNameToConceptName("CL_UNIT_MEASURE"), "UnitMeasure");
		assertEquals(Configuration.codeListNameToConceptName("CL_COLLECTION_MODE"), "CollectionMode");
		assertEquals(Configuration.codeListNameToConceptName("cl_collection_mode"), "CollectionMode");
		assertEquals(Configuration.codeListNameToConceptName("CL_SURVEY_UNIT"), "SurveyUnit");
	}

	@Test
	public void testSDMXCodeConceptURI() {
		assertEquals(Configuration.sdmxCodeConceptURI("CL_FREQ"), "http://purl.org/linked-data/sdmx/2009/code#Freq");
		assertEquals(Configuration.sdmxCodeConceptURI("CL_REF_AREA"), "http://purl.org/linked-data/sdmx/2009/code#Area");
		assertEquals(Configuration.sdmxCodeConceptURI("cl_ref_area"), "http://purl.org/linked-data/sdmx/2009/code#Area");
		assertEquals(Configuration.sdmxCodeConceptURI("CL_UNIT_MEASURE"), "http://purl.org/linked-data/sdmx/2009/code#UnitMeasure");
	}

	@Test
	public void testIdMapping() {
		System.out.println(Configuration.ddsToWeb4GIdMappings);
	}

	@Test
	public void testGetOrganizationURI() {

		assertEquals(Configuration.organizationURI("banque-de-france"), "http://id.insee.fr/organisations/banque-de-france");
	}
}
