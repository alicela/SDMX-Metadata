package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.SIMSFrEntry;
import fr.insee.semweb.sdmx.metadata.SIMSFrScheme;

/**
 * Test and launch methods for class <code>SIMSFrScheme</code>.
 * 
 * @author Franck
 */
public class SIMSFrSchemeTest {

	SIMSFrScheme simsFrScheme = null;
	StringBuilder report = null;

	/**
	 * Reads the SIMSFr from the Excel file.
	 */
	@Before
	public void readScheme() {
		simsFrScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		report = new StringBuilder();
	}

	/**
	 * Writes the SIMSFr to the console.
	 */
	@Test
	public void testReadSIMSFrFromExcel() {

		assertNotNull(simsFrScheme);
		System.out.println(simsFrScheme);
	}

	/**
	 * Writes the SIMSFr attribute hierarchies to the console.
	 */
	@Test
	public void testGetParent() {

		report.append("Attribute hierarchies found in the SIMSFr\n\nChild entry | Parent entry");
		for (SIMSFrEntry testEntry : simsFrScheme.getEntries()) {
			SIMSFrEntry parent = simsFrScheme.getParent(testEntry);
			if (parent == null) report.append('\n').append(StringUtils.rightPad(testEntry.getNotation(), 12) + "| -");
			else report.append('\n').append(StringUtils.rightPad(testEntry.getNotation(), 12) + "| " + parent.getNotation());
		}
		System.out.println(report.toString());
	}

	/**
	 * Checks that attribute hierarchies in the SIMSFr are consistent.
	 */
	@Test
	public void testCheckHierarchy() {

		String hierarchyReport = simsFrScheme.checkHierarchy();
		if (hierarchyReport.length() > 0) System.out.println(hierarchyReport);
		assertEquals(hierarchyReport.length(), 0);
	}

	/**
	 * Writes to the console the list of representations used in the base SIMS and in the SIMSFr models.
	 */
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

	/**
	 * Writes to a file the SIMSFr and the attribute hierarchies.
	 * @throws IOException 
	 */
	@Test
	public void writeReport() throws IOException {

		report.append(simsFrScheme).append("\n\n");
		testGetParent();
		Files.write(Paths.get("src/test/resources/sims-report.txt"), report.toString().getBytes());
	}	
}
