package fr.insee.semweb.sdmx.metadata;

import static fr.insee.semweb.sdmx.metadata.Configuration.M0_RELATED_TO;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Selector;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.insee.semweb.sdmx.metadata.Configuration.OrganizationRole;
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
	 * Reads all the relation properties between operation-like resources and stores them as a sorted map.
	 * Each relation will be store twice: one for each direction.
	 * NB: the relations between code lists and codes is not returned.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A sorted map containing the relations where keys are M0 URIs and values are lists of related M0 URIs, sorted on keys.
	 */
	public static SortedMap<String, List<String>> extractRelations(Model m0AssociationModel) {
	
		// The relations are in the 'associations' graph and have the following structure:
		// <http://baseUri/series/serie/99/RELATED_TO> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/series/serie/98/RELATED_TO>
	
		logger.debug("Extracting the information on relations between series, indicators, etc.");
		SortedMap<String, List<String>> relationMappings = new TreeMap<String, List<String>>();
	
		// Will select the 'RELATED_TO/RELATED_TO' relations between relevant resources
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject URI ends with 'RELATED_TO' and object URI with 'RELATED_TO'
	        public boolean selects(Statement statement) {
	        	// There are also RELATED_TO relations between code lists and codes in the association model, that must be eliminated
	        	String subjectURI = statement.getSubject().getURI();
	        	if (subjectURI.startsWith("http://baseUri/code")) return false;
	        	return ((subjectURI.endsWith("RELATED_TO")) && (statement.getObject().isResource()) && (statement.getObject().asResource().getURI().endsWith("RELATED_TO")));
	        }
	    };
	    // Read the selected statements and fill the map that will be returned (will throw an exception if model is null)
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String oneEnd = StringUtils.removeEnd(statement.getSubject().getURI(), "/RELATED_TO");
				String otherEnd = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/RELATED_TO");
				if (!relationMappings.containsKey(oneEnd)) relationMappings.put(oneEnd, new ArrayList<String>());
				relationMappings.get(oneEnd).add(otherEnd);
			}
		});
		logger.debug("Size of the map to return is: " + relationMappings.size());
		return relationMappings;	
	}

	/**
	 * Reads all the hierarchies (family -> series or series -> operation) and stores them as a sorted map.
	 * The map keys will be the children and the values the parents, both expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A sorted map containing the hierarchies between children and parents M0 URIs, sorted on keys.
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
	 * Reads all the replacement properties and stores them as a sorted map.
	 * The map keys are the replacing resources and the values are lists of the resources they replaced, both expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A sorted map containing the relations between M0 URIs, sorted on keys.
	 */
	public static SortedMap<String, List<String>> extractReplacements(Model m0AssociationModel) {

		// The relations are in the 'associations' graph and have the following structure :
		// <http://baseUri/series/serie/12/REPLACES> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/series/serie/13/REMPLACE_PAR> .
	
		logger.debug("Extracting the information on replacement relations between series or indicators");
		SortedMap<String, List<String>> replacementMappings = new TreeMap<String, List<String>>(Comparator.nullsFirst(new URIComparator()));
		
		// Will select the 'REPLACES/REMPLACE_PAR' relations
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject URI ends with 'REPLACES' and object URI with 'REMPLACE_PAR'
	        public boolean selects(Statement statement) {
	        	return ((statement.getSubject().getURI().endsWith("REPLACES")) && (statement.getObject().isResource()) && (statement.getObject().asResource().getURI().endsWith("REMPLACE_PAR")));
	        }
	    };
	    // Read the selected statements and fill the map that will be returned (will throw an exception if model is null)
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String after = StringUtils.removeEnd(statement.getSubject().getURI(), "/REPLACES");
				String before = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/REMPLACE_PAR");
				if (!replacementMappings.containsKey(after)) replacementMappings.put(after, new ArrayList<String>());
				replacementMappings.get(after).add(before);
			}
		});
		logger.debug("Size of the map to return is: " + replacementMappings.size());	
		return replacementMappings;
	}

	/**
	 * Reads all the relations stating that an indicator is produced from a series and stores them as a sorted map.
	 * The map keys will be the indicators and the values the lists of series they are produced from, all expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 model containing information about all associations.
	 * @return A sorted map containing the relations where keys are M0 URIs and values are lists of replacing M0 URIs, sorted on keys.
	 */
	public static SortedMap<String, List<String>> extractProductionRelations(Model m0AssociationModel) {

		// The relations between series and indicators are in the 'associations' graph and have the following structure:
		// <http://baseUri/indicateurs/indicateur/27/PRODUCED_FROM> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/series/serie/137/PRODUIT_INDICATEURS>
		// Note: discard cases where PRODUCED_FROM is used instead of PRODUIT_INDICATEURS.
	
		logger.debug("Extracting 'PRODUCED_FROM/PRODUIT_INDICATEURS' relations between series and indicators");
		SortedMap<String, List<String>> relationMappings = new TreeMap<String, List<String>>(Comparator.nullsFirst(new URIComparator()));
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
	 * Reads all the relations of a specified type (production, stakeholding) between operations and organizations and stores them as a sorted map.
	 * The map keys will be the operations and the values the lists of organizations, all expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @param organizationRole Role of the organizations to extract: producers or stakeholders.
	 * @return A sorted map containing the relations where keys are M0 URIs and values are lists of linked organizations M0 URIs, sorted on keys.
	 */
	public static SortedMap<String, List<String>> extractOrganizationalRelations(Model m0AssociationModel, OrganizationRole organizationRole) {
	
		// The relations between operations and organizations are in the 'associations' graph and have the following structure (same with '/ORGANISATION' for producer):
		// <http://baseUri/series/serie/42/STAKEHOLDERS> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/organismes/organisme/10/STAKEHOLDERS>
	
		logger.debug("Extracting organizational realtions between series and indicators for organization role " + organizationRole);
		SortedMap<String, List<String>> organizationMappings = new TreeMap<String, List<String>>();
		String suffix = "/" + organizationRole.toString();
		// Will select the 'STAKEHOLDERS/STAKEHOLDERS' or 'ORGANISATION/ORGANISATION' relations
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject and object URIs end with the appropriate suffix
	        public boolean selects(Statement statement) {
	        	return ((statement.getSubject().getURI().endsWith(suffix)) && (statement.getObject().isResource())
	        			&& (statement.getObject().asResource().getURI().startsWith("http://baseUri/organismes")) && (statement.getObject().asResource().getURI().endsWith(suffix)));
	        }
	    };

	    // Read the selected statements and fill the map that will be returned (will throw an exception if model is null)
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String operation = StringUtils.removeEnd(statement.getSubject().getURI(), suffix);
				String organization = StringUtils.removeEnd(statement.getObject().asResource().getURI(), suffix);
				if (!organizationMappings.containsKey(operation)) organizationMappings.put(operation, new ArrayList<String>());
				organizationMappings.get(operation).add(organization);
			}
		});
	
		logger.debug("Size of the map to return is: " + organizationMappings.size());
		return organizationMappings;
	}

	/**
	 * Reads all the relations between SIMS metadata sets and series and operations (and possibly indicators), and returns them as a sorted map.
	 * The map keys will be the SIMS 'documentation' and the values the series, operation or indicator, both expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @param includeIndicators If <code>true</code>, the attachments to indicators will also be returned, otherwise only series and operations are considered.
	 * @return A sorted map containing the attachment relations.
	 */
	public static SortedMap<String, String> extractSIMSAttachments(Model m0AssociationModel, boolean includeIndicators) {
	
		// The attachment relations are in the 'associations' graph and have the following structure:
		// <http://baseUri/documentations/documentation/1527/ASSOCIE_A> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/operations/operation/1/ASSOCIE_A>
	
		logger.debug("Extracting the information on attachment between SIMS metadata sets and series or operations");
		SortedMap<String, String> attachmentMappings = new TreeMap<String, String>();
		// Will select all RELATED_TO relations between documentations and series, operations and, if requested, indicators
		Selector selector = new SimpleSelector(null, Configuration.M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject and object URIs end with 'ASSOCIE_A' and begin with expected objects
			@Override
	        public boolean selects(Statement statement) {
	        	String subjectURI = statement.getSubject().getURI();
	        	String objectURI = statement.getObject().asResource().getURI();
	        	if (!((subjectURI.endsWith("ASSOCIE_A")) && (objectURI.endsWith("ASSOCIE_A")))) return false;
	        	if (subjectURI.startsWith("http://baseUri/documentations")) {
	        		if (objectURI.startsWith("http://baseUri/series")) return true;
	        		if (objectURI.startsWith("http://baseUri/operations")) return true;
	        		if (includeIndicators && objectURI.startsWith("http://baseUri/indicateurs")) return true;
	        	}
	        	return false;
	        }
	    };
	    // Read the selected statements and fill the map that will be returned (will throw an exception if model is null)
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String simsSet = StringUtils.removeEnd(statement.getSubject().getURI(), "/ASSOCIE_A");
				String operation = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/ASSOCIE_A");
				// We can check that each operation or series has not more than one SIMS metadata set attached
				if (attachmentMappings.containsValue(operation)) M0SIMSConverter.logger.warn("Several SIMS metadata sets are attached to " + operation);
				// Each SIMS metadata set should be attached to only one series/operation
				if (attachmentMappings.containsKey(simsSet)) M0SIMSConverter.logger.error("SIMS metadata set " + simsSet + " is attached to both " + operation + " and " + attachmentMappings.get(simsSet));
				else attachmentMappings.put(simsSet, operation);
			}
		});
	
		logger.debug("Size of the map to return is: " + attachmentMappings.size());
		return attachmentMappings;	
	}

	/**
	 * Returns the sorted set of all documentation identifiers in a M0 'documentations' model.
	 * 
	 * @param m0DocumentationModel The M0 'documentations' model.
	 * @return The set of identifiers as integers in ascending order.
	 */
	public static SortedSet<Integer> getM0DocumentationIds(Model m0DocumentationModel) {

		logger.debug("Extracting the list of all M0 documentation identifiers");
		SortedSet<Integer> m0DocumentIdSet = new TreeSet<Integer>();
	
		ResIterator subjectsIterator = m0DocumentationModel.listSubjects();
		while (subjectsIterator.hasNext()) {
			String m0DocumentationURI = subjectsIterator.next().getURI();
			// Documentation URIs will typically look like http://baseUri/documentations/documentation/1608/ASSOCIE_A
			String m0DocumentationId = m0DocumentationURI.substring(Configuration.M0_SIMS_BASE_URI.length()).split("/")[0];
			// Series identifiers are integers (but careful with the sequence number)
			try {
				m0DocumentIdSet.add(Integer.parseInt(m0DocumentationId));
			} catch (NumberFormatException e) {
				// Should be the sequence number resource: http://baseUri/documentations/documentation/sequence
				if (!("sequence".equals(m0DocumentationId))) M0SIMSConverter.logger.error("Invalid documentation URI: " + m0DocumentationURI);
			}
		}
		logger.debug("Found a total of " + m0DocumentIdSet.size() + " documentations in the M0 model");
		logger.debug("Minimum identifier is " + m0DocumentIdSet.first() + ", maximum identifier is " + m0DocumentIdSet.last());
	
		return m0DocumentIdSet;
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
	 * Extracts all named graphs from a M0 dataset and saves them to individual files.
	 * 
	 * @param m0Dataset The RDF dataset containing the M0 data.
	 */
	public static void extractModels(Dataset m0Dataset) {

		logger.info("Extracting all M0 models to different files");
		Iterator<String> nameIterator = m0Dataset.listNames();
		nameIterator.forEachRemaining(new Consumer<String>() {
			@Override
			public void accept(String graphURI) {
				String graphName = graphURI.replace(Configuration.M0_BASE_GRAPH_URI, "");
				String fileName = "src/main/resources/data/m0-" + graphName + ".ttl";
				try (FileWriter writer = new FileWriter(fileName)) {
					m0Dataset.getNamedModel(graphURI).write(writer, "TTL");
					logger.info("Graph " + graphName + " extracted and saved to file " + fileName);
				} catch (IOException e) {
					logger.error("Error while trying to save graph " + graphName + " to file " + fileName);
				}
			}
		});
	}
}
