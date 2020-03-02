package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.utils.Utils;

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
	public void testCamelCase1() {
		assertEquals(Utils.camelCase("How about that", true, false), "howAboutThat");
		assertEquals(Utils.camelCase("How about that", true, true), "howsAboutThat");
		assertEquals(Utils.camelCase("A  B C dF edd", true, false), "aBCDfEdd");
		assertNull(Utils.camelCase(null, true, true));
	}

	@Test
	public void testCamelCase2() {
		assertEquals(Utils.camelCase("Type de source", true, true), "typesSource");
		assertEquals(Utils.camelCase("Type de source", true, false), "typeSource");
		assertEquals(Utils.camelCase("Type de source", false, true), "TypesSource");
		assertEquals(Utils.camelCase("Type de source", false, false), "TypeSource");
		assertEquals(Utils.camelCase("Unité enquêtée", true, true), "unitesEnquetees");		
		assertEquals(Utils.camelCase("Unité enquêtée", true, false), "uniteEnquetee");		
	}

	@Test
	public void testCamelCase3() {
		assertEquals(Utils.camelCase("Frequence", true, true), "frequences");
		assertEquals(Utils.camelCase("Statut de l'enquête", true, false), "statutEnquete");
		assertEquals(Utils.camelCase("Statut de l'enquête", false, true), "StatutsEnquete");
		assertEquals(Utils.camelCase("Catégorie de source", false, true), "CategoriesSource");
	}

	@Test
	public void testIdMapping() {
		System.out.println(Configuration.ddsToWeb4GIdMappings);
	}
}
