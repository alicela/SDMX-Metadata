package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.List;

import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.SIMSChecker;
import fr.insee.semweb.sdmx.metadata.SIMSEntry;

public class SIMSEntryTest {

	@Test
	public void testGetParentNotation() {

		SIMSEntry testEntry = new SIMSEntry("I.1.1.1");
		assertEquals(testEntry.getParentNotation(), "I.1.1");
		testEntry.setNotation("S.18");
		assertEquals(testEntry.getParentNotation(), "S");
		testEntry.setNotation("ZERO");
		assertNull(testEntry.getParentNotation());
	}

	@Test
	public void testEquals() {
		List<SIMSEntry> reference = SIMSChecker.readSIMSFromExcel(new File("src/main/resources/data/SIMS_V2 0.xlsx"), false);
		List<SIMSEntry> modified = SIMSChecker.readSIMSFromExcel(new File("src/main/resources/data/SIMSplus_V20170213.xlsx"), false);
		int index = 0;
		for (SIMSEntry entry : reference) {
			SIMSEntry compareEntry = modified.get(index);
			assertEquals(entry, compareEntry);
			index++;
		}
	}
}
