package fr.insee.semweb.sdmx.metadata;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.DCTypes;
import org.apache.jena.vocabulary.ORG;
import org.apache.jena.vocabulary.SKOS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class SIMSExporter {

	public static Logger logger = LogManager.getLogger();

	// Export as SMDX
	public static void exportAsSDMX(Model simsModel) {
		
	}

	/**
	 * Queries over an RDF connection to create a Jena SIMS expanded model.
	 * A SIMS expanded model completes the base SIMS model with additional context information (code labels, target resource, etc.).
	 * 
	 * @param connection <code>RDFConnection</code> allowing to query RDF data.
	 * @param simsURI URI of the SIMS document.
	 * @return A Jena model containing the expanded SIMS information.
	 */
	public static Model queryExpandedSIMSModel(RDFConnection connection, String simsURI) {

		// Get the graph containing the SIMS information
		// For example: http://id.insee.fr/qualite/rapport/1507 -> http://rdf.insee.fr/graphes/qualite/rapport/1507
		String simsGraphURI = Configuration.simsReportGraphURI(StringUtils.substringAfterLast(simsURI, "/"));
		logger.debug("About to query graph " + simsGraphURI);
		Model simsModel = connection.fetch(simsGraphURI);
		// Extract the URI of the resource to which the SIMS documentation is attached
		Resource simsResource = simsModel.createResource(simsURI);
		List<RDFNode> targets = simsModel.listObjectsOfProperty(simsResource, Configuration.SIMS_TARGET).toList();
		if (targets.size() != 1) {
			logger.error("SIMS documentation " + simsURI + " should target exactly one resource, but found " + targets.size() + " - " + targets);
			return null;
		}
		// Add description of the target resource to the model
		String targetURI = targets.get(0).toString();
		Query targetQuery = QueryFactory.create("DESCRIBE <" + targetURI + ">");
		logger.debug("About to send DESCRIBE query for resource " + targetURI);
		simsModel.add(connection.queryDescribe(targetQuery));

		// TODO Add code labels

		addPrefixes(simsModel);
		return simsModel;
	}

	/**
	 * Adds to a completed SIMS model the prefix mappings that it uses.
	 *
	 * @param model The completed SIMS model.
	 */
	private static void addPrefixes(Model model) {
		model.setNsPrefix("skos", SKOS.getURI());
		model.setNsPrefix("dcterms", DCTerms.getURI());
		model.setNsPrefix("dctypes", DCTypes.getURI());
		model.setNsPrefix("org", ORG.getURI());
		model.setNsPrefix("sdmx-mm", "http://www.w3.org/ns/sdmx-mm#");
		model.setNsPrefix("ibase", "http://rdf.insee.fr/def/base#");
		model.setNsPrefix("sims-att", "http://ec.europa.eu/eurostat/simsv2/attribute/");
		model.setNsPrefix("isims-att", "http://id.insee.fr/qualite/simsv2fr/attribut/");
	}
}
