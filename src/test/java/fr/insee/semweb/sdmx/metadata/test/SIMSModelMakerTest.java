package fr.insee.semweb.sdmx.metadata.test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.jena.rdf.model.Model;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.SIMSFrScheme;
import fr.insee.semweb.sdmx.metadata.SIMSModelMaker;

public class SIMSModelMakerTest {

	@Test
	public void testCreateConceptScheme() throws IOException {

		SIMSFrScheme simsFrScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		Model simsSKOSModel = SIMSModelMaker.createConceptScheme(simsFrScheme, false, true, true);
		simsSKOSModel.write(new FileWriter(Configuration.SIMS_CS_TURTLE_FILE_NAME), "TTL");
		simsSKOSModel.close();
	}

	@Test
	public void testCreateMetadataStructureDefinition() throws IOException {

		SIMSFrScheme simsFrScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		Model simsMSDModel = SIMSModelMaker.createMetadataStructureDefinition(simsFrScheme, false, true);
		simsMSDModel.write(new FileWriter(Configuration.SIMS_FR_MSD_TURTLE_FILE_NAME), "TTL");
		simsMSDModel.close();
	}

	@Test
	public void testReadSDMXModel() {

		SIMSModelMaker.readSDMXModel(Configuration.SDMX_MM_TURTLE_FILE_NAME, true);
	}
}