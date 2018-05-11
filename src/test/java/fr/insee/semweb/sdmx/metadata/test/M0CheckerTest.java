package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.M0Checker;

public class M0CheckerTest {

	@Test
	public void testStudyDocumentations() {
		fail("Not yet implemented");
	}

	@Test
	public void testStudySeries() {
		fail("Not yet implemented");
	}

	@Test
	public void testStudyFamilies() {
		fail("Not yet implemented");
	}

	@Test
	public void testStudyOperations() {
		fail("Not yet implemented");
	}

	@Test
	public void testExtractModels() throws IOException {

		M0Checker.extractModels();
	}

	@Test
	public void testCheckSIMSAttributes() {

		M0Checker.checkSIMSAttributes();
	}

	@Test
	public void testCheckCoherence() {

		M0Checker.checkCoherence(true);
	}

	@Test
	public void testListPropertyValues() {

		Set<String> values = M0Checker.listPropertyValues("REF_AREA");
		System.out.println(values);
	}

	@Test
	public void testCheckPropertyValues() {

		// For CL_FREQ/CL_FREQ_FR
		// Set<String> validValues = new HashSet<String>(Arrays.asList("A", "S", "Q", "M", "W", "D", "H", "B", "N", "I", "P", "C", "U", "L", "T"));
		// For CL_SOURCE_CATEGORY
		// Set<String> validValues = new HashSet<String>(Arrays.asList("S", "A", "C", "I", "M", "P", "R"));
		// For CL_UNIT_MEASURE
		// Set<String> validValues = new HashSet<String>(Arrays.asList("DAYS", "EUR", "FRF1", "HOURS", "KILO", "KLITRE", "LITRES", "MAN-DY", "MAN_YR", "MONTHS", "NATCUR", "OUNCES", "PC", "PCPA", "PERS", "PM", "POINTS", "PURE_NUMB", "SQ_M", "TONNES", "UNITS", "USD", "XDR", "XEU"));
		// For CL_SURVEY_STATUS/CL_STATUS
		// Set<String> validValues = new HashSet<String>(Arrays.asList("T", "Q", "C", "G"));
		// For CL_SURVEY_UNIT
		// Set<String> validValues = new HashSet<String>(Arrays.asList("I", "H", "E", "P", "L", "G", "C", "T", "A", "O"));
		// For CL_COLLECTION_MODE
		// Set<String> validValues = new HashSet<String>(Arrays.asList("F", "M", "P", "I", "O"));
		// For CL_AREAL
		Set<String> validValues = new HashSet<String>(Arrays.asList("FR", "FMET", "FPRV", "COM", "DOM", "FR10", "FRB0", "FRC1", "FRC2", "FRD1", "FRD2", "FRE1", "FRE2", "FRF1", "FRF2", "FRF3", "FRG0", "FRH0", "FRI1", "FRI2", "FRI3", "FRJ1", "FRJ2", "FRK1", "FRK2", "FRL0", "FRM0", "FRY1", "FRY2", "FRY3", "FRY4", "FRY5", "FRZZ", "FR101", "FR102", "FR103", "FR104", "FR105", "FR106", "FR107", "FR108", "FRB01", "FRB02", "FRB03", "FRB04", "FRB05", "FRB06", "FRC11", "FRC12", "FRC13", "FRC14", "FRC21", "FRC22", "FRC23", "FRC24", "FRD11", "FRD12", "FRD13", "FRD21", "FRD22", "FRE11", "FRE12", "FRE21", "FRE22", "FRE23", "FRF11", "FRF12", "FRF21", "FRF22", "FRF23", "FRF24", "FRF31", "FRF32", "FRF33", "FRF34", "FRG01", "FRG02", "FRG03", "FRG04", "FRG05", "FRH01", "FRH02", "FRH03", "FRH04", "FRI11", "FRI12", "FRI13", "FRI14", "FRI15", "FRI22", "FRI23", "FRI31", "FRI32", "FRI33", "FRI34", "FRJ11", "FRJ12", "FRJ13", "FRJ14", "FRJ15", "FRJ21", "FRJ22", "FRJ23", "FRJ24", "FRJ25", "FRJ26", "FRJ27", "FRJ28", "FRK11", "FRK12", "FRK13", "FRK14", "FRK21", "FRK22", "FRK23", "FRK24", "FRK25", "FRK26", "FRK27", "FRK28", "FRL01", "FRL02", "FRL04", "FRL05", "FRL06", "FRM01", "FRM02", "FRY10", "FRY20", "FRY30", "FRY40", "FRY50", "FRZZZ", "OTHER"));

		Model invalidStatements = M0Checker.checkPropertyValues("REF_AREA", validValues);
		if (invalidStatements.size() == 0) System.out.println("No invalid statements found");
		else {
			StmtIterator iterator = invalidStatements.listStatements();
			List<Statement> statementList = iterator.toList();
			for (Statement statement : statementList) {
				System.out.println(statement.getSubject() + " - " + statement.getObject());
			}
		}
	}
}
