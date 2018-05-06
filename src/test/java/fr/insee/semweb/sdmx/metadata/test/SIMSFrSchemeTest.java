package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.SIMSFrEntry;
import fr.insee.semweb.sdmx.metadata.SIMSFrScheme;

public class SIMSFrSchemeTest {

	SIMSFrScheme simsFrScheme = null;

	@Before
	public void readScheme() {
		simsFrScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
	}

	@Test
	public void testReadSIMSFrFromExcel() {

		assertNotNull(simsFrScheme);
		System.out.println(simsFrScheme);
	}

	@Test
	public void testGetParent() {

		for (SIMSFrEntry testEntry : simsFrScheme.getEntries()) {
			SIMSFrEntry parent = simsFrScheme.getParent(testEntry);
			if (parent == null) System.out.println("Entry " + testEntry.getNotation() + " has no parent");
			else System.out.println("Parent of entry " + testEntry.getNotation() + " is entry " + parent.getNotation());
		}
	}

	@Test
	public void testCheckHierarchy() {

		String hierarchyReport = simsFrScheme.checkHierarchy();
		if (hierarchyReport.length() > 0) System.out.println(hierarchyReport);
		assertEquals(hierarchyReport.length(), 0);
	}

	@Test
	public void testRepresentations() {

		List<String> representations = new ArrayList<String>();
		for (SIMSFrEntry testEntry : simsFrScheme.getEntries()) {
			if (testEntry.getRepresentation() == null) continue;
			if (representations.contains(testEntry.getRepresentation())) continue;
			representations.add(testEntry.getRepresentation());
		}
		System.out.println("Base representations " + representations);

		representations.clear();
		for (SIMSFrEntry testEntry : simsFrScheme.getEntries()) {
			if (testEntry.getInseeRepresentation() == null) continue;
			if (representations.contains(testEntry.getInseeRepresentation())) continue;
			representations.add(testEntry.getInseeRepresentation());
		}
		System.out.println("Insee representations " + representations);		
	}
}
