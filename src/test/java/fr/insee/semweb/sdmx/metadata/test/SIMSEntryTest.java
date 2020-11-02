package fr.insee.semweb.sdmx.metadata.test;

import fr.insee.semweb.sdmx.metadata.SIMSChecker;
import fr.insee.semweb.sdmx.metadata.SIMSEntry;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SIMSEntryTest {

	@Test
	public void testGetIndex() {
		SIMSEntry testEntry = new SIMSEntry("I.1.1.1");
		assertEquals(testEntry.getIndex(), "1.1.1");		
		testEntry.setNotation("C.3.7.1");
		assertEquals(testEntry.getIndex(), "3.7.1");		
		testEntry.setNotation("S.19");
		assertEquals(testEntry.getIndex(), "19");		
		testEntry.setNotation("PLUTO");
		assertNull(testEntry.getIndex());
	}
		
	@Test
	public void testGetParentIndex() {

		SIMSEntry testEntry = new SIMSEntry("I.1.1.1");
		assertEquals(testEntry.getParentIndex(), "1.1");
		testEntry.setNotation("S.18.1");
		assertEquals(testEntry.getParentIndex(), "18");
		testEntry.setNotation("ZERO");
		assertNull(testEntry.getParentIndex());
	}

	@Test
	public void testEquals() {
		File refFile = new File("src/main/resources/data/SIMS_V2 0.xlsx") ;
		File modifFile = new File("src/main/resources/data/SIMSplus_V20170213.xlsx" ) ;
		if (refFile.exists() && modifFile.exists()) {
			List<SIMSEntry> reference = SIMSChecker.readSIMSFromExcel(refFile, false);
			List<SIMSEntry> modified = SIMSChecker.readSIMSFromExcel(modifFile, false);
			int index = 0;
			for (SIMSEntry entry : reference) {
				SIMSEntry compareEntry = modified.get(index);
				assertEquals(entry, compareEntry);
				index++;
			}
		}
	}
}
