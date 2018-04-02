package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Selector;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class M0SIMSConverter extends M0Converter {

	public static Logger logger = LogManager.getLogger(M0SIMSConverter.class);

	/**
	 * Converts a list (or all) of M0 'documentation' models to SIMS models.
	 * 
	 * @param m0Ids A <code>List</code> of M0 'documentation' metadata set identifiers, or <code>null</code> to convert all models.
	 */
	public static void convertToSIMS(List<String> m0Ids) {

		// First get the list of all existing M0 'documentation' models
		
	}

	/**
	 * Converts a metadata set from M0 to SIMSFr RDF format.
	 * 
	 * @param m0Model A Jena <code>Model</code> containing the metadata in M0 format.
	 * @return A Jena <code>Model</code> containing the metadata in SIMSFr format.
	 */
	public static Model m0ConvertToSIMS(Model m0Model) {
	
		// Retrieve base URI (the base resource is a skos:Concept) and the corresponding M0 identifier
		Resource baseResource = m0Model.listStatements(null, RDF.type, SKOS.Concept).toList().get(0).getSubject(); // Should raise an exception in case of problem
		String m0Id = baseResource.getURI().substring(baseResource.getURI().lastIndexOf('/') + 1);
	
		// Will be handy for parsing dates
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

		// Read the SIMSFr structure from the Excel specification
		// TODO Move to a calling function
		simsFRScheme = SIMSFRScheme.readSIMSFRFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
	
		logger.debug("Creating metadata report model for m0 documentation " + m0Id);
	
		Model simsModel = ModelFactory.createDefaultModel();
		simsModel.setNsPrefix("rdfs", RDFS.getURI());
		simsModel.setNsPrefix("dcterms", DCTerms.getURI());
		simsModel.setNsPrefix("skos", SKOS.getURI());
		simsModel.setNsPrefix("insee", Configuration.BASE_INSEE_ONTO_URI);
	
		// Create metadata report
		Resource report = simsModel.createResource(REPORT_BASE_URI + m0Id, simsModel.createResource(Configuration.SDMX_MM_BASE_URI + "MetadataReport"));
		report.addProperty(RDFS.label, simsModel.createLiteral("Metadata report " + m0Id, "en"));
		report.addProperty(RDFS.label, simsModel.createLiteral("Rapport de métadonnées " + m0Id, "fr"));
		// TODO Do we create a root Metadata Attribute?
	
		for (SIMSFREntry entry : simsFRScheme.getEntries()) {
			// Create a m0 resource corresponding to the SIMS entry
			Resource m0Resource = ResourceFactory.createResource(baseResource.getURI() + "/" + entry.getCode());
			// Check if the resource has values in M0 (French values are sine qua non)
			List<RDFNode> objectValues = m0Model.listObjectsOfProperty(m0Resource, M0_VALUES).toList();
			if (objectValues.size() == 0) continue; // Resource actually not present in the M0 model
			if (objectValues.size() > 1) {
				// Several values for the resource, we have a problem
				logger.error("Multiple values for resource " + m0Resource);
				continue;
			}
			// If we arrived here, we have one value, but it can be empty (including numerous cases where the value is just new line characters)
			String stringValue = objectValues.get(0).asLiteral().getString().trim().replaceAll("^\n", ""); // TODO Check cases where value is "\n\n"
			if (stringValue.length() == 0) continue;
			logger.debug("Value found for M0 resource " + m0Resource);
			// Get the metadata attribute property from the MSD and get its range
			Property metadataAttributeProperty = simsFrMSD.getProperty(Configuration.simsAttributePropertyURI(entry, false));
			Statement rangeStatement = metadataAttributeProperty.getProperty(RDFS.range);
			Resource range = (rangeStatement == null) ? null : rangeStatement.getObject().asResource();
			logger.debug("Target property is " + metadataAttributeProperty + " with range " + range);
			if (range == null) {
				// We are in the case of a 'text + seeAlso' object
				Resource objectResource = simsModel.createResource(); // Anonymous for now
				objectResource.addProperty(RDF.value, simsModel.createLiteral(stringValue, "fr"));
				report.addProperty(metadataAttributeProperty, objectResource);
			}
			else if (range.equals(SIMS_REPORTED_ATTRIBUTE)) {
				// Just a placeholder for now, the case does not seem to exist in currently available data
				report.addProperty(metadataAttributeProperty, simsModel.createResource(SIMS_REPORTED_ATTRIBUTE));
			}
			else if (range.equals(XSD.xstring)) {
				// TODO For now we attach all properties to the report, but a hierarchy of reported attributes should be created
				report.addProperty(metadataAttributeProperty, simsModel.createLiteral(stringValue, "fr"));
				// See if there is an English version
				objectValues = m0Model.listObjectsOfProperty(m0Resource, M0_VALUES_EN).toList();
				if (objectValues.size() == 0) {
					stringValue = objectValues.get(0).asLiteral().getString().trim().replaceAll("^\n", "");
					if (stringValue.length() > 0) report.addProperty(metadataAttributeProperty, simsModel.createLiteral(stringValue, "en"));
				}
			}
			else if (range.equals(XSD.date)) {
				// Try to parse the string value as a date (yyyy-MM-dd seems to be used in the documentations graph)
				try {
					dateFormat.parse(stringValue); // Just to make sure we have a valid date
					report.addProperty(metadataAttributeProperty, simsModel.createTypedLiteral(stringValue, XSDDatatype.XSDdate));
				} catch (ParseException e) {
					logger.error("Unparseable date value " + stringValue + " for M0 resource " + m0Resource.getURI());
				}
			}
			else if (range.equals(DQV_QUALITY_MEASUREMENT)) {
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
}
