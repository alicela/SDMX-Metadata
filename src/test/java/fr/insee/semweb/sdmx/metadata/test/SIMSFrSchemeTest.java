package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.SIMSFrEntry;
import fr.insee.semweb.sdmx.metadata.SIMSFrScheme;

public class SIMSFrSchemeTest {

	@Test
	public void testReadSIMSFrFromExcel() {

		SIMSFrScheme simsFrScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		assertNotNull(simsFrScheme);

		System.out.println(simsFrScheme);
	}

	@Test
	public void testGetParent() {

		SIMSFrScheme simsFrScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));

		for (SIMSFrEntry testEntry : simsFrScheme.getEntries()) {
			SIMSFrEntry parent = simsFrScheme.getParent(testEntry);
			if (parent == null) System.out.println("Entry " + testEntry.getNotation() + " has no parent");
			else System.out.println("Parent of entry " + testEntry.getNotation() + " is entry " + parent.getNotation());
		}
	}

	@Test
	public void testCheckHierarchy() {

		SIMSFrScheme simsFrScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		String hierarchyReport = simsFrScheme.checkHierarchy();

		if (hierarchyReport.length() > 0) System.out.println(hierarchyReport);

		assertEquals(hierarchyReport.length(), 0);
	}
}
