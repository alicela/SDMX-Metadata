package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.SIMSFrScheme;

public class SIMSPlusSchemeTest {

	@Test
	public void testReadSIMSPlusFromExcel() {

		SIMSFrScheme simsPlusScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		assertNotNull(simsPlusScheme);

		System.out.println(simsPlusScheme);

	}

}
