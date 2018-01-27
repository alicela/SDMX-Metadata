package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.*;

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
	public void testExtractModels() {
		fail("Not yet implemented");
	}

	@Test
	public void testCheckSIMSAttributes() {

		M0Checker.checkSIMSAttributes();
	}

	@Test
	public void testCheckCoherence() {

		M0Checker.checkCoherence(true);
	}

}
