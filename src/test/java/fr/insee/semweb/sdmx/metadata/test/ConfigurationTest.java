package fr.insee.semweb.sdmx.metadata.test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.SIMSFrEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test and launch methods for class <code>Configuration</code>.
 * 
 * @author Franck
 */
public class ConfigurationTest {

	@Test
	public void testInseeCodeURI() {
		assertEquals(Configuration.inseeCodeURI("A", "Catégorie de source"), "http://id.insee.fr/codes/categorieSource/A");
		assertEquals(Configuration.inseeCodeURI("Q", "Frequence"), "http://id.insee.fr/codes/frequence/Q");
		assertEquals(Configuration.inseeCodeURI("M", "ModeCollecte"), "http://id.insee.fr/codes/modeCollecte/M");
		assertEquals(Configuration.inseeCodeURI("L", "UniteEnquetee"), "http://id.insee.fr/codes/uniteEnquetee/L");
	}

	@Test
	public void testSIMSFrRichTextURI() {

		SIMSFrEntry testEntry = new SIMSFrEntry("S.3.3");
		assertEquals(Configuration.simsFrRichTextURI("1234", testEntry, "fr"), "http://id.insee.fr/qualite/attribut/1234/S.3.3/texte");
		assertEquals(Configuration.simsFrRichTextURI("1234", testEntry, "en"), "http://id.insee.fr/qualite/attribut/1234/S.3.3/text");
	}

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

	@Test
	public void testIsInseeOrganization() {
		assertTrue(Configuration.isInseeOrganization("DG75-D001"));
		assertTrue(Configuration.isInseeOrganization("C520"));
		assertFalse(Configuration.isInseeOrganization("Drees"));		
	}
}
