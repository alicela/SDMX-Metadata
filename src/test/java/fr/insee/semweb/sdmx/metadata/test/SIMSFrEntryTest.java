package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.*;

import org.apache.jena.vocabulary.ORG;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.SIMSFrEntry;
import fr.insee.stamina.utils.DQV;

public class SIMSFrEntryTest {

	@Test
	public void testGetRangeSIMS() {

		SIMSFrEntry testEntry = new SIMSFrEntry("TEST");

		testEntry.setRepresentation("Text");
		assertEquals(testEntry.getRange(true), XSD.xstring);
		testEntry.setRepresentation("e-mail");
		assertEquals(testEntry.getRange(true), XSD.xstring);
		testEntry.setRepresentation("Telephone");
		assertEquals(testEntry.getRange(true), XSD.xstring);
		testEntry.setRepresentation("Fax");
		assertEquals(testEntry.getRange(true), XSD.xstring);
		testEntry.setRepresentation("Fax");
		assertEquals(testEntry.getRange(true), XSD.xstring);
		testEntry.setRepresentation("Text/Coded (code list: CL_REF_AREA) ");
		assertEquals(testEntry.getRange(true).getURI(), "http://purl.org/linked-data/sdmx/2009/code#Area");
		testEntry.setRepresentation("Text/Coded (code list: CL_UNIT_MEASURE)");
		assertEquals(testEntry.getRange(true).getURI(), "http://purl.org/linked-data/sdmx/2009/code#UnitMeasure");
		testEntry.setRepresentation("Text / Coded (code list: CL_FREQ)");
		assertEquals(testEntry.getRange(true).getURI(), "http://purl.org/linked-data/sdmx/2009/code#Freq");
		testEntry.setRepresentation("Quality indicator ");
		assertEquals(testEntry.getRange(true), DQV.Metric);
	}

	@Test
	public void testGetRangeSIMSFr() {

		SIMSFrEntry testEntry = new SIMSFrEntry("TEST");

		testEntry.setInseeRepresentation("Text");
		testEntry.setOrigin("Ajouté"); // To avoid funny effects
		assertEquals(testEntry.getRange(false), XSD.xstring);
		testEntry.setInseeRepresentation(null);
		assertEquals(testEntry.getRange(false).getURI(), Configuration.SDMX_MM_BASE_URI + "ReportedAttribute");
		testEntry.setInseeRepresentation("Text (max length ??)");
		assertEquals(testEntry.getRange(false), XSD.xstring);
		testEntry.setInseeRepresentation("Rich text");
		assertEquals(testEntry.getRange(false), XSD.xstring);
		testEntry.setInseeRepresentation("Expression régulière (ex : 2016X066EC)");
		assertEquals(testEntry.getRange(false), XSD.xstring);
		testEntry.setInseeRepresentation("Code list : CL_SOURCE_CATEGORY");
		assertEquals(testEntry.getRange(false).getURI(), Configuration.codeConceptURI("CL_SOURCE_CATEGORY"));
		testEntry.setInseeRepresentation("Code list : CL_COLLECTION_MODE ou texte");
		assertEquals(testEntry.getRange(false).getURI(), Configuration.codeConceptURI("CL_COLLECTION_MODE"));
		testEntry.setInseeRepresentation("Code list : CL_AREA\n\nNommée différemment parce que je ne sais pas si ça correspond tout à fait à la liste définie en SDMX");
		assertEquals(testEntry.getRange(false).getURI(), Configuration.codeConceptURI("CL_AREA"));
		testEntry.setInseeRepresentation("Code list : Liste Insee+Liste Liste des SSM + ajouts possibles");
		assertEquals(testEntry.getRange(false), ORG.Organization);
		testEntry.setInseeRepresentation("Lien (0,n)");
		assertEquals(testEntry.getRange(false), RDFS.Resource); // TODO Should rather be a kind of StatisticalOperation or StatisticalActivity
		testEntry.setInseeRepresentation("Rich text + other material (0,n) : label+URI/URL");
		assertEquals(testEntry.getRange(false), RDFS.Resource);
	}

}
