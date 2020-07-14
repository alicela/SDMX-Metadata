package fr.insee.semweb.sdmx.metadata.test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.SIMSExporter;

class SIMSExporterTest {

	/**
	 * Reads RDF data about all base resources and SIMS models and lists the named graphs to the console.
	 * 
	 * @throws IOException In case of problem reading the TriG file.
	 */
	@Test
	public void testListGraphs() throws IOException {

		SortedSet<String> uris = new TreeSet<>();

		Dataset simsDataset = RDFDataMgr.loadDataset("src/main/resources/data/all-operations-and-indicators.trig");
		RDFDataMgr.read(simsDataset, new FileInputStream("src/main/resources/data/sims-all.trig"), Lang.TRIG);
		simsDataset.listNames().forEachRemaining(uris::add);
		uris.stream().forEach(System.out::println);
		simsDataset.close();
	}

	/**
	 * Exports to a Turtle file a SIMS model extracted from a <code>Dataset</code> file.
	 * 
	 * @throws IOException In case of problems reading the SIMS dataset.
	 */
	@Test
	public void testQuerySIMSModelDataset() throws IOException {

		String simsId = "1507";
		String simsURI = Configuration.simsReportURI(simsId);

		Dataset simsDataset = RDFDataMgr.loadDataset("src/main/resources/data/all-operations-and-indicators.trig");
		RDFDataMgr.read(simsDataset, new FileInputStream("src/main/resources/data/sims-all.trig"), Lang.TRIG);

		try (RDFConnection connection = RDFConnectionFactory.connect(simsDataset)) {

			Model simsModel = SIMSExporter.querySIMSModel(connection, simsURI);
			simsModel.write(new FileOutputStream("src/test/resources/models/" + simsId + ".ttl"), "TTL");
		}
		simsDataset.close();
	}
}
