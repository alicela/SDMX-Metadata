package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.SIMSFrScheme;

public class SIMSFrSchemeTest {

	@Test
	public void testReadSIMSFrFromExcel() {

		SIMSFrScheme simsFrScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		assertNotNull(simsFrScheme);

		System.out.println(simsFrScheme);
	}
}
