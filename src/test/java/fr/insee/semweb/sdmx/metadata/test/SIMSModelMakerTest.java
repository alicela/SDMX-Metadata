package fr.insee.semweb.sdmx.metadata.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.GeoModelMaker;
import fr.insee.semweb.sdmx.metadata.SIMSFrScheme;
import fr.insee.semweb.sdmx.metadata.SIMSModelMaker;

/**
 * Test and launch methods for class <code>SIMSModelMaker</code>.
 * 
 * @author Franck
 */
public class SIMSModelMakerTest {

	/**
	 * Creates and writes to a file the Concept Scheme associated to the SIMS in a Jena model.
	 *
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testCreateConceptScheme() throws IOException {

		SIMSFrScheme simsFrScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		Model simsSKOSModel = SIMSModelMaker.createConceptScheme(simsFrScheme, false, true, true);
		simsSKOSModel.write(new FileWriter(Configuration.SIMS_CS_TURTLE_FILE_NAME), "TTL");
		simsSKOSModel.close();
	}

	/**
	 * Creates and writes to a file the Metadata Structure Definition associated to the SIMS in a Jena model.
	 *
	 * @throws IOException In case of problem while writing the output file.
	 */
	@Test
	public void testCreateMetadataStructureDefinition() throws IOException {

		SIMSFrScheme simsFrScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		Model simsMSDModel = SIMSModelMaker.createMetadataStructureDefinition(simsFrScheme, false, true);
		simsMSDModel.write(new FileWriter(Configuration.SIMS_FR_MSD_TURTLE_FILE_NAME), "TTL");
		simsMSDModel.close();
	}

	/**
	 * Reads the SDMX metadata RDF vocabulary into a Jena ontology model.
	 */
	@Test
	public void testReadSDMXModel() {

		SIMSModelMaker.readSDMXModel(Configuration.SDMX_MM_TURTLE_FILE_NAME, true);
	}

	/**
	 * Exports all SIMSFr metadata (MSD, concepts, base RDF vocabulary) into a TriG file.
	 */
	@SuppressWarnings("unused")
	@Test
	public void exportAllAsTriG() throws IOException {

		// If not null, URI of the graph where the SDMX-MM model will be put
		final String SDMX_MM_GRAPH = null;
	
		SIMSFrScheme simsFrScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		Dataset metadata = DatasetFactory.create();

		// Adjust parameters or comment lines according to desired result
		Model simsModel = SIMSModelMaker.createMetadataStructureDefinition(simsFrScheme, false, true);
		simsModel.add(SIMSModelMaker.createConceptScheme(simsFrScheme, false, true, true));
		Model sdmxMMModel = SIMSModelMaker.readSDMXModel(Configuration.SDMX_MM_TURTLE_FILE_NAME, false);
		if (SDMX_MM_GRAPH == null) simsModel.add(sdmxMMModel);

		metadata.addNamedModel(Configuration.INSEE_BASE_GRAPH_URI + "qualite/simsv2fr", simsModel);
		if (SDMX_MM_GRAPH != null) metadata.addNamedModel(SDMX_MM_GRAPH, sdmxMMModel);
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/sims-metadata.trig"), metadata, Lang.TRIG);
		simsModel.close();
		sdmxMMModel.close();
		metadata.close();
	}

	/**
	 * Exports all specific territories into a TriG file.
	 */
	@Test
	public void exportGeoAsTriG() throws IOException {

		Dataset geography = DatasetFactory.create();

		// Adjust parameters or comment lines according to desired result
		Model geoModel = GeoModelMaker.createGeoModel();
		geography.addNamedModel(Configuration.INSEE_BASE_GRAPH_URI + "qualite/territoires", geoModel);
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/sims-geo.trig"), geography, Lang.TRIG);
		geoModel.close();
		geography.close();
	}

}