package fr.insee.semweb.sdmx.metadata;

import static fr.insee.semweb.sdmx.metadata.Configuration.M0_RELATED_TO;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Selector;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.insee.semweb.utils.URIComparator;

/**
 * Extracts RDF information expressed in the interim format ("M0 model").
 * 
 * @author Franck
 */
public class M0Extractor {

	/** Log4J2 logger */
	public static Logger logger = LogManager.getLogger(M0Extractor.class);

	/**
	 * Extracts from an M0 model all the statements related to a given SIMS attribute.
	 * Warning: only statements with (non empty) literal object will be selected.
	 * 
	 * @param m0Model A Jena <code>Model</code> in M0 format from which the statements will be extracted.
	 * @param attributeName The name of the SIMS attribute (e.g. SUMMARY).
	 * @return A Jena <code>Model</code> containing the statements of the extract in M0 format.
	 */
	public static Model extractAttributeStatements(Model m0Model, String attributeName) {
	
		logger.debug("Extracting from M0 model triples with subject corresponding to SIMS atttribute: " + attributeName);
	
		Model extractModel = ModelFactory.createDefaultModel();
		Selector selector = new SimpleSelector(null, Configuration.M0_VALUES, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject URI ends with the property name
	        public boolean selects(Statement statement) {
	        	return statement.getSubject().getURI().endsWith(attributeName);
	        }
	    };
	
		// Run the selector and add the selected statements to the extract model
		extractModel.add(m0Model.listStatements(selector));
		long numberOfValues = extractModel.size();
	
		// String attributes may also have English values
		selector = new SimpleSelector(null, Configuration.M0_VALUES_EN, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return ((statement.getSubject().getURI().endsWith(attributeName)) && (statement.getObject().isLiteral()) && (statement.getLiteral().getString().trim().length() > 0));
	        }
	    };
		extractModel.add(m0Model.listStatements(selector));
		long numberOfEnglishValues = extractModel.size() - numberOfValues;
	
		String reportNumber = (numberOfEnglishValues == 0) ? Long.toString(numberOfValues) : Long.toString(numberOfValues) + " (French) + " + Long.toString(numberOfEnglishValues) + " (English)";
		logger.debug("Number of triples extracted: " + reportNumber);
	
		return extractModel;
	}

	/**
	 * Reads all the hierarchies (family -> series or series -> operation) and stores them as a map.
	 * The map keys will be the children and the values the parents, both expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A map containing the hierarchies between children and parents M0 URIs, sorted on keys.
	 */
	public static SortedMap<String, String> extractHierarchies(Model m0AssociationModel) {

		// The hierarchies are in the 'associations' graph and have the following structure:
		// <http://baseUri/familles/famille/58/ASSOCIE_A> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/series/serie/117/ASSOCIE_A>
	
		logger.debug("Extracting the information on hierarchies between families, series and operations");
		SortedMap<String, String> hierarchyMappings = new TreeMap<String, String>(Comparator.nullsFirst(new URIComparator()));

		// Will select the 'ASSOCIE_A/ASSOCIE_A' relations between relevant resources
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject and object URIs end with 'ASSOCIE_A' and begin with expected objects
	        public boolean selects(Statement statement) {
	        	String subjectURI = statement.getSubject().getURI();
	        	String objectURI = statement.getObject().asResource().getURI();
	        	if (!((subjectURI.endsWith("ASSOCIE_A")) && (objectURI.endsWith("ASSOCIE_A")))) return false;
	        	if ((subjectURI.startsWith("http://baseUri/series")) && (objectURI.startsWith("http://baseUri/familles"))) return true;
	        	if ((subjectURI.startsWith("http://baseUri/operations")) && (objectURI.startsWith("http://baseUri/series"))) return true;
	        	return false;
	        }
	    };
	    // Read the selected statements and fill the map that will be returned (will throw an exception if model is null)
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String child = StringUtils.removeEnd(statement.getSubject().getURI(), "/ASSOCIE_A");
				String parent = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/ASSOCIE_A");
				// Each series or operation should have at most one parent
				if (hierarchyMappings.containsKey(child)) M0Converter.logger.error("Conflicting parents for " + child + " - " + parent + " and " + hierarchyMappings.get(child));
				else hierarchyMappings.put(child, parent);
			}
		});
		logger.debug("Size of the map to return is: " + hierarchyMappings.size());
		return hierarchyMappings;	
	}

	/**
	 * Reads all the relations stating that an indicator is produced from a series and stores them as a map.
	 * The map keys will be the indicators and the values the lists of series they are produced from, all expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 model containing information about all associations.
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractProductionRelations(Model m0AssociationModel) {

		// The relations between series and indicators are in the 'associations' graph and have the following structure:
		// <http://baseUri/indicateurs/indicateur/27/PRODUCED_FROM> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/series/serie/137/PRODUIT_INDICATEURS>
		// Note: discard cases where PRODUCED_FROM is used instead of PRODUIT_INDICATEURS.
	
		logger.debug("Extracting 'PRODUCED_FROM/PRODUIT_INDICATEURS' relations between series and indicators");
		Map<String, List<String>> relationMappings = new HashMap<String, List<String>>();
		// Will select the 'PRODUCED_FROM/PRODUIT_INDICATEURS' relations
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject and object URIs end with 'PRODUCED_FROM' and begin with expected objects
	        public boolean selects(Statement statement) {
	        	String subjectURI = statement.getSubject().getURI();
	        	String objectURI = statement.getObject().asResource().getURI();
	        	if (!((subjectURI.endsWith("PRODUCED_FROM")) && (objectURI.endsWith("PRODUIT_INDICATEURS")))) return false;
	        	if ((subjectURI.startsWith("http://baseUri/indicateurs")) && (objectURI.startsWith("http://baseUri/series"))) return true;
	        	return false;
	        }
	    };
	
	    // Read the selected statements and fill the map that will be returned (will throw an exception if model is null)
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String indicatorURI = StringUtils.removeEnd(statement.getSubject().getURI(), "/PRODUCED_FROM");
				String seriesURI = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/PRODUIT_INDICATEURS");
				if (!relationMappings.containsKey(indicatorURI)) relationMappings.put(indicatorURI, new ArrayList<String>());
				relationMappings.get(indicatorURI).add(seriesURI);
			}
		});
		logger.debug("Size of the map to return is: " + relationMappings.size());
		return relationMappings;
	}

	/**
	 * Extracts from the base M0 model all the statements related to a given base resource (series, operation, etc.).
	 * The statements extracted are those whose subject URI begins with the base resource URI.
	 * 
	 * @param m0Model A Jena <code>Model</code> in M0 format from which the statements will be extracted.
	 * @param m0URI The URI of the M0 base resource for which the statements must to extracted.
	 * @return A Jena <code>Model</code> containing the statements of the extract in M0 format.
	 */
	public static Model extractM0ResourceModel(Model m0Model, String m0URI) {
	
		logger.debug("Extracting M0 model for resource: " + m0URI);
	
		Model extractModel = ModelFactory.createDefaultModel();
		Selector selector = new SimpleSelector(null, null, (RDFNode) null) {
									// Override 'selects' method to retain only statements whose subject URI begins with the wanted URI
							        public boolean selects(Statement statement) {
							        	return statement.getSubject().getURI().startsWith(m0URI);
							        }
							    };
		// Copy the relevant statements to the extract model
		extractModel.add(m0Model.listStatements(selector));
	
		return extractModel;
	}

	/**
	 * Returns the maximum of the sequence number used in a M0 model.
	 * 
	 * M0 URIs use a sequence number an increment inferior to the value of property http://rem.org/schema#sequenceValue of resource http://baseUri/codelists/codelist/sequence
	 * @param m0Model The M0 model (extracted from the dataset).
	 * @return The maximum sequence number, or 0 if the information cannot be obtained in the model.
	 */
	public static int getMaxSequence(Model m0Model) {
	
		// M0 URIs use a sequence number an increment inferior or equal to the value of property http://rem.org/schema#sequenceValue of resource http://baseUri/{type}s/{type}/sequence
		// We assume that there is only one triple containing this property per graph.
		final Property sequenceValueProperty = ResourceFactory.createProperty("http://rem.org/schema#sequenceValue");
	
		StmtIterator statements = m0Model.listStatements(null, sequenceValueProperty, (RDFNode)null);
		if (!statements.hasNext()) return 0;
		Statement sequenceStatement = statements.next();
	
		if (!sequenceStatement.getObject().isLiteral()) return 0;
	
		return (Integer.parseInt(sequenceStatement.getObject().asLiteral().toString())); // Assuming we have a string parseable to integer
	}

	/**
	 * Splits the base M0 SIMS model into smaller M0 models corresponding to metadata set identifiers passed as a list, and saves the smaller models to disk.
	 * 
	 * @param m0SIMSModel A Jena <code>Model</code> containing the SIMS metadata in M0 format.
	 * @param m0Ids A <code>List</code> of M0 metadata set identifiers.
	 * @throws IOException In case of problem while writing the model to disk.
	 */
	public static void m0SplitAndSave(Model m0SIMSModel, List<String> m0Ids) throws IOException {
	
		M0Converter.logger.debug("Splitting M0 model into " + m0Ids.size() + " models");
		for (String m0Id : m0Ids) {
			// Create model for the current source
			Model sourceModel = extractM0ResourceModel(m0SIMSModel, "http://baseUri/documentations/documentation/" + m0Id);
			sourceModel.write(new FileOutputStream("src/main/resources/data/models/m0-"+ m0Id + ".ttl"), "TTL");
			sourceModel.close();
		}
	}

}
