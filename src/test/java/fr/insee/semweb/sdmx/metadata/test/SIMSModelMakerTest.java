package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.apache.jena.vocabulary.ORG;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.SIMSModelMaker;
import fr.insee.semweb.sdmx.metadata.SIMSFrEntry;
import fr.insee.semweb.sdmx.metadata.SIMSFrScheme;
import fr.insee.stamina.utils.DQV;

public class SIMSModelMakerTest {

	@Test
	public void testReadSIMSFromExcel() {

		SIMSModelMaker.readSIMSFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME), false);
	}

	@Test
	public void testReadSIMSPlusFromExcel() {

		SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
	}

	@Test
	public void testReadSDMXModel() {

		SIMSModelMaker.readSDMXModel(Configuration.SDMX_MM_TURTLE_FILE_NAME, true);
	}

	@Test
	public void testGetRangeSIMS() {

		SIMSFrEntry testEntry = new SIMSFrEntry("TEST");

		testEntry.setRepresentation("Text");
		assertEquals(SIMSModelMaker.getRange(testEntry, true), XSD.xstring);
		testEntry.setRepresentation("e-mail");
		assertEquals(SIMSModelMaker.getRange(testEntry, true), XSD.xstring);
		testEntry.setRepresentation("Telephone");
		assertEquals(SIMSModelMaker.getRange(testEntry, true), XSD.xstring);
		testEntry.setRepresentation("Fax");
		assertEquals(SIMSModelMaker.getRange(testEntry, true), XSD.xstring);
		testEntry.setRepresentation("Fax");
		assertEquals(SIMSModelMaker.getRange(testEntry, true), XSD.xstring);
		testEntry.setRepresentation("Text/Coded (code list: CL_REF_AREA) ");
		assertEquals(SIMSModelMaker.getRange(testEntry, true).getURI(), "http://purl.org/linked-data/sdmx/2009/code#Area");
		testEntry.setRepresentation("Text/Coded (code list: CL_UNIT_MEASURE)");
		assertEquals(SIMSModelMaker.getRange(testEntry, true).getURI(), "http://purl.org/linked-data/sdmx/2009/code#UnitMeasure");
		testEntry.setRepresentation("Text / Coded (code list: CL_FREQ)");
		assertEquals(SIMSModelMaker.getRange(testEntry, true).getURI(), "http://purl.org/linked-data/sdmx/2009/code#Freq");
		testEntry.setRepresentation("Quality indicator ");
		assertEquals(SIMSModelMaker.getRange(testEntry, true), DQV.QualityMeasurement);
	}

	@Test
	public void testGetRangeSIMSPlus() {

		SIMSFrEntry testEntry = new SIMSFrEntry("TEST");

		testEntry.setInseeRepresentation("Text");
		assertEquals(SIMSModelMaker.getRange(testEntry, false), XSD.xstring);

		testEntry.setInseeRepresentation("ou texte");
		assertEquals(SIMSModelMaker.getRange(testEntry, false).getURI(), Configuration.SDMX_MM_BASE_URI + "ReportedAttribute");

		testEntry.setInseeRepresentation("Text (max length ??)");
		assertEquals(SIMSModelMaker.getRange(testEntry, false), XSD.xstring);

		testEntry.setInseeRepresentation("Rich text");
		assertEquals(SIMSModelMaker.getRange(testEntry, false), XSD.xstring);

		testEntry.setInseeRepresentation("Expression régulière (ex : 2016X066EC)");
		assertEquals(SIMSModelMaker.getRange(testEntry, false), XSD.xstring);

		testEntry.setInseeRepresentation("Code list : CL_SOURCE_CATEGORY");
		assertEquals(SIMSModelMaker.getRange(testEntry, false).getURI(), Configuration.codeConceptURI("CL_SOURCE_CATEGORY"));

		testEntry.setInseeRepresentation("Code list : CL_COLLECTION_MODE ou texte");
		assertEquals(SIMSModelMaker.getRange(testEntry, false).getURI(), Configuration.codeConceptURI("CL_COLLECTION_MODE"));

		testEntry.setInseeRepresentation("Code list : CL_AREA\n\nNommée différemment parce que je ne sais pas si ça correspond tout à fait à la liste définie en SDMX");
		assertEquals(SIMSModelMaker.getRange(testEntry, false).getURI(), Configuration.codeConceptURI("CL_AREA"));

		testEntry.setInseeRepresentation("Code list : Liste Insee+Liste Liste des SSM + ajouts possibles");
		assertEquals(SIMSModelMaker.getRange(testEntry, false), ORG.Organization);

		testEntry.setInseeRepresentation("Lien (0,n)");
		assertEquals(SIMSModelMaker.getRange(testEntry, false), RDFS.Resource); // TODO Should rather be a kind of StatisticalOperation or StatisticalActivity

		testEntry.setInseeRepresentation("Rich text + other material (0,n) : label+URI/URL");
		assertEquals(SIMSModelMaker.getRange(testEntry, false), RDFS.Resource);
	}
}