package fr.insee.semweb.sdmx.metadata.test;

import fr.insee.semweb.sdmx.metadata.PostProcessor;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Test and launch methods for class <code>PostProcessor</code>.
 * 
 * @author Franck
 */
class PostProcessorTest {

	/**
	 * Runs the EnrichGSIMLabels post-processing task on one SIMS model.
	 */
	@Test
	public void testEnrichGSIMLabelsOne() throws IOException {

		String simsNumber = "1776"; // Can be changed, but target resource should be indicator

		Dataset simsDataset = RDFDataMgr.loadDataset("src/main/resources/data/sims-all.trig");
		Dataset resourceDataset = RDFDataMgr.loadDataset("src/main/resources/data/all-operations-and-indicators.trig");
		Model simsModel = simsDataset.getNamedModel("http://rdf.insee.fr/graphes/qualite/rapport/" + simsNumber);
		Model resourceModel = resourceDataset.getNamedModel("http://rdf.insee.fr/graphes/produits");

		PostProcessor.enrichGSIMLabels(simsModel, resourceModel);
		simsModel.write(new FileOutputStream("src/test/resources/relabelled-sims-" + simsNumber + ".ttl"), "TTL");

		resourceModel.close();
		simsModel.close();
		resourceDataset.close();
		simsDataset.close();
	}

	/**
	 * Runs the EnrichGSIMLabels post-processing task on all SIMS model in a <code>Dataset</code>.
	 */
	@Test
	public void testEnrichGSIMLabelsAll() throws IOException {

		Dataset simsDataset = RDFDataMgr.loadDataset("src/main/resources/data/sims-all.trig");
		Dataset resourceDataset = RDFDataMgr.loadDataset("src/main/resources/data/all-operations-and-indicators.trig");

		Model resourceModel = resourceDataset.getNamedModel("http://rdf.insee.fr/graphes/operations")
											 .add(resourceDataset.getNamedModel("http://rdf.insee.fr/graphes/produits"));

		Dataset modifiedResourceDataset = DatasetFactory.create();
		Model simsModel = null;
		for (Iterator<String> graphIterator = simsDataset.listNames(); graphIterator.hasNext(); ) {
			String graphName = graphIterator.next();
			simsModel = simsDataset.getNamedModel(graphName);
			PostProcessor.enrichGSIMLabels(simsModel, resourceModel);
			modifiedResourceDataset.addNamedModel(graphName, simsModel); // TODO Does that copy the resources?
		}

		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/relabelled-sims-all.trig"), modifiedResourceDataset, Lang.TRIG);

		resourceModel.close();
		simsModel.close();
		resourceDataset.close();
		simsDataset.close();
		modifiedResourceDataset.close();
	}
}
