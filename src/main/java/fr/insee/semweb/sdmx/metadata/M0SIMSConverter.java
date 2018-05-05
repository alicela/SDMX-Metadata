package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Selector;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class M0SIMSConverter extends M0Converter {

	public static Logger logger = LogManager.getLogger(M0SIMSConverter.class);

	public static String M0_DOCUMENTATION_BASE_URI = "http://baseUri/documentations/documentation/";

	/** The SIMS-FR metadata structure definition */
	protected static OntModel simsFrMSD = null;
	/** The SIMS-FR scheme */
	protected static SIMSFrScheme simsFRScheme = null;

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
			Model docModel = extractM0ResourceModel(m0DocumentationModel, M0_DOCUMENTATION_BASE_URI + docIdentifier);
			// Convert to SIMS format
			Model simsModel = m0ConvertToSIMS(docModel);
			if (!namedModels) simsDataset.getDefaultModel().add(simsModel);
			else {
				simsDataset.addNamedModel("http://rdf.insee.fr/graphe/qualite/sims" + docIdentifier, simsModel);
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
		Resource m0BaseResource = m0Model.listStatements(null, RDF.type, SKOS.Concept).toList().get(0).getSubject(); // Should raise an exception in case of problem
		String m0Id = m0BaseResource.getURI().substring(m0BaseResource.getURI().lastIndexOf('/') + 1);
	
		// Will be handy for parsing dates
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	
		logger.debug("Creating metadata report model for m0 documentation " + m0Id);
	
		Model simsModel = ModelFactory.createDefaultModel();
		simsModel.setNsPrefix("rdf", RDF.getURI());
		simsModel.setNsPrefix("rdfs", RDFS.getURI());
		simsModel.setNsPrefix("xsd", XSD.getURI());
		simsModel.setNsPrefix("dcterms", DCTerms.getURI());
		simsModel.setNsPrefix("skos", SKOS.getURI());
		simsModel.setNsPrefix("insee", Configuration.BASE_INSEE_ONTO_URI);
	
		// Create the metadata report
		Resource report = simsModel.createResource(Configuration.simsReportURI(m0Id), simsModel.createResource(Configuration.SDMX_MM_BASE_URI + "MetadataReport"));
		report.addProperty(RDFS.label, simsModel.createLiteral("Metadata report " + m0Id, "en"));
		report.addProperty(RDFS.label, simsModel.createLiteral("Rapport de métadonnées " + m0Id, "fr"));
		logger.debug("MetadataReport resource created for report: " + report.getURI());
		// TODO Do we create a root Metadata Attribute?

		for (SIMSFrEntry entry : simsFRScheme.getEntries()) {
			// Create a m0 resource corresponding to the SIMSFr entry
			Resource m0EntryResource = ResourceFactory.createResource(m0BaseResource.getURI() + "/" + entry.getCode());
			logger.debug("Looking for the presence of SIMS attribute " + entry.getCode() + " (M0 URI: " + m0EntryResource + ")");
			// Check if the resource has values in M0 (French values are sine qua non)
			List<RDFNode> objectValues = m0Model.listObjectsOfProperty(m0EntryResource, M0_VALUES).toList();
			if (objectValues.size() == 0) {
				logger.debug("No value found in the M0 documentation model for SIMSFr attribute " + entry.getCode());
				continue;
			}
			if (objectValues.size() > 1) {
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
			Statement rangeStatement = metadataAttributeProperty.getProperty(RDFS.range);
			Resource propertyRange = (rangeStatement == null) ? null : rangeStatement.getObject().asResource();
			logger.debug("Target property is " + metadataAttributeProperty + " with range " + propertyRange);
			if (propertyRange == null) {
				// We are in the case of a 'text + seeAlso' object
				Resource objectResource = simsModel.createResource(); // Anonymous for now
				objectResource.addProperty(RDF.value, simsModel.createLiteral(stringValue, "fr"));
				report.addProperty(metadataAttributeProperty, objectResource);
			}
			else if (propertyRange.equals(SIMS_REPORTED_ATTRIBUTE)) {
				// Just a placeholder for now, the case does not seem to exist in currently available data
				report.addProperty(metadataAttributeProperty, simsModel.createResource(SIMS_REPORTED_ATTRIBUTE));
			}
			else if (propertyRange.equals(XSD.xstring)) {
				// TODO For now we attach all properties to the report, but a hierarchy of reported attributes should be created
				report.addProperty(metadataAttributeProperty, simsModel.createLiteral(stringValue, "fr"));
				// See if there is an English version
				objectValues = m0Model.listObjectsOfProperty(m0EntryResource, M0_VALUES_EN).toList();
				if (objectValues.size() == 0) {
					stringValue = objectValues.get(0).asLiteral().getString().trim().replaceAll("^\n", "");
					if (stringValue.length() > 0) report.addProperty(metadataAttributeProperty, simsModel.createLiteral(stringValue, "en"));
				}
			}
			else if (propertyRange.equals(XSD.date)) {
				// Try to parse the string value as a date (yyyy-MM-dd seems to be used in the documentations graph)
				try {
					dateFormat.parse(stringValue); // Just to make sure we have a valid date
					report.addProperty(metadataAttributeProperty, simsModel.createTypedLiteral(stringValue, XSDDatatype.XSDdate));
				} catch (ParseException e) {
					logger.error("Unparseable date value " + stringValue + " for M0 resource " + m0EntryResource.getURI());
				}
			}
			else if (propertyRange.equals(DQV_QUALITY_MEASUREMENT)) {
				// This case should not exist
			}
			else {
				// Only remaining case is code list (check that)
			}
		}
	
		return simsModel;
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
}
