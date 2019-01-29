package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Selector;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.DC;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.DCTypes;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Extends the M0 converter with methods for the conversion of SIMS quality metadata.
 * 
 * @author Franck
 */
public class M0SIMSConverter extends M0Converter {

	public static Logger logger = LogManager.getLogger(M0SIMSConverter.class);

	public static String M0_DOCUMENTATION_BASE_URI = "http://baseUri/documentations/documentation/";
	public static String M0_LINK_BASE_URI = "http://baseUri/liens/lien/";
	public static String M0_DOCUMENT_BASE_URI = "http://baseUri/documents/document/";

	// Base URL for the documents referenced in the SIMS attributes
	public static String SIMS_DOCUMENT_BASE_URI = "https://www.insee.fr/fr/metadonnees/source/fichier/";

	/** The SIMS-FR metadata structure definition */
	protected static OntModel simsFrMSD = null;
	/** The SIMS-FR scheme */
	protected static SIMSFrScheme simsFRScheme = null;
	/** All the references from attributes to links or documents */
	protected static SortedMap<Integer, SortedMap<String, SortedSet<String>>> attributeReferences = null;
	/** The SIMS model for documents */
	protected static Model simsDocumentsModel = null;
	/** The SIMS model for links */
	protected static Model simsLinksModel = null;

	// Will be handy for parsing dates
	final static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

	/**
	 * Converts a list (or all) of M0 'documentation' models to SIMS models.
	 * 
	 * @param m0Ids A <code>List</code> of M0 'documentation' metadata set identifiers, or <code>null</code> to convert all models.
	 * @param namedModels If <code>true</code>, a named model will be created for each identifier, otherwise all models will be merged in the dataset.
	 * @return A Jena dataset containing the models corresponding to the identifiers received.
	 */
	public static Dataset convertToSIMS(List<Integer> m0Ids, boolean namedModels) {

		// We will need the documentation model, the SIMSFr scheme and the SIMSFr MSD
		if (dataset == null) dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentationModel = dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations");
		simsFrMSD = (OntModel) ModelFactory.createOntologyModel().read(Configuration.SIMS_FR_MSD_TURTLE_FILE_NAME);
		simsFRScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));

		// We will also need the documents and links models, as well as all the attribute references to them
		attributeReferences = M0SIMSConverter.extractAllAttributeReferences();
		simsDocumentsModel = convertDocumentsToSIMS();
		simsLinksModel = convertLinksToSIMS();

		// If parameter was null, get the list of all existing M0 'documentation' models
		SortedSet<Integer> docIdentifiers = new TreeSet<Integer>();
		if (m0Ids == null) {
			docIdentifiers = getM0DocumentationIds();
			logger.debug("Converting all M0 'documentation' models to SIMSFr format (" + docIdentifiers.size() + " models)");
		}
		else {
			docIdentifiers.addAll(m0Ids); // Sorts and eliminates duplicates
			logger.debug("Converting a list of M0 'documentation' models to SIMSFr format (" + docIdentifiers.size() + " models)");
		}

		Dataset simsDataset = DatasetFactory.create();
		for (Integer docIdentifier : docIdentifiers) {
			// Extract the M0 model containing the resource of the current documentation
			Model docModel = M0Converter.extractM0ResourceModel(m0DocumentationModel, M0_DOCUMENTATION_BASE_URI + docIdentifier);
			// Convert to SIMS format
			Model simsModel = m0ConvertToSIMS(docModel);
			if (!namedModels) simsDataset.getDefaultModel().add(simsModel);
			else {
				simsDataset.addNamedModel(Configuration.simsReportGraphURI(docIdentifier.toString()), simsModel);
			}
			simsModel.close();
			docModel.close();
		}
		return simsDataset;
	}

	/**
	 * Converts a metadata set from M0 to SIMSFr RDF format.
	 * 
	 * @param m0Model A Jena <code>Model</code> containing the metadata in M0 format.
	 * @return A Jena <code>Model</code> containing the metadata in SIMSFr format.
	 */
	public static Model m0ConvertToSIMS(Model m0Model) {

		// Retrieve base URI (the base resource is a skos:Concept) and the corresponding M0 identifier
		// TODO Secure this part
		Resource m0BaseResource = m0Model.listStatements(null, RDF.type, SKOS.Concept).toList().get(0).getSubject(); // Should raise an exception in case of problem
		String m0Id = m0BaseResource.getURI().substring(m0BaseResource.getURI().lastIndexOf('/') + 1);
		Integer documentNumber = Integer.parseInt(m0Id);

		logger.debug("Creating metadata report model for m0 documentation " + m0Id + ", base M0 model has " + m0Model.size() + " statements");

		Model simsModel = ModelFactory.createDefaultModel();
		simsModel.setNsPrefix("rdf", RDF.getURI());
		simsModel.setNsPrefix("rdfs", RDFS.getURI());
		simsModel.setNsPrefix("xsd", XSD.getURI());
		simsModel.setNsPrefix("dcterms", DCTerms.getURI());
		simsModel.setNsPrefix("skos", SKOS.getURI());
		simsModel.setNsPrefix("insee", Configuration.BASE_INSEE_ONTO_URI);

		// Create the metadata report resource
		Resource report = simsModel.createResource(Configuration.simsReportURI(m0Id), simsModel.createResource(Configuration.SDMX_MM_BASE_URI + "MetadataReport"));
		report.addProperty(RDFS.label, simsModel.createLiteral("Metadata report " + m0Id, "en"));
		report.addProperty(RDFS.label, simsModel.createLiteral("Rapport de métadonnées " + m0Id, "fr"));
		logger.debug("MetadataReport resource created for report: " + report.getURI());

		// Shortcut to the list of document and link references on attributes of the current documentation
		SortedMap<String, SortedSet<String>> documentReferences = attributeReferences.get(documentNumber);

		// For each possible SIMSFr entry, check if the M0 model contains corresponding information and in that case convert it
		for (SIMSFrEntry entry : simsFRScheme.getEntries()) {
			// Create a m0 resource corresponding to the SIMSFr entry and check if the resource has values in M0 (French values are sine qua non)
			Resource m0EntryResource = ResourceFactory.createResource(m0BaseResource.getURI() + "/" + entry.getCode());
			logger.debug("Looking for the presence of SIMS attribute " + entry.getCode() + " (M0 URI: " + m0EntryResource + ")");
			// Query for the list of valued of the M0 resource
			List<RDFNode> objectValues = m0Model.listObjectsOfProperty(m0EntryResource, M0_VALUES).toList();
			if (objectValues.size() == 0) {
				logger.debug("No value found in the M0 documentation model for SIMSFr attribute " + entry.getCode());
				continue;
			}
			if (objectValues.size() > 1) { // TODO Some coded attributes (survey unit, collection mode) can actually be multi-valued
				// Several values for the resource, we have a problem
				logger.error("Error: there are multiple values in the M0 documentation model for SIMSFr attribute " + entry.getCode());
				continue;
			}
			// If we arrived here, we have one value, but it can be empty (including numerous cases where the value is just new line characters)
			String stringValue = objectValues.get(0).asLiteral().getString().trim().replaceAll("^\n", ""); // TODO Check cases where value is "\n\n"
			if (stringValue.length() == 0) {
				logger.debug("Empty value found in the M0 documentation model for SIMSFr attribute " + entry.getCode() + ", ignoring");
				continue;
			}
			logger.debug("Non-empty value found in the M0 documentation model for SIMSFr attribute " + entry.getCode());
			// Get the metadata attribute property from the MSD and get its range
			String propertyURI = Configuration.simsAttributePropertyURI(entry, false);
			OntProperty metadataAttributeProperty = simsFrMSD.getOntProperty(propertyURI);
			if (metadataAttributeProperty == null) { // This should not happen
				logger.error("Error: property " + propertyURI + " not found in the SIMSFr MSD");
				continue;
			}
			// If specified, create a reported attribute (otherwise, the metadata attribute properties will be attached to the report)
			Resource targetResource = null;
			if (Configuration.CREATE_REPORTED_ATTRIBUTES) {
				String reportedAttributeURI = Configuration.simsReportedAttributeURI(m0Id, entry.getCode());
				targetResource = simsModel.createResource(reportedAttributeURI, SIMS_REPORTED_ATTRIBUTE);
				targetResource.addProperty(simsModel.createProperty(Configuration.SDMX_MM_BASE_URI + "metadataReport"), report);
			} else targetResource = report;

			Statement rangeStatement = metadataAttributeProperty.getProperty(RDFS.range);
			Resource propertyRange = (rangeStatement == null) ? null : rangeStatement.getObject().asResource();
			logger.debug("Target property is " + metadataAttributeProperty + " with range " + propertyRange);
			if (propertyRange.equals(DCTypes.Text)) {
				// We are in the case of a 'text + seeAlso...' object
				Resource objectResource = simsModel.createResource(Configuration.simsFrRichText(m0Id, entry), DCTypes.Text);
				objectResource.addProperty(RDF.value, simsModel.createLiteral(stringValue, "fr"));
				targetResource.addProperty(metadataAttributeProperty, objectResource);
				// We search for references to documents or links attached to this attribute
				SortedSet<String> thisAttributeReferences = (documentReferences == null) ? null : documentReferences.get(entry.getCode());
				if (thisAttributeReferences != null) {
					for (String refURI : thisAttributeReferences) {}
				}
			}
			else if (propertyRange.equals(SIMS_REPORTED_ATTRIBUTE)) {
				// Just a placeholder for now, the case does not seem to exist in currently available data
				targetResource.addProperty(metadataAttributeProperty, simsModel.createResource(SIMS_REPORTED_ATTRIBUTE));
			}
			else if (propertyRange.equals(XSD.xstring)) {
				targetResource.addProperty(metadataAttributeProperty, simsModel.createLiteral(stringValue, "fr"));
				// See if there is an English version
				objectValues = m0Model.listObjectsOfProperty(m0EntryResource, M0_VALUES_EN).toList();
				if (objectValues.size() == 0) {
					stringValue = objectValues.get(0).asLiteral().getString().trim().replaceAll("^\n", "");
					if (stringValue.length() > 0) targetResource.addProperty(metadataAttributeProperty, simsModel.createLiteral(stringValue, "en"));
				}
			}
			else if (propertyRange.equals(XSD.date)) {
				// Try to parse the string value as a date (yyyy-MM-dd seems to be used in the documentations graph)
				try {
					dateFormat.parse(stringValue); // Just to make sure we have a valid date
					targetResource.addProperty(metadataAttributeProperty, simsModel.createTypedLiteral(stringValue, XSDDatatype.XSDdate));
				} catch (ParseException e) {
					logger.error("Unparseable date value " + stringValue + " for M0 resource " + m0EntryResource.getURI());
				}
			}
			else if (propertyRange.equals(DQV_METRIC)) {
				// This case should not exist, since quality indicators have been filtered out
				logger.error("Property range should not be equal to dqv:Metric");
			}
			else {
				// The only remaining case should be code list, with the range equal to the concept associated to the code list
				String propertyRangeString = propertyRange.getURI();
				if (!propertyRangeString.startsWith(Configuration.INSEE_CODE_CONCEPTS_BASE_URI)) logger.error("Unrecognized property range: " + propertyRangeString);
				else {
					// We don't verify at this stage that the value is a valid code in the code list, but just sanitize the value (by taking the first word) to avoid URI problems
					String sanitizedCode = (stringValue.indexOf(' ') == -1) ? stringValue : stringValue.split(" ", 2)[0];
					String codeURI = Configuration.INSEE_CODES_BASE_URI + StringUtils.uncapitalize(propertyRangeString.substring(propertyRangeString.lastIndexOf('/') + 1)) + "/" + sanitizedCode;
					targetResource.addProperty(metadataAttributeProperty, simsModel.createResource(codeURI));
					logger.debug("Code list value " + codeURI + " assigned to attribute property");
				}
			}
		}
	
		return simsModel;
	}

	/**
	 * Reads all the relations between SIMS metadata sets and series and operations (and possibly indicators), and returns them as a map.
	 * The map keys will be the SIMS 'documentation' and the values the series, operation or indicator, both expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A map containing the attachment relations.
	 */
	public static Map<String, String> extractSIMSAttachments(boolean includeIndicators) {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting the information on SIMS metadata sets attachment from dataset " + Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, String> attachmentMappings = extractSIMSAttachments(m0Model, includeIndicators);
	    m0Model.close();

	    return attachmentMappings;
	}

	/**
	 * Reads all the relations between SIMS metadata sets and series and operations (and possibly indicators), and returns them as a map.
	 * The map keys will be the SIMS 'documentation' and the values the series, operation or indicator, both expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @param includeIndicators If <code>true</code>, the attachments to indicators will also be returned, otherwise only series and operations are considered.
	 * @return A map containing the attachment relations.
	 */
	public static Map<String, String> extractSIMSAttachments(Model m0AssociationModel, boolean includeIndicators) {

		// The attachment relations are in the 'associations' graph and have the following structure:
		// <http://baseUri/documentations/documentation/1527/ASSOCIE_A> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/operations/operation/1/ASSOCIE_A>

		logger.debug("Extracting the information on attachment between SIMS metadata sets and series or operations");
		Map<String, String> attachmentMappings = new HashMap<String, String>();

		if (m0AssociationModel == null) return extractSIMSAttachments(includeIndicators);
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
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
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String simsSet = StringUtils.removeEnd(statement.getSubject().getURI(), "/ASSOCIE_A");
				String operation = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/ASSOCIE_A");
				// We can check that each operation or series has not more than one SIMS metadata set attached
				if (attachmentMappings.containsValue(operation)) logger.warn("Several SIMS metadata sets are attached to " + operation);
				// Each SIMS metadata set should be attached to only one series/operation
				if (attachmentMappings.containsKey(simsSet)) logger.error("SIMS metadata set " + simsSet + " is attached to both " + operation + " and " + attachmentMappings.get(simsSet));
				else attachmentMappings.put(simsSet, operation);
			}
		});

		return attachmentMappings;	
	}

	/**
	 * Returns the set of documentation identifiers in the M0 'documentations' model of the current dataset.
	 * 
	 * @return The set of identifiers as integers in ascending order.
	 */
	public static SortedSet<Integer> getM0DocumentationIds() {

		if (dataset == null) dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		logger.debug("Listing M0 documentation identifiers from dataset " + Configuration.M0_FILE_NAME);

		Model m0 = dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations");
		SortedSet<Integer> m0DocumentIdSet = getM0DocumentationIds(dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations"));
		m0.close();
		return m0DocumentIdSet;
	}

	/**
	 * Returns the set of documentation identifiers in a M0 'documentations' model.
	 * 
	 * @param m0DocumentationModel The M0 'documentations' model.
	 * @return The set of identifiers as integers in ascending order.
	 */
	private static SortedSet<Integer> getM0DocumentationIds(Model m0DocumentationModel) {

		SortedSet<Integer> m0DocumentIdSet = new TreeSet<Integer>();

		ResIterator subjectsIterator = m0DocumentationModel.listSubjects();
		while (subjectsIterator.hasNext()) {
			String m0DocumentationURI = subjectsIterator.next().getURI();
			// Documentation URIs will typically look like http://baseUri/documentations/documentation/1608/ASSOCIE_A
			String m0DocumentationId = m0DocumentationURI.substring(M0_DOCUMENTATION_BASE_URI.length()).split("/")[0];
			// Series identifiers are integers (but careful with the sequence number)
			try {
				m0DocumentIdSet.add(Integer.parseInt(m0DocumentationId));
			} catch (NumberFormatException e) {
				// Should be the sequence number resource: http://baseUri/documentations/documentation/sequence
				if (!("sequence".equals(m0DocumentationId))) logger.error("Invalid documentation URI: " + m0DocumentationURI);
			}
		}
		logger.debug("Found a total of " + m0DocumentIdSet.size() + " documentations in the M0 model");

		return m0DocumentIdSet;
	}

	/**
	 * Converts the information on external links from a model in M0 format to a model in the target format.
	 * 
	 * @return The model in the target format containing the information on external links.
	 */
	public static Model convertLinksToSIMS() {

		readDataset();
		Model m0LinkModel = dataset.getNamedModel("http://rdf.insee.fr/graphe/liens");
		Model simsLinkModel = ModelFactory.createDefaultModel();
		simsLinkModel.setNsPrefix("foaf", FOAF.getURI());
		simsLinkModel.setNsPrefix("dc", DC.getURI());
		simsLinkModel.setNsPrefix("rdfs", RDFS.getURI());
		simsLinkModel.setNsPrefix("schema", "http://schema.org/");

		Map<String, Property> attributeMappings = new HashMap<String, Property>();
		attributeMappings.put("TITLE", RDFS.label);
		attributeMappings.put("TYPE", RDFS.comment); // Should be eventually replaced by SUMMARY
		attributeMappings.put("SUMMARY", RDFS.comment); // For now, both attributes map to the same property (they are not filled at the same time)
		attributeMappings.put("URI", ResourceFactory.createProperty("http://schema.org/url"));

		// The direct attributes for the links are URI, TITLE and SUMMARY (or TYPE)
		// First get the mapping between links and language tags (and take a copy of the keys for verifications below)
		SortedMap<Integer, String> linkLanguages = getLanguageTags(true);
		List<Integer> linkNumbers = new ArrayList<>(linkLanguages.keySet());

		// First pass through the M0 model to create the foaf:Document instances (links are SKOS concepts in M0)
		Selector selector = new SimpleSelector(null, RDF.type, SKOS.Concept);
		m0LinkModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				Integer linkNumber = 0;
				try {
					linkNumber = Integer.parseInt(StringUtils.substringAfterLast(statement.getSubject().getURI(), "/"));
					logger.info("Creating FOAF document for link number " + linkNumber);
				} catch (Exception e) {
					logger.error("Unparseable URI for a link M0 concept: cannot extract link number");
					return;
				}
				Resource linkResource = simsLinkModel.createResource(Configuration.linkURI(linkNumber), FOAF.Document);
				// We can add a dc:language property at this stage
				if (linkLanguages.containsKey(linkNumber)) {
					linkResource.addProperty(DC.language, linkLanguages.get(linkNumber));
					linkNumbers.remove(linkNumber); // So we can check at the end if there are missing links
				} else logger.warn("Cannot determine language for link number " + linkNumber);
			}
		});
		for (Integer missingLink : linkNumbers) logger.warn("Link number " + missingLink + " has a language tag but is missing from model");

		// Now we can iterate on the 'M0_VALUES' predicates to get the other properties of the link (NB: no 'M0_VALUES_EN' in the M0 link model)
		StmtIterator statementIterator = m0LinkModel.listStatements(null, M0_VALUES, (RDFNode) null);
		statementIterator.forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String variablePart = statement.getSubject().toString().replace(M0_LINK_BASE_URI, "");
				if (variablePart.length() == statement.getSubject().toString().length()) logger.warn("Unexpected subject URI in statement " + statement);
				String attributeName = variablePart.split("/")[1];
				if (!attributeMappings.keySet().contains(attributeName)) return;
				Integer linkNumber = Integer.parseInt(variablePart.split("/")[0]);
				String languageTag = linkLanguages.get(linkNumber);
				if (languageTag == null) languageTag = "fr"; // Take 'fr' as default
				Resource linkResource = simsLinkModel.createResource(Configuration.linkURI(linkNumber));
				if ("URI".equals(attributeName)) linkResource.addProperty(attributeMappings.get(attributeName), simsLinkModel.createResource(statement.getObject().toString()));
				else linkResource.addProperty(attributeMappings.get(attributeName), simsLinkModel.createLiteral(statement.getObject().toString(), languageTag));
			}
		});
		// We check that all subjects are foaf:Documents (ie: have been created in the first pass)
		simsLinkModel.listSubjects().forEachRemaining(new Consumer<Resource>() {
			@Override
			public void accept(Resource link) {
				if (!simsLinkModel.contains(link, RDF.type, FOAF.Document)) logger.warn("Link " + link.getURI() + " not defined as FOAF Document");
			}});

		return simsLinkModel; 
	}

	/**
	 * Converts the information on external documents from a model in M0 format to a model in the target format.
	 * 
	 * @return The model in the target format containing the information on external documents.
	 */
	public static Model convertDocumentsToSIMS() {

		readDataset();
		Model m0DocumentModel = dataset.getNamedModel("http://rdf.insee.fr/graphe/documents");
		Model simsDocumentModel = ModelFactory.createDefaultModel();
		simsDocumentModel.setNsPrefix("xsd", XSD.getURI());
		simsDocumentModel.setNsPrefix("foaf", FOAF.getURI());
		simsDocumentModel.setNsPrefix("dc", DC.getURI());
		simsDocumentModel.setNsPrefix("rdfs", RDFS.getURI());
		simsDocumentModel.setNsPrefix("schema", "http://schema.org/");
		simsDocumentModel.setNsPrefix("pav", "http://purl.org/pav/");
		Map<String, Property> propertyMappings = new HashMap<String, Property>();
		// TYPE, FORMAT and TAILLE are ignored
		propertyMappings.put("TITLE", RDFS.label);
		propertyMappings.put("URI", ResourceFactory.createProperty("http://schema.org/url"));
		propertyMappings.put("DATE", ResourceFactory.createProperty("http://purl.org/pav/lastRefreshedOn"));

		// The direct attributes for the documents are URI, TITLE and DATE/DATE_PUBLICATION
		// First get the mapping between documents and language tags (and take a copy of the keys for verifications below)
		SortedMap<Integer, String> documentLanguages = getLanguageTags(false);
		List<Integer> documentNumbers = new ArrayList<>(documentLanguages.keySet());
		// We will also need the value of the 'date' attribute (calculated from DATE and DATE_PUBLICATION
		SortedMap<Integer, Date> documentDates = getDocumentDates(m0DocumentModel);

		// First pass through the M0 model to create the foaf:Document instances (documents are SKOS concepts in M0)
		Selector selector = new SimpleSelector(null, RDF.type, SKOS.Concept);
		m0DocumentModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				Integer documentNumber = 0;
				try {
					documentNumber = Integer.parseInt(StringUtils.substringAfterLast(statement.getSubject().getURI(), "/"));
					logger.info("Creating FOAF document for document number " + documentNumber);
				} catch (Exception e) {
					logger.error("Unparseable URI for a link M0 concept: cannot extract document number");
					return;
				}
				Resource documentResource = simsDocumentModel.createResource(Configuration.documentURI(documentNumber), FOAF.Document);
				// We can add a dc:language property at this stage
				if (documentLanguages.containsKey(documentNumber)) {
					documentResource.addProperty(DC.language, documentLanguages.get(documentNumber));
					documentNumbers.remove(documentNumber); // So we can check at the end if there are missing links
				} else logger.warn("Cannot determine language for document number " + documentNumber);
				// We can also add the 'date' property
				if (documentDates.containsKey(documentNumber)) {
					String dateString = dateFormat.format(documentDates.get(documentNumber));
					Literal dateLiteral = simsDocumentModel.createTypedLiteral(dateString, XSDDatatype.XSDdate);
					documentResource.addProperty(propertyMappings.get("DATE"), dateLiteral);
				}
			}
		});

		// Now we can iterate on the 'M0_VALUES' predicates to get the other properties of the document (NB: no 'M0_VALUES_EN' in the M0 link model)
		// That is actually only TITLE and URI for now.
		StmtIterator statementIterator = m0DocumentModel.listStatements(null, M0_VALUES, (RDFNode) null);
		statementIterator.forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String variablePart = statement.getSubject().toString().replace(M0_DOCUMENT_BASE_URI, "");
				if (variablePart.length() == statement.getSubject().toString().length()) logger.warn("Unexpected subject URI in statement " + statement);
				String attributeName = variablePart.split("/")[1];
				if (!propertyMappings.keySet().contains(attributeName)) return;
				if (attributeName.startsWith("DATE")) return; // Already done above
				Integer documentNumber = Integer.parseInt(variablePart.split("/")[0]);
				String languageTag = documentLanguages.get(documentNumber);
				if (languageTag == null) languageTag = "fr"; // Take 'fr' as default
				Resource documentResource = simsDocumentModel.createResource(Configuration.documentURI(documentNumber));
				if ("URI".equals(attributeName)) {
					String documentURI = SIMS_DOCUMENT_BASE_URI + statement.getObject().toString();
					documentResource.addProperty(propertyMappings.get(attributeName), simsDocumentModel.createResource(documentURI));
				}
				else documentResource.addProperty(propertyMappings.get(attributeName), simsDocumentModel.createLiteral(statement.getObject().toString(), languageTag));
			}
		});
		// We check that all subjects are foaf:Documents (ie: have been created in the first pass)
		simsDocumentModel.listSubjects().forEachRemaining(new Consumer<Resource>() {
			@Override
			public void accept(Resource document) {
				if (!simsDocumentModel.contains(document, RDF.type, FOAF.Document)) logger.warn("Document " + document.getURI() + " not defined as FOAF Document");
			}});

		return simsDocumentModel; 
	}

	/**
	 * Reads in a given 'associations' model all the associations between SIMS attributes in all documentations and all links stores them as a map.
	 * The map keys will be the documentation identifiers and the values will be maps with attribute names as keys and list of link or document URIs as values.
	 * Example: <1580, <SEE_ALSO, <http://id.insee.fr/documents/page/54, http://id.insee.fr/documents/document/55>>>
	 * This method actually merges the results from the more specialized methods that follow.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A map containing the relations.
	 */
	public static SortedMap<Integer, SortedMap<String, SortedSet<String>>> extractAllAttributeReferences() {

		readDataset();
		logger.debug("Extracting the information on relations between SIMS properties and link or document objects from dataset " + Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");

		return extractAllAttributeReferences(m0Model);
	}

	/**
	 * Reads in the current 'associations' model all the associations between SIMS attributes in all documentations and all links stores them as a map.
	 * The map keys will be the documentation identifiers and the values will be maps with attribute names as keys and list of link or document numbers as values.
	 * Example: <1580, <SEE_ALSO, <http://id.insee.fr/documents/page/54, http://id.insee.fr/documents/document/55>>>
	 * This method actually merges the results from the more specialized methods that follow.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A map containing the relations.
	 */
	public static SortedMap<Integer, SortedMap<String, SortedSet<String>>> extractAllAttributeReferences(Model m0AssociationModel) {

		List<SortedMap<Integer, SortedMap<String, SortedSet<String>>>> referenceMappingsList = new ArrayList<SortedMap<Integer, SortedMap<String, SortedSet<String>>>>();

		referenceMappingsList.add(extractAttributeReferences(m0AssociationModel, "fr", true));
		referenceMappingsList.add(extractAttributeReferences(m0AssociationModel, "fr", false));
		referenceMappingsList.add(extractAttributeReferences(m0AssociationModel, "en", true));
		referenceMappingsList.add(extractAttributeReferences(m0AssociationModel, "en", true));

		return mergeAttributeReferences(referenceMappingsList);	
	}

	/**
	 * Reads in the current 'associations' model all the associations between SIMS attributes in all documentations and link or document objects in a given language and stores them as a map.
	 * The map keys will be the documentation identifiers and the values will be maps with attribute names as keys and list of link or document URIs as values.
	 * Example: <1580, <SEE_ALSO, <http://id.insee.fr/documents/page/54, http://id.insee.fr/documents/page/55>>>
	 * 
	 * @param language The language tag corresponding to the language of the link (should be 'fr' or 'en', defaults to 'fr').
	 * @param links A boolean specifying if the tags returns should concern links (<code>true</code>) or documents.
	 * @return A map containing the relations.
	 */
	public static SortedMap<Integer, SortedMap<String, SortedSet<String>>> extractAttributeReferences(String language, boolean links) {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting the information on relations between SIMS properties and link objects from dataset " + Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		SortedMap<Integer, SortedMap<String, SortedSet<String>>> linkMappings = extractAttributeReferences(m0Model, language, links);
	    m0Model.close();

	    return linkMappings;
	}

	/**
	 * Reads all the associations between SIMS attributes in all documentations and link objects in a given language and stores them as a map.
	 * The map keys will be the documentation identifiers and the values will be maps with attribute names as keys and list of link or document URIs as values.
	 * Example: <1580, <SEE_ALSO, <http://id.insee.fr/documents/page/54, http://id.insee.fr/documents/page/55>>>
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @param language The language tag corresponding to the language of the link (should be 'fr' or 'en', defaults to 'fr').
	 * @param links A boolean specifying if the tags returns should concern links (<code>true</code>) or documents.
	 * @return A map containing the relations.
	 */
	public static SortedMap<Integer, SortedMap<String, SortedSet<String>>> extractAttributeReferences(Model m0AssociationModel, String language, boolean links) {

		// The relations between SIMS properties and link/documents objects are in the 'associations' graph and have the following structure (replace by relatedToGb for English):
		// <http://baseUri/documentations/documentation/1580/SEE_ALSO> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/liens/lien/54/SEE_ALSO> .

		Property associationProperty = "en".equalsIgnoreCase(language) ? M0_RELATED_TO_EN : M0_RELATED_TO;
		final String m0BaseURI = (links) ? M0_LINK_BASE_URI : M0_DOCUMENT_BASE_URI;
		final String referenceType = (links) ? "link" : "document";

		logger.debug("Extracting relations between SIMS attributes and " + referenceType + " objects for language '" + language + "'");
		SortedMap<Integer, SortedMap<String, SortedSet<String>>> referenceMappings = new TreeMap<Integer, SortedMap<String, SortedSet<String>>>();

		// Will select triples of the form indicated above
		Selector selector = new SimpleSelector(null, associationProperty, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject and object URIs conform to what we expect
	        public boolean selects(Statement statement) {
	        	return ((statement.getSubject().getURI().startsWith(M0_DOCUMENTATION_BASE_URI)) && (statement.getObject().isResource())
	        			&& (statement.getObject().asResource().getURI().startsWith(m0BaseURI)));
	        }
	    };
	    // Go through the selected triples and fill the map to return
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				// The link/document identifier and SIMS attribute are the two last elements of the link/document URI
				String[] pathElements = statement.getObject().asResource().getURI().replace(m0BaseURI, "").split("/");
				// Check that the URI contains an attribute name and that the attributes in both subject and object URIs are the same
				if ((pathElements.length != 2) || (!pathElements[1].equals(StringUtils.substringAfterLast(statement.getSubject().getURI(), "/")))) {
					logger.error("Unexpected statement ignored: " + statement);
					return;
				}
				// Hopefully the identifiers are really integers
				try {
					Integer referenceNumber = Integer.parseInt(pathElements[0]);
					Integer documentationNumber = Integer.parseInt(statement.getSubject().getURI().replace(M0_DOCUMENTATION_BASE_URI, "").split("/")[0]);
					String attributeName = pathElements[1];
					// HACK: some associations are made on the 'ASSOCIE_A' attribute, which is not a SIMS attribute, we don't want those associations
					if ("ASSOCIE_A".equals(attributeName)) return;
					// Update the map with the information from the current triple
					if (!referenceMappings.containsKey(documentationNumber)) referenceMappings.put(documentationNumber, new TreeMap<String, SortedSet<String>>());
					Map<String, SortedSet<String>> attributeMappings = referenceMappings.get(documentationNumber);
					if (!attributeMappings.containsKey(attributeName)) attributeMappings.put(attributeName, new TreeSet<String>());
					String referenceURI = links ? Configuration.linkURI(referenceNumber) : Configuration.documentURI(referenceNumber);
					attributeMappings.get(attributeName).add(referenceURI);
				} catch (Exception e) {
					logger.error("Statement ignored (invalid integer): " + statement);
					return;
				}
			}
		});

		return referenceMappings;
	}

	/**
	 * Merges a list of attribute references maps into a unique map.
	 * 
	 * @param referenceMappingsList A list of attribute references maps.
	 * @return A consolidated map of attribute references.
	 */
	public static SortedMap<Integer, SortedMap<String, SortedSet<String>>> mergeAttributeReferences(List<SortedMap<Integer, SortedMap<String, SortedSet<String>>>> referenceMappingsList) {

		SortedMap<Integer, SortedMap<String, SortedSet<String>>> mergedReferenceMappings = new TreeMap<Integer, SortedMap<String, SortedSet<String>>>();

		for (SortedMap<Integer, SortedMap<String, SortedSet<String>>> referenceMappings : referenceMappingsList) {
			for (Integer documentationNumber : referenceMappings.keySet()) {
				if (mergedReferenceMappings.containsKey(documentationNumber)) {
					for (String attributeName : referenceMappings.get(documentationNumber).keySet()) {
						if (mergedReferenceMappings.get(documentationNumber).containsKey(attributeName)) {
							mergedReferenceMappings.get(documentationNumber).get(attributeName).addAll(referenceMappings.get(documentationNumber).get(attributeName));
						}
						else mergedReferenceMappings.get(documentationNumber).put(attributeName, referenceMappings.get(documentationNumber).get(attributeName));
					}
				} else mergedReferenceMappings.put(documentationNumber, referenceMappings.get(documentationNumber));
			}
		}

		return mergedReferenceMappings;
	}

	/**
	 * Returns the languages associated to the different links or documents in the default dataset.
	 * 
	 * @param links A boolean specifying if the tags returns should concern links (<code>true</code>) or documents.
	 * @return A map whose keys are the link or document numbers and the values the language tags.
	 */
	public static SortedMap<Integer, String> getLanguageTags(boolean links) {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting list of links with associated language from dataset " + Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		SortedMap<Integer, String> languageTags = getLanguageTags(m0Model, links);
	    m0Model.close();

	    return languageTags;
	}

	/**
	 * Returns the languages associated to the different links or documents.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @param links A boolean specifying if the tags returns should concern links (<code>true</code>) or documents.
	 * @return A map whose keys are the link or document numbers and the values the language tags.
	 */
	public static SortedMap<Integer, String> getLanguageTags(Model m0AssociationModel, boolean links) {

		final String m0BaseURI = ((links) ? M0_LINK_BASE_URI : M0_DOCUMENT_BASE_URI);

		logger.debug("Extracting language tag for each " + ((links) ? "link" : "document"));

		SortedMap<Integer, String> languageTags = new TreeMap<Integer, String>();

		// Will select triples corresponding to French links or documents
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return ((statement.getSubject().getURI().startsWith(M0_DOCUMENTATION_BASE_URI)) && (statement.getObject().isResource())
	        			&& (statement.getObject().asResource().getURI().startsWith(m0BaseURI)));
	        }
	    };
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				// The link or document identifier is the penultimate element of the URI
				String[] pathElements = statement.getObject().asResource().getURI().replace(m0BaseURI, "").split("/");
				if (pathElements.length != 2) return; // Avoid weird cases
				try {
					Integer objectNumber = Integer.parseInt(pathElements[0]);
					if (!languageTags.containsKey(objectNumber)) languageTags.put(objectNumber, "fr");
				} catch (Exception e) {
					logger.error("Statement ignored (invalid integer): " + statement);
					return;
				}			
			}
		});

		// Will select triples corresponding to English links or document
		selector = new SimpleSelector(null, M0_RELATED_TO_EN, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return ((statement.getSubject().getURI().startsWith(M0_DOCUMENTATION_BASE_URI)) && (statement.getObject().isResource())
	        			&& (statement.getObject().asResource().getURI().startsWith(m0BaseURI)));
	        }
	    };
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String[] pathElements = statement.getObject().asResource().getURI().replace(m0BaseURI, "").split("/");
				if (pathElements.length != 2) return;
				try {
					Integer objectNumber = Integer.parseInt(pathElements[0]);
					if (!languageTags.containsKey(objectNumber)) languageTags.put(objectNumber, "en");
					else {
						if (!"fr".equals(languageTags.get(objectNumber))) logger.warn(((links) ? "Link" : "Document") + " number " + objectNumber + " is both English and French");
					}
				} catch (Exception e) {
					logger.error("Statement ignored (invalid integer): " + statement);
					return;
				}			
			}
		});

		return languageTags;
	}

	/**
	 * Returns the dates of publication for each document.
	 * Specification is DATE_PUBLICATION if existing, otherwise DATE if existing.
	 * 
	 * @param m0DocumentModel The M0 'documents' model where the information should be read.
	 * @return A map whose keys are the document numbers and the values the dates.
	 */
	public static SortedMap<Integer, Date> getDocumentDates(Model m0DocumentModel) {

		// In the 'documents' M0 model, dates are of the form dd/MM/yyyy (exceptionally dd-MM-yyyy)
		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");

		SortedMap<Integer, Date> documentDates = new TreeMap<>();
		Selector selector = new SimpleSelector(null, M0Converter.M0_VALUES, (RDFNode) null);
		m0DocumentModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String documentDateURI = statement.getSubject().getURI();
				if ((!documentDateURI.endsWith("/DATE_PUBLICATION")) && (!documentDateURI.endsWith("/DATE"))) return;
				String dateValue = statement.getObject().toString().replace('-', '/').trim();
				if (dateValue.length() == 0) return;
				Integer documentNumber = Integer.parseInt(StringUtils.substringAfterLast(StringUtils.substringBeforeLast(documentDateURI, "/"), "/"));
				Date date = null;
				try {
					date = dateFormat.parse(dateValue);
				} catch (ParseException e) {
					logger.error("Unparseable date value: '" + dateValue + "' for document number " + documentNumber);
					return;
				}
				if (documentDateURI.endsWith("/DATE_PUBLICATION")) documentDates.put(documentNumber, date);
				else {
					// DATE only stored if no DATE_PUBLICATION
					if (!documentDates.containsKey(documentNumber)) documentDates.put(documentNumber, date);
				}
			}
		});

		return documentDates;
	}
}
