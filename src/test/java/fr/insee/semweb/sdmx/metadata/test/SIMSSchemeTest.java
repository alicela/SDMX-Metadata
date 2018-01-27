package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.SIMSFREntry;
import fr.insee.semweb.sdmx.metadata.SIMSFRScheme;

public class SIMSSchemeTest {

	@Test
	public void testCheckNotations() {

		SIMSFRScheme testSIMS = new SIMSFRScheme();
		testSIMS.addEntry(new SIMSFREntry("S.13"));
		assertEquals(testSIMS.checkHierarchy().length(), 0);
		testSIMS.addEntry(new SIMSFREntry("S.13.3.2"));
		assertNotEquals(testSIMS.checkHierarchy().length(), 0);
		testSIMS.addEntry(new SIMSFREntry("S.13.3"));
		System.out.println(testSIMS.checkHierarchy());
		assertEquals(testSIMS.checkHierarchy().length(), 0);
		System.out.println(testSIMS.checkHierarchy());
	}

}
