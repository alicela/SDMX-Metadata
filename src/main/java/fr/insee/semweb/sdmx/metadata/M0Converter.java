package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Selector;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.ORG;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import static fr.insee.semweb.sdmx.metadata.Configuration.*;
import fr.insee.semweb.utils.URIComparator;
import fr.insee.stamina.utils.PROV;

/**
 * Converts RDF information expressed in the interim format ("M0 model") to the target model.
 * 
 * @author Franck
 */
public class M0Converter {

	public static Logger logger = LogManager.getLogger(M0Converter.class);

	/** The M0 dataset containing all the models */
	protected static Dataset m0Dataset = null;

	/** The explicit fixed mappings between M0 and target URIs for operations and the like */
	static Map<String, String> fixedURIMappings = null;

	/** The mappings between M0 and target URIs for organizations */
	static Map<String, String> organizationURIMappings = null;

	/** All the mappings between M0 and target URIs for families, series, operations and indicators */
	static Map<String, String> allURIMappings = null;

	/**
	 * Return a dataset containing two named graphs: one for families, series and operations, and one for indicators.
	 * 
	 * @param operationGraph The URI to use for the 'operations' graph.
	 * @param indicatorGraph The URI to use for the 'indicators' graph.
	 * @return A Jena <code>Dataset</code> containing the information organized in two graphs.
	 */
	public static Dataset readAllBaseResources(String operationGraph, String indicatorGraph) {

		logger.debug("Extracting M0 dataset with graph: " + operationGraph + " for operations and graph " + indicatorGraph + " for indicators");
		Dataset dataset = DatasetFactory.create();
		dataset.addNamedModel(operationGraph, extractAllOperations());
		dataset.addNamedModel(indicatorGraph, convertIndicators());

		return dataset;
	}

	/**
	 * Extracts the code lists from the M0 model and restructures them as SKOS concept schemes.
	 * 
	 * @return A Jena <code>Model</code> containing the M0 code lists as SKOS concept schemes.
	 */
	public static Model extractCodeLists() {

		// Mappings between M0 'attribute URIs' and SKOS properties
		final Map<String, Property> clMappings = new HashMap<String, Property>();
		clMappings.put("ID", null); // ID is equal to code or code list number, no business meaning (and expressed with a weird property http://www.SDMX.org/.../message#values"
		clMappings.put("CODE_VALUE", SKOS.notation); // CODE_VALUE seems to be the notation, FIXME it is in French
		clMappings.put("ID_METIER", RDFS.comment); // ID_METIER is just TITLE - ID, store in a comment for now
		clMappings.put("TITLE", SKOS.prefLabel); // Can have French and English values
		final List<String> stringProperties = Arrays.asList("ID_METIER", "TITLE"); // Property whose values should have a language tag
		
		readDataset();
		logger.debug("Extracting code lists from dataset " + M0_FILE_NAME);
		Model skosModel = ModelFactory.createDefaultModel();
		skosModel.setNsPrefix("rdfs", RDFS.getURI());
		skosModel.setNsPrefix("skos", SKOS.getURI());

		// Open the 'codelists' model first to obtain the number of code lists and create them in SKOS model
		Model clModel = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "codelists");
		// Code lists M0 URIs take the form http://baseUri/codelists/codelist/n, where n is an increment strictly inferior to the value of http://baseUri/codelists/codelist/sequence
		int clNumber = getMaxSequence(clModel);
		logger.debug(clNumber + " code lists found in 'codelists' model");

		// Then we read in the 'associations' model the mappings between code lists and codes and store them as a map
		// Mappings are of the form {code list URI}/RELATED_TO M0_RELATED_TO {code URI}/RELATED_TO
		Map<Integer, List<Integer>> codeMappings = new HashMap<Integer, List<Integer>>();
		Model assoModel = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		for (int index = 1; index <= clNumber; index++) {
			List<Integer> listOfCodes = new ArrayList<Integer>();
			Resource clResource = clModel.createResource("http://baseUri/codelists/codelist/" + index + "/RELATED_TO");
			// Retrieve the numeric identifiers of the codes related to the current code list
			StmtIterator assoIterator = assoModel.listStatements(clResource, M0_RELATED_TO, (RDFNode)null);
			assoIterator.forEachRemaining(new Consumer<Statement>() {
				@Override
				public void accept(Statement statement) {
					Integer code = Integer.parseInt(statement.getObject().asResource().getURI().split("/")[5]);
					listOfCodes.add(code);
				}
			});
			codeMappings.put(index, listOfCodes);
		}
		assoModel.close();

		// Open the 'code' model and browse both 'codelists' and 'codes' models to produce the target SKOS model
		Model codeModel = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "codes");
		// Main loop is on code lists
		for (int clIndex = 1; clIndex <= clNumber; clIndex++) {
			Resource clResource = clModel.createResource("http://baseUri/codelists/codelist/" + clIndex);
			Resource skosCLResource = skosModel.createResource("http://baseUri/codelists/codelist/" + clIndex, SKOS.ConceptScheme);
			logger.info("Creating code list " + skosCLResource.getURI() + " containing codes " + codeMappings.get(clIndex));
			for (String property : clMappings.keySet()) {
				if (clMappings.get(property) == null) continue;
				Resource propertyResource = clModel.createResource(clResource.getURI() + "/" + property);
				StmtIterator valueIterator = clModel.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // Find French values (there should be exactly one)
				if (!valueIterator.hasNext()) {
					logger.error("No value for property " + property + " of code list " + clResource.getURI());
					continue;
				}
				// Create the relevant statement in the SKOS model, adding a language tag if the property is in stringProperties
				if (stringProperties.contains(property)) {
					skosCLResource.addProperty(clMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString(), "fr"));
				} else {
					skosCLResource.addProperty(clMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString()));
				}
				if (valueIterator.hasNext()) logger.error("Several values for property " + property + " of code list " + clResource.getURI());
				valueIterator = clModel.listStatements(propertyResource, M0_VALUES_EN, (RDFNode)null); // Find English values (can be zero or one)
				if (valueIterator.hasNext()) {
					skosCLResource.addProperty(clMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString(), "en"));
				}
			}
			// Read in the code mappings the list of codes associated to the current code list
			List<Integer> codesOfList = codeMappings.get(clIndex);
			for (int codeIndex : codesOfList) {
				Resource codeResource = codeModel.createResource("http://baseUri/codes/code/" + codeIndex);
				Resource skosCodeResource = skosModel.createResource("http://baseUri/codes/code/" + codeIndex, SKOS.Concept);
				// Create the statements associated to the code
				for (String property : clMappings.keySet()) {
					if (clMappings.get(property) == null) continue;
					Resource propertyResource = codeModel.createResource(codeResource.getURI() + "/" + property);
					StmtIterator valueIterator = codeModel.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // Find French values (there should be exactly one)
					if (!valueIterator.hasNext()) {
						logger.error("No value for property " + property + " of code " + codeResource.getURI());
						continue;
					}
					if (stringProperties.contains(property)) {
						skosCodeResource.addProperty(clMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString(), "fr"));
					} else {
						skosCodeResource.addProperty(clMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString()));
					}
					if (valueIterator.hasNext()) logger.error("Several values for property " + property + " of code " + codeResource.getURI());
					valueIterator = codeModel.listStatements(propertyResource, M0_VALUES_EN, (RDFNode)null); // Find English values (can be zero or one)
					if (valueIterator.hasNext()) {
						skosCodeResource.addProperty(clMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString(), "en"));
					}
					// Finally, add the relevant SKOS properties between the code and the code list
					skosCodeResource.addProperty(SKOS.inScheme, skosCLResource);
					skosCodeResource.addProperty(SKOS.topConceptOf, skosCLResource);
					skosCLResource.addProperty(SKOS.hasTopConcept, skosCodeResource);
				}
			}
		}

		clModel.close();
		codeModel.close();

		return skosModel;
	}

	/**
	 * Extracts the information on organizations from the M0 model and restructures it according to ORG.
	 * For now we only extract the ID_CODE and (French) TITLE, and check consistency with target model (created from spreadsheet).
	 * 
	 * @return A Jena <code>Model</code> containing the M0 organizations in ORG format.
	 */
	public static Model extractOrganizations() {

		// Read dataset and create model to return and read target model for consistency check
		readDataset();
		logger.debug("Extracting information on organizations from dataset " + M0_FILE_NAME);
		Model orgModel = ModelFactory.createDefaultModel();
		orgModel.setNsPrefix("rdfs", RDFS.getURI());
		orgModel.setNsPrefix("org", ORG.getURI());
		Model targetModel = ModelFactory.createDefaultModel();
		try {
			targetModel.read("src/main/resources/ssm.ttl");
			logger.debug("Target model read from 'src/main/resources/ssm.ttl'");
		} catch (Exception e) {
			// Model will be empty: all requests will return no results
			logger.warn("Error while reading the target organization model - " + e.getMessage());
		}

		// Open the 'organismes' model first to obtain the number of organizations and create them in an ORG model
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "organismes");
		// Code lists M0 URIs take the form http://baseUri/organismes/organisme/n, where n is an increment strictly inferior to the value of http://baseUri/organismes/organisme/sequence
		int orgNumber = getMaxSequence(m0Model);
		logger.debug(orgNumber + " organizations found in 'organismes' model");

		for (int orgIndex = 1; orgIndex <= orgNumber; orgIndex++) {
			String resourceURI = "http://baseUri/organismes/organisme/" + orgIndex;
			logger.info("Creating organization " + resourceURI);
			Resource propertyResource = m0Model.createResource(resourceURI + "/ID_CODE");
			StmtIterator valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // There should be exactly one value
			String orgId = "";
			if (valueIterator.hasNext()) orgId = valueIterator.next().getObject().toString().trim();
			if (orgId.length() == 0) {
				logger.warn("No organization for index  " + orgIndex);
				continue;
			}
			// Create resource with its identifier
			Resource orgResource = orgModel.createResource(resourceURI, ORG.organization);
			orgResource.addProperty(ORG.identifier, orgId);
			// Add the title of the organization
			propertyResource = m0Model.createResource(resourceURI + "/TITLE");
			valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // We assume there is exactly one value
			orgResource.addProperty(RDFS.label, valueIterator.next().getObject().toString().trim());

			// Check that organization is in the target scheme (for non Insee organizations)
			if ((orgId.length() == 4) && (StringUtils.isNumeric(orgId.substring(1)))) continue; // Insee organizations identifiers are like XNNN
			// Look in the target model for an organization with identifier equal to orgId
			if (!targetModel.contains(null, DCTerms.identifier, orgId)) logger.warn("Organization " + orgId + " not found in target model");
			
		}
		m0Model.close();
		targetModel.close();

		return orgModel;
	}

	/**
	 * Returns the correspondence between the M0 identifiers and the target URI for series and operations which have a fixed (existing) target Web4G identifier.
	 * 
	 * @param m0Dataset The MO dataset.
	 * @param type Type of resource under consideration: should be 'famille', 'serie', 'operation' or 'indicateur'.
	 * @return A map giving the correspondences between the M0 identifier (integer) and the target URI of the resource.
	 */
	public static Map<Integer, String> getIdURIFixedMappings(Dataset m0Dataset, String type) {

		Map<Integer, String> mappings = new HashMap<Integer, String>();

		// For families and indicators, there is no fixed mappings: return an empty map
		if (("famille".equals(type)) || ("indicateur".equals(type))) return mappings;

		// For operations, there are only a few cases where the Web4G identifier is fixed
		if ("operation".equals(type)) {
			for (Integer m0Id : m02Web4GIdMappings.keySet()) mappings.put(m0Id, operationResourceURI(m02Web4GIdMappings.get(m0Id), type));
			return mappings;
		}

		// Here we should only have type 'serie'
		if (!("serie".equals(type))) return null;

		// For series, the Web4G identifier is obtained through the DDS identifier: extract the ID_DDS property from the series model
		String graphURI = M0_BASE_GRAPH_URI + type + "s";
		Model extract = M0Extractor.extractAttributeStatements(m0Dataset.getNamedModel(graphURI), "ID_DDS");
		logger.debug("Extracted ID_DDS property statements from graph " + graphURI + ", size of resulting model is " + extract.size());

		extract.listStatements().forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				// Retrieve the M0 numeric identifier, assuming URI structure http://baseUri/{type}s/{type}/{nn}/ID_DDS
				String m0URI = statement.getSubject().getURI().toString();
				Integer m0Id = Integer.parseInt(m0URI.split("/")[5]);
				// Retrieve the "DDS" identifier from the object of the ID_DDS statement (eg OPE-ENQ-SECTORIELLE-ANNUELLE-ESA, skip the 'OPE-' start)
				String ddsId = statement.getObject().asLiteral().toString().substring(4);
				// Retrieve the "Web4G" identifier from the "DDS" identifier and the mappings contained in the Configuration class
				if (!dds2Web4GIdMappings.containsKey(ddsId)) {
					logger.warn("No correspondence found for DDS identifier " + ddsId + " (M0 resource " + m0URI + ")");
				} else {
					String web4GId = dds2Web4GIdMappings.get(ddsId);
					logger.debug("Correspondence found for " + type + " " + m0Id + " with DDS identifier " + ddsId + ": Web4G identifier is " + web4GId);
					String targetURI = operationResourceURI(web4GId, type);
					mappings.put(m0Id, targetURI);
				}
			}
		});
		extract.close();
		// HACK Add three direct mappings for series 135, 136 and 137 because they have an ID_DDS but it is not in the M0 dataset
		mappings.put(135, operationResourceURI("1241", "serie"));
		mappings.put(136, operationResourceURI("1371", "serie")); // Changed from 1195, see mail 2/20
		mappings.put(137, operationResourceURI("1284", "serie"));
		// HACK Add new mapping (mail RC 3/7)
		mappings.put(21, operationResourceURI("1229", "serie"));

		return mappings;
	}

	/**
	 * Returns all URI mappings for operations, series, families and indicators.
	 * 
	 * @return The mappings as a map where the keys are the M0 URIs and the values the target URIs, sorted on keys.
	 */
	public static SortedMap<String, String> createURIMappings() {

		// Fix the sizes of the ranges reserved for the new identifications of the different types of objects
		Map<String, Integer> idRanges = new HashMap<String, Integer>();
		idRanges.put("famille", 0); // Families are identified in their own range [1, 999], not in the common range
		idRanges.put("serie", 50); // There are only 7 series without a fixed mapping, that leaves 43 for future creations
		idRanges.put("operation", 430); // There are 17 out of 243 operations with a fixed mapping, that leaves 204
		idRanges.put("indicateur", 0); // Not used
		Map<String, Integer> idCounters = new HashMap<String, Integer>();

		readDataset();
		SortedMap<String, String> mappings = new TreeMap<String, String>(Comparator.nullsFirst(new URIComparator()));
		List<String> types = Arrays.asList("famille", "serie", "operation", "indicateur");
		logger.info("Starting the creation of all the URI mappings for families, series, operations and indicators");

		// 1: Get fixed mappings and remove correspondent identifiers from available identifiers
		// Target identifiers range from 1001 upwards (except for families)
		List<Integer> availableNumbers = IntStream.rangeClosed(1001, 1999).boxed().collect(Collectors.toList());
		// First we have to remove from available numbers all those associated with fixed mappings
		// We have to do a complete pass on all types of objects because there is no separation of the ranges for identifiers of different types
		for (String resourceType : types) {
			Map<Integer, String> typeMappings = getIdURIFixedMappings(m0Dataset, resourceType);
			if (typeMappings.size() == 0) logger.info("No fixed mappings for type " + resourceType);
			else logger.info("Number of fixed mappings for type " + resourceType + ": " + typeMappings.size() + ", a corresponding amount of available identifiers will be removed");
			for (int index : typeMappings.keySet()) {
				// Add fixed mapping to the global list of all mappings
				mappings.put("http://baseUri/" + resourceType + "s/" + resourceType + "/" + index, typeMappings.get(index));
				int toRemove = Integer.parseInt(StringUtils.substringAfterLast(typeMappings.get(index), "/").substring(1));
				availableNumbers.removeIf(number -> number == toRemove); // Not super-efficient, but the list is not that big
			}
		}
		logger.info("Total number of fixed mappings: " + mappings.size());

		// 2: Attribute remaining identifiers to all resources that don't have a fixed mapping
		for (String resourceType : types) {
			idCounters.put(resourceType, 0); // Initialize identification counter for this type of resources
			// Get the model corresponding to this type of resource
			Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + resourceType + "s");
			int maxNumber = getMaxSequence(m0Model);
			for (int index = 1; index <= maxNumber; index++) {
				String m0URI = "http://baseUri/" + resourceType + "s/" + resourceType + "/" + index;
				if (mappings.containsKey(m0URI)) continue; // Fixed mappings already dealt with
				// The following instruction does not actually add the resource to the model, so the test on the next line will work as expected
				Resource m0Resource = m0Model.createResource(m0URI);
				if (!m0Model.contains(m0Resource, null)) continue; // Verify that M0 resource actually exist
				// At this point, the resource exists and has not a fixed mapping: attribute target URI based on first available number, except for families who use the M0 index
				if ("famille".equals(resourceType)) mappings.put(m0Resource.getURI(), operationResourceURI(Integer.toString(index), resourceType));
				else {
					Integer targetId = availableNumbers.get(0);
					availableNumbers.remove(0);
					mappings.put(m0Resource.getURI(), operationResourceURI(targetId.toString(), resourceType));
				}
				idCounters.put(resourceType, idCounters.get(resourceType) + 1);
				if (idRanges.get(resourceType) > 0) idRanges.put(resourceType, idRanges.get(resourceType) - 1);
			}
			m0Model.close();
			logger.info("Number of new mappings created for type " + resourceType + ": " + idCounters.get(resourceType));
			if (idRanges.get(resourceType) > 0) {
				//idRanges.put(resourceType, idRanges.get(resourceType) - idCounters.get(resourceType));
				// Reserve some available numbers for future new series or operations
				logger.debug("Reserving " + idRanges.get(resourceType) + " identifiers for future instances of type " + resourceType);
				availableNumbers.subList(0, idRanges.get(resourceType)).clear();
			}
			logger.info("Total number of remaining identifiers for new mappings: " + availableNumbers.size());
			logger.debug("Next available identifier is " + availableNumbers.get(0));
		}
		logger.info("Total number of mappings: " + mappings.size());
		return mappings;
	}

	/**
	 * Extracts the informations on the families from the M0 model and converts them according to the target model.
	 * Also adds the references to statistical themes.
	 * 
	 * @return A Jena <code>Model</code> containing the target RDF model for families.
	 */
	public static Model convertFamilies() {

		// Read the M0 model and create the URI mappings if necessary
		readDataset();
		if (allURIMappings == null) allURIMappings = createURIMappings(); // Not indispensable for families
		// Get the family-themes relations
		Map<String, List<String>> familyThemesRelations = getFamilyThemesRelations();

		logger.debug("Extracting the information on families from dataset " + M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "familles");

		// Create the target model and set appropriate prefix mappings
		Model familyModel = ModelFactory.createDefaultModel();
		familyModel.setNsPrefix("rdfs", RDFS.getURI());
		familyModel.setNsPrefix("skos", SKOS.getURI());
		familyModel.setNsPrefix("dcterms", DCTerms.getURI());
		familyModel.setNsPrefix("org", ORG.getURI());
		familyModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");
		// Family M0 URIs take the form http://baseUri/familles/famille/n, where n is an increment strictly inferior to the sequence number
		int familyMaxNumber = getMaxSequence(m0Model);
		logger.debug("Maximum index for families is " + familyMaxNumber);

		// Loop on the family index
		int familyRealNumber = 0;
		for (int familyIndex = 1; familyIndex <= familyMaxNumber; familyIndex++) {
			Resource m0Resource = m0Model.createResource("http://baseUri/familles/famille/" + familyIndex);
			if (!m0Model.contains(m0Resource, null)) continue; // No actual family for the current index
			familyRealNumber++;
			String targetURI = allURIMappings.get(m0Resource.getURI());
			if (targetURI == null) { // Should really not happen
				targetURI = operationResourceURI(Integer.toString(familyIndex), "famille");
				logger.error("No target URI found for M0 family " + m0Resource.getURI() + ", defaulting to " + targetURI);
			}
			Resource targetResource = familyModel.createResource(targetURI, OperationModelMaker.statisticalOperationFamily);
			logger.info("Creating target family " + targetURI + " from M0 resource " + m0Resource.getURI());
			fillLiteralProperties(targetResource, m0Model, m0Resource);
			// Add relation from family to theme(s)
			if (familyThemesRelations.containsKey(targetURI)) {
				for (String themeURI : familyThemesRelations.get(targetURI)) {
					targetResource.addProperty(DCTerms.subject, familyModel.createResource(themeURI));
					logger.debug("Adding theme " + themeURI + " to family");
				}
			} else logger.warn("No statistical theme found for family " + targetURI);
			
		}
		logger.info(familyRealNumber + " families extracted");
		m0Model.close();

		return familyModel;
	}

	/**
	 * Extracts the informations on the series from the M0 model and converts them according to the target model.
	 * 
	 * @return A Jena <code>Model</code> containing the target RDF model for series.
	 */
	public static Model convertSeries() {

		// Read the M0 model and create the URI mappings if necessary
		readDataset();
		if (allURIMappings == null) allURIMappings = createURIMappings();

		logger.debug("Extracting the information on series from dataset " + M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "series");

		// Create the target model and set appropriate prefix mappings
		Model seriesModel = ModelFactory.createDefaultModel();
		seriesModel.setNsPrefix("rdfs", RDFS.getURI());
		seriesModel.setNsPrefix("skos", SKOS.getURI());
		seriesModel.setNsPrefix("dcterms", DCTerms.getURI());
		seriesModel.setNsPrefix("org", ORG.getURI());
		seriesModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");
		// Series M0 URIs take the form http://baseUri/series/serie/n, where n is an increment strictly inferior to the sequence number
		int seriesMaxNumber = getMaxSequence(m0Model);
		logger.debug("Maximum index for series is " + seriesMaxNumber);

		// Loop on series number, but actually not all values of index correspond to existing series so the existence of the resource has to be tested
		int seriesRealNumber = 0;
		for (int seriesIndex = 1; seriesIndex <= seriesMaxNumber; seriesIndex++) {
			// The following instruction does not actually add the resource to the model, so the test on the next line will work as expected
			Resource m0Resource = m0Model.createResource("http://baseUri/series/serie/" + seriesIndex);
			if (!m0Model.contains(m0Resource, null)) continue;
			seriesRealNumber++;
			String targetURI = allURIMappings.get(m0Resource.getURI());
			if (targetURI == null) { // There is definitely a problem if the M0 URI is not in the mappings
				logger.error("No target URI found for M0 series " + m0Resource.getURI());
				continue;
			}
			Resource targetResource = seriesModel.createResource(targetURI, OperationModelMaker.statisticalOperationSeries);
			logger.info("Creating target series " + targetURI + " from M0 resource " + m0Resource.getURI());
			fillLiteralProperties(targetResource, m0Model, m0Resource);
		}
		logger.info(seriesRealNumber + " series extracted");
		m0Model.close();

		return seriesModel;
	}

	/**
	 * Extracts the informations on the operations from the M0 model and restructures them according to the target model.
	 * 
	 * @return A Jena <code>Model</code> containing the target RDF model for operations.
	 */
	public static Model convertOperations() {

		// Read the M0 model and create the URI mappings if necessary
		readDataset();
		if (allURIMappings == null) allURIMappings = createURIMappings();

		logger.debug("Extracting the information on operations from dataset " + M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "operations");

		// Create the target model and set appropriate prefix mappings
		Model operationModel = ModelFactory.createDefaultModel();
		operationModel.setNsPrefix("rdfs", RDFS.getURI());
		operationModel.setNsPrefix("skos", SKOS.getURI());
		operationModel.setNsPrefix("dcterms", DCTerms.getURI());
		operationModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");
		// Operation M0 URIs take the form http://baseUri/operations/operation/n, where n is an increment strictly inferior to the sequence number
		int operationMaxNumber = getMaxSequence(m0Model);
		logger.debug("Maximum index for operations is " + operationMaxNumber);

		// Loop on the operation index
		int operationRealNumber = 0;
		for (int operationIndex = 1; operationIndex <= operationMaxNumber; operationIndex++) {
			Resource m0Resource = m0Model.createResource("http://baseUri/operations/operation/" + operationIndex);
			if (!m0Model.contains(m0Resource, null)) continue; // Cases where the index is not attributed
			operationRealNumber++;
			String targetURI = allURIMappings.get(m0Resource.getURI());
			if (targetURI == null) { // There is definitely a problem if the M0 URI is not in the mappings
				logger.info("No target URI found for M0 operation " + m0Resource.getURI());
				continue;
			}
			Resource targetResource = operationModel.createResource(targetURI, OperationModelMaker.statisticalOperation);
			logger.info("Creating target operation " + targetURI + " from M0 resource " + m0Resource.getURI());
			// Extract TITLE, ALT_LABEL and MILLESIME (or MILESSIME)
			fillLiteralProperties(targetResource, m0Model, m0Resource);
			for (String propertyName : Arrays.asList("MILLESIME", "MILESSIME")) {
				Resource propertyResource = m0Model.createResource(m0Resource.getURI() + "/" + propertyName);
				StmtIterator valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null);
				if (!valueIterator.hasNext()) continue;
				String year = valueIterator.next().getObject().asLiteral().toString().trim();
				if (year.length() == 0) continue;
				if ((year.length() != 4) || (!StringUtils.isNumeric(year))) {
					logger.error("Invalid year value for resource " + m0Resource.getURI() + ": " + year);
				} else { // Assuming there is no M0 resource with both MILLESIME and MILESSIME attributes
					targetResource.addProperty(DCTerms.valid, year); // TODO dct:valid is probably not the best option
				}
			}
		}
		logger.info(operationRealNumber + " operations extracted");
		m0Model.close();

		return operationModel;
	}

	/**
	 * Extracts the informations on the indicators from the M0 model and restructures them according to the target model.
	 * 
	 * @return A Jena <code>Model</code> containing the target RDF model for indicators.
	 */
	public static Model convertIndicators() {

		// Read the M0 model and create the URI mappings if necessary
		readDataset();
		if (allURIMappings == null) allURIMappings = createURIMappings();

		logger.debug("Extracting the information on indicators from dataset " + M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "indicateurs");

		// Create the target model and set appropriate prefix mappings
		Model indicatorModel = ModelFactory.createDefaultModel();
		indicatorModel.setNsPrefix("skos", SKOS.getURI());
		indicatorModel.setNsPrefix("dcterms", DCTerms.getURI());
		indicatorModel.setNsPrefix("prov", PROV.getURI());
		indicatorModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");
		// Indicator M0 URIs take the form http://baseUri/indicateurs/indicateur/n, where n is an increment strictly inferior to the sequence number
		int indicatorMaxNumber = getMaxSequence(m0Model);
		logger.debug("Maximum index for indicators is " + indicatorMaxNumber);

		// Loop on the indicator index
		int indicatorRealNumber = 0;
		for (int indicatorIndex = 1; indicatorIndex <= indicatorMaxNumber; indicatorIndex++) {
			Resource m0Resource = m0Model.createResource("http://baseUri/indicateurs/indicateur/" + indicatorIndex);
			if (!m0Model.contains(m0Resource, null)) continue; // Cases where the index is not attributed
			indicatorRealNumber++;
			String targetURI = allURIMappings.get(m0Resource.getURI());
			if (targetURI == null) { // There is definitely a problem if the M0 URI is not in the mappings
				logger.info("No target URI found for M0 indicator " + m0Resource.getURI());
				continue;
			}
			Resource targetResource = indicatorModel.createResource(targetURI, OperationModelMaker.statisticalIndicator);
			logger.info("Creating indicator " + targetURI + " from M0 resource " + m0Resource.getURI());
			fillLiteralProperties(targetResource, m0Model, m0Resource);
		}
		m0Model.close();

		logger.info(indicatorRealNumber + " indicators extracted, now adding the PRODUCED_FROM, RELATED_TO and REPLACES relations");
		Map<String, List<String>> multipleRelations = extractProductionRelations();
		for (String indicatorM0URI : multipleRelations.keySet()) {
			String indicatorTargetURI = allURIMappings.get(indicatorM0URI);
			if (indicatorTargetURI == null) {
				logger.info("No target URI found for M0 indicator " + indicatorM0URI);
				continue;				
			}
			Resource indicatorResource = indicatorModel.createResource(indicatorTargetURI);
			for (String seriesM0URI : multipleRelations.get(indicatorM0URI)) {
				String seriesTargetURI = allURIMappings.get(seriesM0URI);
				if (seriesTargetURI == null) {
					logger.info("No target URI found for M0 series " + seriesM0URI);
					continue;
				}
				indicatorResource.addProperty(PROV.wasGeneratedBy, indicatorModel.createResource(seriesTargetURI));
				logger.debug("PROV wasGeneratedBy property created from indicator " + indicatorTargetURI + " to series " + seriesTargetURI);
			}
		}
		// RELATED_TO relations (limited to indicators)
		multipleRelations = extractRelations();
		for (String startM0URI : multipleRelations.keySet()) {
			if (!startM0URI.startsWith("http://baseUri/indicateurs")) continue;
			Resource startResource = indicatorModel.createResource(allURIMappings.get(startM0URI));
			for (String endM0URI : multipleRelations.get(startM0URI)) {
				Resource endResource = indicatorModel.createResource(allURIMappings.get(endM0URI));
				startResource.addProperty(RDFS.seeAlso, endResource); // extractRelations returns each relation twice (in each direction)
				logger.debug("See also property created from resource " + startResource.getURI() + " to resource " + endResource.getURI());
			}
		}
		// REPLACES relations (limited to indicators)
		multipleRelations = extractReplacements();
		for (String replacingM0URI : multipleRelations.keySet()) {
			if (!replacingM0URI.startsWith("http://baseUri/indicateurs")) continue;
			Resource replacingResource = indicatorModel.createResource(allURIMappings.get(replacingM0URI));
			for (String replacedM0URI : multipleRelations.get(replacingM0URI)) {
				Resource replacedResource = indicatorModel.createResource(allURIMappings.get(replacedM0URI));
				replacingResource.addProperty(DCTerms.replaces, replacedResource);
				replacedResource.addProperty(DCTerms.isReplacedBy, replacingResource);
				logger.debug("Replacement property created between resource " + replacingResource.getURI() + " replacing resource " + replacedResource.getURI());
			}
		}

		return indicatorModel;
	}

	/**
	 * Fills the basic literal properties for operation-related resources.
	 * 
	 * @param targetResource The resource in the target model.
	 * @param m0Model The M0 model where the information is taken from.
	 * @param m0Resource The origin M0 resource (a SKOS concept).
	 */
	private static void fillLiteralProperties(Resource targetResource, Model m0Model, Resource m0Resource) {
		for (String property : propertyMappings.keySet()) {
			Resource propertyResource = m0Model.createResource(m0Resource.getURI() + "/" + property);
			if (stringProperties.contains(property)) {
				// Start with the string properties that can have a French and an English value (except ALT_LABEL?)
				StmtIterator valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // Find French values (there should be at most one)
				if (valueIterator.hasNext()) {
					// Must go through lexical values to avoid double escaping
					String propertyValue = valueIterator.next().getObject().asLiteral().getLexicalForm().trim();
					if (propertyValue.length() == 0) continue; // Ignore empty values for text properties
					// Remove this is ALT_LABEL should have a language tag
					if ("ALT_LABEL".equals(property)) {
						targetResource.addProperty(propertyMappings.get(property), ResourceFactory.createStringLiteral(propertyValue));
						continue;
					}
					// Create the current property on the target resource, with string value tagged '@fr'
					Literal langValue = ResourceFactory.createLangLiteral(propertyValue, "fr");
					targetResource.addProperty(propertyMappings.get(property), langValue);
				}
				valueIterator = m0Model.listStatements(propertyResource, M0_VALUES_EN, (RDFNode)null); // Find English values (can be zero or one)
				if (valueIterator.hasNext()) {
					// Create the current property on the target resource, with string value tagged '@en'
					String propertyValue = valueIterator.next().getObject().asLiteral().getLexicalForm().trim();
					targetResource.addProperty(propertyMappings.get(property), ResourceFactory.createLangLiteral(propertyValue, "en"));
				}
			} else {
				// In the other properties, select the coded ones (SOURCE_CATEGORY and FREQ_COLL)
				// TODO FREQ_DISS (at least for indicators)? But there is no property mapping for this attribute
				StmtIterator valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null);
				if (!valueIterator.hasNext()) continue;
				// Then process the SOURCE_CATEGORY and FREQ_COLL attributes, values are taken from code lists
				if (("SOURCE_CATEGORY".equals(property)) || ("FREQ_COLL".equals(property))) {
					String frenchLabel = ("SOURCE_CATEGORY".equals(property)) ? "Catégorie de source" : "Fréquence"; // TODO Find better method
					String codeURI = inseeCodeURI(valueIterator.next().getObject().toString(), frenchLabel);
					targetResource.addProperty(propertyMappings.get(property), m0Model.createResource(codeURI));
				}
				// The remaining (object) properties (ORGANISATION, STAKEHOLDERS, REPLACES and RELATED_TO) are processed by dedicated methods.
			}
		}
	}

	/**
	 * Extracts from the current dataset all informations about families, series and operations, and relations between them
	 * 
	 * @return A Jena model containing all the statements.
	 */
	public static Model extractAllOperations() {

		Model operationModel = ModelFactory.createDefaultModel();
		operationModel.setNsPrefix("rdfs", RDFS.getURI());
		operationModel.setNsPrefix("skos", SKOS.getURI());
		operationModel.setNsPrefix("dcterms", DCTerms.getURI());
		operationModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");

		// First add models on families, series and operations (this will read the dataset and create the URI mappings)
		operationModel.add(convertFamilies()).add(convertSeries()).add(convertOperations());

		// Now read the links of various kinds between families, series and operations, starting with hierarchies
		// For readability, we do not verify in this method that the M0 URIs are in the mappings
		Model m0AssociationModel = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, String> simpleRelations = extractHierarchies(m0AssociationModel);
		for (String chilM0dURI : simpleRelations.keySet()) {
			Resource child = operationModel.createResource(allURIMappings.get(chilM0dURI));
			Resource parent = operationModel.createResource(allURIMappings.get(simpleRelations.get(chilM0dURI)));
			child.addProperty(DCTerms.isPartOf, parent);
			parent.addProperty(DCTerms.hasPart, child);
			logger.debug("Hierarchy properties created between child " + child.getURI() + " and parent " + parent.getURI());
		}
		// RELATED_TO relations (excluding indicators)
		Map<String, List<String>> multipleRelations = extractRelations(m0AssociationModel);
		for (String startM0URI : multipleRelations.keySet()) {
			if (startM0URI.startsWith("http://baseUri/indicateurs")) continue;
			Resource startResource = operationModel.createResource(allURIMappings.get(startM0URI));
			for (String endM0URI : multipleRelations.get(startM0URI)) {
				Resource endResource = operationModel.createResource(allURIMappings.get(endM0URI));
				startResource.addProperty(RDFS.seeAlso, endResource); // extractRelations returns each relation twice (in each direction)
				logger.debug("See also property created from resource " + startResource.getURI() + " to resource " + endResource.getURI());
			}
		}
		// REPLACES relations (excluding indicators)
		multipleRelations = extractReplacements(m0AssociationModel);
		for (String replacingM0URI : multipleRelations.keySet()) {
			if (replacingM0URI.startsWith("http://baseUri/indicateurs")) continue; // There is no cross-relation of replacement between operations and indicators
			Resource replacingResource = operationModel.createResource(allURIMappings.get(replacingM0URI));
			for (String replacedM0URI : multipleRelations.get(replacingM0URI)) {
				Resource replacedResource = operationModel.createResource(allURIMappings.get(replacedM0URI));
				replacingResource.addProperty(DCTerms.replaces, replacedResource);
				replacedResource.addProperty(DCTerms.isReplacedBy, replacingResource);
				logger.debug("Replacement property created between resource " + replacingResource.getURI() + " replacing resource " + replacedResource.getURI());
			}
		}
		// Finally, add relations to organizations
		for (OrganizationRole role : OrganizationRole.values()) {
			logger.debug("Creating organizational relations with role " + role.toString());
			multipleRelations = extractOrganizationalRelations(m0AssociationModel, role);
			for (String operationM0URI : multipleRelations.keySet()) {
				Resource operationResource = operationModel.createResource(allURIMappings.get(operationM0URI));
				for (String organizationURI : multipleRelations.get(operationM0URI)) {
					Resource organizationResource = ResourceFactory.createResource(convertM0OrganizationURI(organizationURI));
					operationResource.addProperty(role.getProperty(), organizationResource);
					logger.debug("Organizational relation created between resource " + operationResource.getURI() + " and organization " + organizationResource.getURI());
				}
			}
		}
		m0AssociationModel.close();
		return operationModel;
	}

	/**
	 * Reads all the replacement properties and stores them as a map where the keys are the resources replaced and the values are lists of the resources they replaced.
	 * 
	 * @return A map containing the replacement relations.
	 */
	public static Map<String, List<String>> extractReplacements() {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting the information on replacements from dataset " + M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, List<String>> replacementMappings = extractReplacements(m0Model);
	    m0Model.close();

		return replacementMappings;
	}

	/**
	 * Reads all the replacement properties and stores them as a map where the keys are the resources replacing and the values are lists of the resources they replaced.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractReplacements(Model m0AssociationModel) {
		// The relations are in the 'associations' graph and have the following structure :
		// <http://baseUri/series/serie/12/REPLACES> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/series/serie/13/REMPLACE_PAR> .

		logger.debug("Extracting the information on replacement relations between series or indicators");
		Map<String, List<String>> replacementMappings = new HashMap<String, List<String>>();
		
		if (m0AssociationModel == null) return extractReplacements();
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject URI ends with 'REPLACES' and object URI with 'REMPLACE_PAR'
	        public boolean selects(Statement statement) {
	        	return ((statement.getSubject().getURI().endsWith("REPLACES")) && (statement.getObject().isResource()) && (statement.getObject().asResource().getURI().endsWith("REMPLACE_PAR")));
	        }
	    };
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			// 
			public void accept(Statement statement) {
				String after = StringUtils.removeEnd(statement.getSubject().getURI(), "/REPLACES");
				String before = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/REMPLACE_PAR");
				if (!replacementMappings.containsKey(after)) replacementMappings.put(after, new ArrayList<String>());
				replacementMappings.get(after).add(before);
			}
		});

		return replacementMappings;
	}

	/**
	 * Reads all the relation properties between operation-like resources and stores them as a map.
	 * Each relation will be store twice: one for each direction.
	 * NB: the relations between code lists and codes is not returned.
	 * 
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractRelations() {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting the information on relations from dataset " + M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, List<String>> relationMappings = extractRelations(m0Model);
	    m0Model.close();

		return relationMappings;
	}
	
	/**
	 * Reads all the relation properties between operation-like resources and stores them as a map.
	 * Each relation will be store twice: one for each direction.
	 * NB: the relations between code lists and codes is not returned.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractRelations(Model m0AssociationModel) {

		// The relations are in the 'associations' graph and have the following structure:
		// <http://baseUri/series/serie/99/RELATED_TO> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/series/serie/98/RELATED_TO>

		logger.debug("Extracting the information on relations between series, indicators, etc.");
		Map<String, List<String>> relationMappings = new HashMap<String, List<String>>();

		if (m0AssociationModel == null) return extractRelations();
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject URI ends with 'RELATED_TO' and object URI with 'RELATED_TO'
	        public boolean selects(Statement statement) {
	        	// There are also RELATED_TO relations between code lists and codes in the association model, that must be eliminated
	        	String subjectURI = statement.getSubject().getURI();
	        	if (subjectURI.startsWith("http://baseUri/code")) return false;
	        	return ((subjectURI.endsWith("RELATED_TO")) && (statement.getObject().isResource()) && (statement.getObject().asResource().getURI().endsWith("RELATED_TO")));
	        }
	    };
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			// 
			public void accept(Statement statement) {
				String oneEnd = StringUtils.removeEnd(statement.getSubject().getURI(), "/RELATED_TO");
				String otherEnd = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/RELATED_TO");
				if (!relationMappings.containsKey(oneEnd)) relationMappings.put(oneEnd, new ArrayList<String>());
				relationMappings.get(oneEnd).add(otherEnd);
			}
		});

		return relationMappings;	
	}
	
	/**
	 * Reads all the hierarchies (family -> series or series -> operation) and stores them as a map.
	 * The map keys will be the children and the values the parents, both expressed as M0 URIs.
	 * 
	 * @return A map containing the hierarchies.
	 */
	public static Map<String, String> extractHierarchies() {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting the information on hierarchies from dataset " + M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, String> hierarchyMappings = extractHierarchies(m0Model);
	    m0Model.close();

	    return hierarchyMappings;
	}

	/**
	 * Reads all the hierarchies (family -> series or series -> operation) and stores them as a map.
	 * The map keys will be the children and the values the parents, both expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A map containing the hierarchies.
	 */
	public static Map<String, String> extractHierarchies(Model m0AssociationModel) {

		// The hierarchies are in the 'associations' graph and have the following structure:
		// <http://baseUri/familles/famille/58/ASSOCIE_A> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/series/serie/117/ASSOCIE_A>

		logger.debug("Extracting the information on hierarchies between families, series and operations");
		Map<String, String> hierarchyMappings = new HashMap<String, String>();

		if (m0AssociationModel == null) return extractHierarchies();
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
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String child = StringUtils.removeEnd(statement.getSubject().getURI(), "/ASSOCIE_A");
				String parent = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/ASSOCIE_A");
				// Each series or operation should have at most one parent
				if (hierarchyMappings.containsKey(child)) logger.error("Conflicting parents for " + child + " - " + parent + " and " + hierarchyMappings.get(child));
				else hierarchyMappings.put(child, parent);
			}
		});

		return hierarchyMappings;	
	}

	/**
	 * Reads all the relations of a specified type (production, stakeholding) between operations and organizations and stores them as a map.
	 * The map keys will be the operations and the values the lists of stakeholders, all expressed as M0 URIs.
	 * 
	 * @param organizationRole Role of the organizations to extract: producers or stakeholders.
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractOrganizationalRelations(OrganizationRole organizationRole) {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting the information on relations to organizations from dataset " + M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, List<String>> organizationMappings = extractOrganizationalRelations(m0Model, organizationRole);
	    m0Model.close();

	    return organizationMappings;
	}

	/**
	 * Reads all the relations of a specified type (production, stakeholding) between operations and organizations and stores them as a map.
	 * The map keys will be the operations and the values the lists of organizations, all expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @param organizationRole Role of the organizations to extract: producers or stakeholders.
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractOrganizationalRelations(Model m0AssociationModel, OrganizationRole organizationRole) {

		// The relations between operations and organizations are in the 'associations' graph and have the following structure (same with '/ORGANISATION' for producer):
		// <http://baseUri/series/serie/42/STAKEHOLDERS> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/organismes/organisme/10/STAKEHOLDERS>

		logger.debug("Type of relationship extracted " + organizationRole);
		Map<String, List<String>> organizationMappings = new HashMap<String, List<String>>();
		String suffix = "/" + organizationRole.toString();

		if (m0AssociationModel == null) return extractOrganizationalRelations(organizationRole);
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject and object URIs end with the appropriate suffix
	        public boolean selects(Statement statement) {
	        	return ((statement.getSubject().getURI().endsWith(suffix)) && (statement.getObject().isResource())
	        			&& (statement.getObject().asResource().getURI().startsWith("http://baseUri/organismes")) && (statement.getObject().asResource().getURI().endsWith(suffix)));
	        }
	    };
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String operation = StringUtils.removeEnd(statement.getSubject().getURI(), suffix);
				String organization = StringUtils.removeEnd(statement.getObject().asResource().getURI(), suffix);
				if (!organizationMappings.containsKey(operation)) organizationMappings.put(operation, new ArrayList<String>());
				organizationMappings.get(operation).add(organization);
			}
		});

		return organizationMappings;	
	}

	/**
	 * Reads all the relations stating that an indicator is produced from a series and stores them as a map.
	 * The map keys will be the indicators and the values the lists of series they are produced from, all expressed as M0 URIs.
	 * 
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractProductionRelations() {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting the information on relations between indicators and series from dataset " + M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, List<String>> relationMappings = extractProductionRelations(m0Model);
	    m0Model.close();

	    return relationMappings;
	}

	/**
	 * Reads all the relations stating that an indicator is produced from a series and stores them as a map.
	 * The map keys will be the indicators and the values the lists of series they are produced from, all expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractProductionRelations(Model m0AssociationModel) {

		// The relations between series and indicators are in the 'associations' graph and have the following structure:
		// <http://baseUri/indicateurs/indicateur/27/PRODUCED_FROM> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/series/serie/137/PRODUIT_INDICATEURS>
		// Note: discard cases where PRODUCED_FROM is used instead of PRODUIT_INDICATEURS.

		logger.debug("Extracting 'PRODUCED_FROM/PRODUIT_INDICATEURS' relations between series and indicators");
		Map<String, List<String>> relationMappings = new HashMap<String, List<String>>();

		//if (m0AssociationModel == null) return extractProductionRelations();
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

	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			// 
			public void accept(Statement statement) {
				String indicatorURI = StringUtils.removeEnd(statement.getSubject().getURI(), "/PRODUCED_FROM");
				String seriesURI = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/PRODUIT_INDICATEURS");
				if (!relationMappings.containsKey(indicatorURI)) relationMappings.put(indicatorURI, new ArrayList<String>());
				relationMappings.get(indicatorURI).add(seriesURI);
			}
		});
		logger.debug("Number of 'PRODUCED_FROM' relations found: " + relationMappings.size());
		return relationMappings;
	}

	/**
	 * Reads from an external spreadsheet the relations between the families and the statistical themes and stores them as a map.
	 * The map keys will be the family URIs and the values the lists of theme URIs.
	 * 
	 * @return A map containing the relations or <code>null</code> in case of error.
	 */
	public static Map<String, List<String>> getFamilyThemesRelations() {

		Map<String, List<String>> relationMappings = new HashMap<String, List<String>>();

		Workbook familyThemesWorkbook = null;
		File xlsxFile = new File(FAMILY_THEMES_XLSX_FILE_NAME);
		try {
			logger.info("Reading family-themes relations from Excel file " + xlsxFile.getAbsolutePath());
			familyThemesWorkbook = WorkbookFactory.create(xlsxFile);
		} catch (Exception e) {
			logger.fatal("Error while opening Excel file - " + e.getMessage());
			return null;
		}

		Iterator<Row> rows = familyThemesWorkbook.getSheetAt(0).rowIterator();
		while (rows.hasNext()) {
			Row row = rows.next();
			// Family URI is in column B
			String familyURI = row.getCell(1).toString();
			relationMappings.put(familyURI, new ArrayList<String>());
			// First theme identifier is in column D, never empty
			String themeId = row.getCell(3).toString();
			relationMappings.get(familyURI).add(themeURI(themeId));
			// Second theme identifier is in column E, can be empty
			Cell themeCell = row.getCell(4);
			if (themeCell != null) {
				themeId = themeCell.toString();
				relationMappings.get(familyURI).add(themeURI(themeId));
			}
			logger.debug("Themes registered for family " + familyURI + ": " + relationMappings.get(familyURI));
		}
		try { familyThemesWorkbook.close(); } catch (IOException ignored) { }

		return relationMappings;
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
	 * Checks the URI mappings to detect any duplicates in the target URI.
	 * 
	 * @param mappings The URI mappings (M0 URIs as keys, target URIs as values).
	 */
	public static void checkMappings(Map<String, String> mappings) {

		logger.debug("Checking for duplicate values in the URI mappings"); 
		List<String> values = new ArrayList<String>();
		for (String key : mappings.keySet()) {
			String value = mappings.get(key);
			if (values.contains(value)) logger.error("Duplicate value in mappings: " + value); 
			else values.add(value);
		}
	}

	/**
	 * Reads the complete M0 dataset if it has not been read already.
	 */
	protected static void readDataset() {
		if (m0Dataset == null) {
			m0Dataset = RDFDataMgr.loadDataset(M0_FILE_NAME);
			logger.debug("M0 dataset read from file " + M0_FILE_NAME);
		}
	}

	/**
	 * Reads the mappings between M0 and target URIs for organizations.
	 */
	public static void readOrganizationURIMappings() {

		readDataset();
		organizationURIMappings = new HashMap<String, String>();
		// Read the 'organismes' model and loop through the statements with 'ID_CODE' subjects
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "organismes");
		Model extractModel = M0Extractor.extractAttributeStatements(m0Model, "ID_CODE");
		extractModel.listStatements().forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				// Get the M0 URI (just strip the /ID_CODE part)
				String m0URI = StringUtils.removeEnd(statement.getSubject().toString(), "/ID_CODE");
				// Read the value of the property
				String orgId = statement.getObject().asLiteral().toString();
				// HACK Organization 81 has a weird identifier
				if (m0URI.endsWith("/81")) orgId = "Drees";
				String orgURI = null;
				if ((orgId.length() == 4) && (StringUtils.isNumeric(orgId.substring(1)))) orgURI = inseeUnitURI("DG75-" + orgId);
				else orgURI = organizationURI(orgId);
				organizationURIMappings.put(m0URI, orgURI);
			}
		});
	}

	/**
	 * Converts an M0 organization resource URI into the corresponding target URI.
	 * 
	 * @param m0URI The M0 organization resource URI.
	 * @return The target URI for the resource.
	 */
	public static String convertM0OrganizationURI(String m0URI) {

		if (organizationURIMappings == null) readOrganizationURIMappings();
		if (organizationURIMappings.containsKey(m0URI)) return organizationURIMappings.get(m0URI);
		return null;
	}

	/**
	 * Enumeration of the different roles in which an organization can appear in the M0 model.
	 */
	public enum OrganizationRole {
		PRODUCER,
		STAKEHOLDER;

		@Override
		public String toString() {
			switch(this) {
				case PRODUCER: return "ORGANISATION";
				case STAKEHOLDER: return "STAKEHOLDERS";
				default: return "unknown";
			}
		}

		/** Returns the OWL property associated to the organization role */
		public Property getProperty() {
			switch(this) {
				case PRODUCER: return DCTerms.creator;
				case STAKEHOLDER: return DCTerms.contributor;
				default: return null;
			}
		}
	}
}
