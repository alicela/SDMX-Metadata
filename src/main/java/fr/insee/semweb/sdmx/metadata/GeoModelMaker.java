package fr.insee.semweb.sdmx.metadata;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Creates a RDF model containing the specific geographic areas used in the SIMSFr.
 * 
 * @author Franck Cotton
 */
public class GeoModelMaker {

	public static Logger logger = LogManager.getLogger(GeoModelMaker.class);

	/**
	 * Reads one code list from a sheet of the dedicated Excel file into a Jena model.
	 * 
	 * @param sheet A sheet of the Excel file containing the code lists (<code>Sheet</code> object).
	 * @return A Jena <code>Model</code> containing the code list as a SKOS concept scheme.
	 */
	public static Model createGeoModel() {

		Model geoModel = ModelFactory.createDefaultModel();
		geoModel.setNsPrefix("skos", SKOS.getURI());
		geoModel.setNsPrefix("rdfs", RDFS.getURI());
		geoModel.setNsPrefix("igeo", "http://rdf.insee.fr/def/geo#");

		Resource territoireStatistique = geoModel.createResource("http://rdf.insee.fr/def/geo#TerritoireStatistique");
		Resource franceHorsMayotte = geoModel.createResource(Configuration.QUALITY_BASE_URI + "territoire/franceHorsMayotte", territoireStatistique);
		logger.debug("Creating resource 'France hors Mayotte");
		franceHorsMayotte.addProperty(SKOS.prefLabel, geoModel.createLiteral("France hors Mayotte", "fr"));

		return geoModel;
	}
}
