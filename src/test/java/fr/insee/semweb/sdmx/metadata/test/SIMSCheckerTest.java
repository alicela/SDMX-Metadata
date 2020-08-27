package fr.insee.semweb.sdmx.metadata.test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.SIMSChecker;
import fr.insee.semweb.sdmx.metadata.SIMSEntry;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

/**
 * Test and run methods for <code>SIMSChecker</code>.
 * 
 * @author Franck
 */
public class SIMSCheckerTest {

	/**
	 * Checks the coherence between SIMS and SIMSFr definitions.
	 */
	@Test
	public void testCheckCoherence() {

		String report = SIMSChecker.checkCoherence();
		System.out.println(report);
	}

	/**
	 * Writes to the console a report on the SIMSFr Metadata Structure Definition.
	 */
	@Test
	public void testSIMSFrMSDReport() {

		String report = SIMSChecker.simsFrMSDReport();
		System.out.println(report);		
	}

	/**
	 * Writes to the console the list of SIMS entry read from the SIMS sheet of the SIMS/SIMSFr file.
	 */
	@Test
	public void testReadSIMSFromExcel() {

		List<SIMSEntry> simsEntries = SIMSChecker.readSIMSFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME), false);
		for (SIMSEntry simsEntry : simsEntries) System.out.println(simsEntry + "\n");
	}

	/**
	 * Writes to the console the list of SIMS entry read from the SIMSFr sheet of the SIMS/SIMSFr file.
	 */
	@Test
	public void testReadSIMSFromExcelFr() {

		List<SIMSEntry> simsEntries = SIMSChecker.readSIMSFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME), true);
		for (SIMSEntry simsEntry : simsEntries) System.out.println(simsEntry + "\n");
	}

}
