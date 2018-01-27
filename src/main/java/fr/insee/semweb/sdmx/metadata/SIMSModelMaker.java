package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.jena.ontology.DatatypeProperty;
import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.ORG;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import fr.insee.semweb.sdmx.metadata.SIMSEntry.EntryType;
import fr.insee.stamina.utils.DQV;

/**
 * Creates a collection of Jena models corresponding to the SIMSv2/SIMSv2FR standards.
 * 
 * @author Franck Cotton
 */
public class SIMSModelMaker {

	public static Logger logger = LogManager.getLogger(SIMSModelMaker.class);

	/** Jena model for the SDMX metadata model */
	public static OntModel sdmxModel = null;

	public static void main(String[] args) {

		// Read the SDMX metadata model RDF vocabulary in the OntModel
		logger.debug("About to read the SDMX metadata model RDF vocabulary");
		sdmxModel = readSDMXModel(Configuration.SDMX_MM_TURTLE_FILE_NAME, true);

		// Read the SIMS Excel file into a SIMSFRScheme object
		SIMSFRScheme simsFRScheme = null;
		try {
			simsFRScheme = SIMSFRScheme.readSIMSFRFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		} catch (Exception e) {
			logger.fatal("Error reading SIMS Excel file " + Configuration.SIMS_XLSX_FILE_NAME + " - " + e.getMessage());
			System.exit(1);
		}

		// Create the SKOS concept scheme for SIMSv2 (strict) with DQV dimensions and categories
		Model simsSKOSModel = createConceptScheme(simsFRScheme, true, false, true);
		try {
			simsSKOSModel.write(new FileWriter(Configuration.SIMS_CS_TURTLE_FILE_NAME), "TTL");
		} catch (IOException e) {
			logger.fatal("Error writing the SIMSv2 concept scheme Turtle file", e);
			System.exit(1);
		}

		// Create the SKOS concept scheme for SIMSv2FR with DQV dimensions and categories
		simsSKOSModel = createConceptScheme(simsFRScheme, false, true, true);
		try {
			simsSKOSModel.write(new FileWriter(Configuration.SIMS_FR_CS_TURTLE_FILE_NAME), "TTL");
		} catch (IOException e) {
			logger.fatal("Error writing the SIMSv2FR concept scheme Turtle file", e);
			System.exit(1);
		}

		// Create the SIMS MSD model for SIMSv2 (strict)
		Model simsMSDModel = createMetadataStructureDefinition(simsFRScheme, true, false);
		try {
			simsMSDModel.write(new FileWriter(Configuration.SIMS_MSD_TURTLE_FILE_NAME), "TTL");
		} catch (IOException e) {
			logger.fatal("Error writing the SIMSv2 MSD Turtle file", e);
			System.exit(1);
		}

		// Create the SIMS MSD model for SIMSv2FR
		simsMSDModel = createMetadataStructureDefinition(simsFRScheme, false, true);
		try {
			simsMSDModel.write(new FileWriter(Configuration.SIMS_FR_MSD_TURTLE_FILE_NAME), "TTL");
		} catch (IOException e) {
			logger.fatal("Error writing the SIMSv2FR MSD Turtle file", e);
			System.exit(1);
		}
	}

	/**
	 * Reads the SDMX metadata RDF vocabulary into a Jena ontology model.
	 * 
	 * @param logDetails If true, details on the model will be written in the log.
	 * @return An <code>OntModel</code> corresponding to the SDMX metadata model.
	 */
	public static OntModel readSDMXModel(String turtleFileName, boolean logDetails) {

		OntModel sdmxMetadataModel = ModelFactory.createOntologyModel();
		sdmxMetadataModel.read(turtleFileName);
		if (logDetails) {
			List<OntClass> ontClasses = sdmxMetadataModel.listClasses().toList();
			logger.info("Number of classes in the model: " + ontClasses.size());
			for (OntClass ontClass : ontClasses) {
				logger.info(ontClass.getURI());
			}
			List<DatatypeProperty> datatypeProperties = sdmxMetadataModel.listDatatypeProperties().toList();
			logger.info("Number of data type properties in the model: " + datatypeProperties.size());
			for (DatatypeProperty property : datatypeProperties) {
				logger.info(property.getURI());
			}
			List<ObjectProperty> objectProperties = sdmxMetadataModel.listObjectProperties().toList();
			logger.info("Number of object properties in the model: " + objectProperties.size());
			for (ObjectProperty property : objectProperties) {
				logger.info(property.getURI());
			}
		}
		return sdmxMetadataModel;
	}

	/**
	 * Creates a Jena model containing the SKOS concept scheme associated to SIMSv2 or SIMSv2FR.
	 * 
	 * @param simsFR A <code>SIMSFRScheme</object> containing the SIMSv2FR model.
	 * @param simsStrict A boolean indicating if the concept scheme generated is restricted to the SIMSv2 or extended to SIMSv2FR.
	 * @param addFrench A boolean indicating if French labels and descriptions should be included.
	 * @param createDQV A boolean indicating if Data Quality Vocabulary constructs should be added to the model.
	 * @return A Jena <code>Model</code> containing the concept scheme.
	 */
	public static Model createConceptScheme(SIMSFRScheme simsFR, boolean simsStrict, boolean addFrench, boolean createDQV) {

		logger.info("About to create the concept scheme for the " + (simsStrict ? " SIMSv2" : "SIMSv2FR") + " model");

		Model skosCS = ModelFactory.createDefaultModel();
		skosCS.setNsPrefix("skos", SKOS.getURI());
		if (createDQV) skosCS.setNsPrefix("dqv", DQV.getURI());

		// Creates the Concept Scheme
		Resource simsv2 = skosCS.createResource(Configuration.simsConceptSchemeURI(simsStrict), SKOS.ConceptScheme);
		simsv2.addProperty(SKOS.notation, "SIMSv2" + (simsStrict ? "" : "FR"));
		simsv2.addProperty(SKOS.prefLabel, skosCS.createLiteral(Configuration.simsConceptSchemeName(simsStrict, false), "en"));
		if (addFrench) simsv2.addProperty(SKOS.prefLabel, skosCS.createLiteral(Configuration.simsConceptSchemeName(simsStrict, true), "fr"));

		for (SIMSFREntry entry : simsFR.getEntries()) {

			if (simsStrict && entry.getNotation().startsWith("I")) continue; // All strict SIMS entries have notations starting with 'S'
			if (entry.getNotation().equals("I.1") || entry.getNotation().startsWith("I.1.")) continue; // Even in non-strict mode, entries in the 'Identity' section are direct properties on the operation rather than metadata attributes

			logger.debug("Creating concept for entry " + entry.getNotation() + " (" + entry.getCode() + ")");

			Resource simsConcept = skosCS.createResource(Configuration.simsConceptURI(entry), SKOS.Concept);
			simsConcept.addProperty(SKOS.notation, entry.getNotation());
			simsConcept.addProperty(SKOS.prefLabel, skosCS.createLiteral(entry.getName(), "en"));
			if (addFrench) {
				simsConcept.addProperty(SKOS.prefLabel, skosCS.createLiteral(entry.getFrenchName(), "fr"));				
			}
			if (createDQV) {
				if (entry.getType() == EntryType.CATEGORY) simsConcept.addProperty(RDF.type, DQV.Category); // Add type DQV category
				if (entry.getType() == EntryType.DIMENSION) {
					simsConcept.addProperty(RDF.type, DQV.Dimension); // Add type DQV dimension
					// NB: only case of a 'I' category is I.20, but it has no dimensions
					simsConcept.addProperty(DQV.inCategory, skosCS.createResource(Configuration.simsConceptURI(SIMSEntry.getCategoryNotation(entry.getNotation())))); // Add inCategory for dimensions
				}
			}
			simsConcept.addProperty(SKOS.inScheme, simsv2);
			SIMSFREntry parent = simsFR.getParent(entry);
			if (parent == null) {
				simsv2.addProperty(SKOS.hasTopConcept, simsConcept);
				simsConcept.addProperty(SKOS.topConceptOf, simsv2);
			} else {
				Resource parentConcept = skosCS.createResource(Configuration.simsConceptURI(parent));
				simsConcept.addProperty(SKOS.broader, parentConcept);
				parentConcept.addProperty(SKOS.narrower, simsConcept);
			}
		}
		return skosCS;
	}

	/**
	 * Creates the Metadata Structure Definition associated to the SIMS in a Jena model.
	 * 
	 * @param sims A <code>SIMSScheme</object> containing the SIMS model.
	 * @param simsStrict A boolean indicating if the MSD generated is restricted to the SIMS or extended to SIMSPlus.
	 * @param addFrench A boolean indicating if French labels and descriptions should be included.
	 * @return A Jena <code>Model</code> containing the metadata structure definition.
	 */
	public static Model createMetadataStructureDefinition(SIMSFRScheme sims, boolean simsStrict, boolean addFrench) {

		logger.info("About to create the model for the " + (simsStrict ? " SIMS" : "SIMSPlus") + " Metadata Structure Definition");

		Model msdModel = ModelFactory.createDefaultModel();
		msdModel.setNsPrefix("rdfs", RDFS.getURI());
		msdModel.setNsPrefix("owl", OWL.getURI());
		msdModel.setNsPrefix("xsd", XSD.getURI());
		msdModel.setNsPrefix("dcterms", DCTerms.getURI());
		msdModel.setNsPrefix("dqv", DQV.getURI());
		msdModel.setNsPrefix(Configuration.SDMX_MM_PREFIX, Configuration.SDMX_MM_BASE_URI);

		// Create the metadata structure definition
		Resource msd = msdModel.createResource(Configuration.simsMSDURI(simsStrict), sdmxModel.getResource(Configuration.SDMX_MM_BASE_URI + "MetadataStructureDefinition"));
		msd.addProperty(RDFS.label, msdModel.createLiteral(Configuration.simsMSDLabel(simsStrict, false), "en"));
		if (addFrench) msd.addProperty(RDFS.label, msdModel.createLiteral(Configuration.simsMSDLabel(simsStrict, true), "fr"));

		// Create the report structure and associate it to the MSD
		Resource reportStructure = msdModel.createResource(Configuration.simsStructureReportURI(simsStrict), sdmxModel.getResource(Configuration.SDMX_MM_BASE_URI + "ReportStructure"));
		reportStructure.addProperty(RDFS.label, msdModel.createLiteral(Configuration.simsReportStructureLabel(simsStrict, false), "en"));
		if (addFrench) reportStructure.addProperty(RDFS.label, msdModel.createLiteral(Configuration.simsReportStructureLabel(simsStrict, true), "fr"));
		msd.addProperty(DCTerms.hasPart, reportStructure);

		// Create the metadata attribute properties and metadata attribute property specifications
		for (SIMSFREntry entry : sims.getEntries()) {

			if (simsStrict && entry.getNotation().startsWith("I")) continue; // All strict SIMS entries have notations starting with 'S'

			if (entry.getNotation().equals("I.1") || entry.getNotation().startsWith("I.1.")) continue; // Even in non-strict mode, entries in the 'Identity' section are direct properties on the operation rather than metadata attributes

			logger.debug("Processing metadata attribute " + entry.getNotation() + " (" + entry.getCode() + ")");

			Resource attributeSpec = msdModel.createResource(Configuration.simsAttributeSpecificationURI(entry, simsStrict), sdmxModel.getResource(Configuration.SDMX_MM_BASE_URI + "MetadataAttributeSpecification"));
			attributeSpec.addProperty(RDFS.label, msdModel.createLiteral("Metadata Attribute Specification for concept " + entry.getName(), "en"));
			if (entry.isPresentational()) {
				attributeSpec.addProperty(sdmxModel.getProperty(Configuration.SDMX_MM_BASE_URI + "isPresentational"), msdModel.createTypedLiteral(true));
			}
			SIMSFREntry parent = sims.getParent(entry);
			if (parent != null) {
				attributeSpec.addProperty(sdmxModel.getProperty(Configuration.SDMX_MM_BASE_URI + "parent"), msdModel.createResource(Configuration.simsAttributeSpecificationURI(parent, simsStrict)));
			}
			reportStructure.addProperty(sdmxModel.getProperty(Configuration.SDMX_MM_BASE_URI + "metadataAttributeSpecification"), attributeSpec);
			Resource attributeProperty = msdModel.createResource(Configuration.simsAttributePropertyURI(entry, simsStrict), sdmxModel.getResource(Configuration.SDMX_MM_BASE_URI + "MetadataAttributeProperty"));
			// The type of the property depends on the values of the representation variables (SIMS and Insee)
			Resource propertyRange = getRange(entry, simsStrict);
			logger.debug("Property range is " + propertyRange.getLocalName());
			if (propertyRange.equals(XSD.xstring) || propertyRange.equals(XSD.date)) attributeProperty.addProperty(RDF.type, OWL.DatatypeProperty);
			else attributeProperty.addProperty(RDF.type, OWL.ObjectProperty);
			attributeProperty.addProperty(RDFS.label, msdModel.createLiteral("Metadata Attribute Property for concept " + entry.getName(), "en"));
			attributeProperty.addProperty(sdmxModel.getProperty(Configuration.SDMX_MM_BASE_URI + "concept"), msdModel.createResource(Configuration.simsConceptURI(entry)));
			attributeProperty.addProperty(RDFS.domain, sdmxModel.getResource(Configuration.SDMX_MM_BASE_URI + "ReportedAttribute"));
			if (!propertyRange.equals(RDFS.Resource)) attributeProperty.addProperty(RDFS.range, propertyRange);
			// TODO Add isDefinedBy?
			// attributeProperty.addProperty(RDFS.isDefinedBy, msd);
			attributeSpec.addProperty(sdmxModel.getProperty(Configuration.SDMX_MM_BASE_URI + "metadataAttributeProperty"), attributeProperty);
		}

		return msdModel;
	}

	/**
	 * Determines the range of an metadata attribute property based on the information on the representation of its values.
	 * 
	 * @param entry A <code>SIMSPlusEntry</code> corresponding to the metadata attribute.
	 * @param simsStrict A boolean indicating if the context is restricted to the SIMS or extended to SIMSPlus.
	 * @return The range of the property represented as a (non-null) Jena <code>Resource</code>.
	 */
	public static Resource getRange(SIMSFREntry entry, boolean simsStrict) {

		Resource range = null;

		// In the strict SIMS model, the only possible (non-null) types are Text (with variants Telephone, etc.), Date, Quality Indicator and code list (CL_...)
		// If the SIMS type is null, it means that the property is presentational, so the metadata attribute property will have ReportedAttribute for range
		String type = entry.getRepresentation();
		if ((type == null) || (type.trim().length() == 0)) range = ResourceFactory.createResource(Configuration.SDMX_MM_BASE_URI + "ReportedAttribute");
		else {
			type = type.trim().toLowerCase();
			if (type.equals("date")) range = XSD.date;
			else if (type.startsWith("quality")) range = DQV.QualityMeasurement; // TODO Or is it Metric?
			else if (type.contains("code")) {
				// Extract SDMX code list name (pattern is '(code list: CL_FREQ)')
				String clConceptNotation = type.substring(type.indexOf("cl_"), type.lastIndexOf(")"));
				range = ResourceFactory.createResource(Configuration.sdmxCodeConceptURI(clConceptNotation));
			}
			// All that is left is text and variants like fax, telephone or e-mail
			else range = XSD.xstring;
		}

		// When in strict SIMS mode, we stop here
		if (simsStrict) return range;

		// In SIMSPlus mode, we have to look at the Insee representation if it exists
		// We ignore for now 'ou texte' mentions
		type = entry.getInseeRepresentation();
		if ((type == null) || (type.equals("ou texte"))) return range;

		type = type.trim().toLowerCase();
		// All that starts with 'text' or 'expression' (for 'expression régulière) is treated as string datatype property
		if (type.startsWith("text") || type.startsWith("expression")) return XSD.xstring;
		// 'Rich text' alone (without reference) is also string for now (could be HTLM text)
		if (type.equals("rich text")) return XSD.xstring;
		// The other cases of 'rich text' ('Rich text + other material...') are associated with references: we simply return RDF resource in this case
		if (type.startsWith("rich text")) return RDFS.Resource;
		// If representation starts with 'code list', either the list is indicated with CL_*, or it is the list of SSMs
		else if (type.startsWith("code list")) {
			int index = type.indexOf("cl_");
			if (index >= 0) {
				// Extract the name of the code list (can be followed by space or new line)
				int firstSpace = type.indexOf("\n", index);
				if (firstSpace == -1) firstSpace = type.indexOf(" ", index);
				String clConceptNotation = (firstSpace < 0) ? type.substring(index) : type.substring(index, firstSpace);
				return ResourceFactory.createResource(Configuration.codeConceptURI(clConceptNotation));
			} else {
				// Code list is in fact a list of organizations, so the property range will be org:Organization
				return ORG.Organization;
			}
		}
		// Third and last possible type: text + reference, we simply return RDF resource in this case
		else {
			// Catch-all case
			return RDFS.Resource;
		}
	}

	/**
	 * Reads the SIMS from an Excel file.
	 * 
	 * @param xlsxFile The Excel file containing the SIMS.
	 * @param fromPlus Indicates if the data should be read in the "SIMS Plus" sheet.
	 * @return The SIMS as a <code>List<SIMSEntry></code> object, or <code>null</code> in case of problem.
	 */
	public static List<SIMSEntry> readSIMSFromExcel(File xlsxFile, boolean fromPlus) {

		// The SIMS Plus Excel file contains the original SIMS on the first sheet and the SIMS
		// Plus data on the second sheet. The latter contains the SIMS (except ESS guidelines and quality
		// indicators, but also additional columns and lines;

		int sheetNumber = (fromPlus ? 1 : 0);
		
		Workbook simsWorkbook = null;
		Sheet simsSheet = null;
		try {
			simsWorkbook = WorkbookFactory.create(xlsxFile);
			simsSheet = simsWorkbook.getSheetAt(sheetNumber);
		} catch (Exception e) {
			logger.fatal("Error while opening Excel file - " + e.getMessage());
			return null;
		}

		List<SIMSEntry> sims = new ArrayList<SIMSEntry>();

		Iterator<Row> rows = simsSheet.rowIterator();
		rows.next(); // Skip the title line
		if (fromPlus) rows.next(); // Second title line in the SIMS Plus format
		while (rows.hasNext()) {
			Row row = rows.next();
			// Additional lines in the SIMS Plus format are identified by a non blank K column ("Origine")
			if ((fromPlus) && (row.getCell(10, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim().length() > 0)) continue;

			SIMSEntry simsEntry = SIMSEntry.readFromRow(row, fromPlus);
			if (simsEntry == null) continue;

			System.out.println(simsEntry);
			// logger.debug("Entry read: " + entry);
			sims.add(simsEntry);
		}
		try { simsWorkbook.close(); } catch (IOException ignored) { }
		return sims;
	}

	/**
	 * Reads the SIMS from a CVS file.
	 * 
	 * @param csvFile The CVS file containing the SIMS.
	 * @return The SIMS as a <code>List<SIMSEntry></code> object, or <code>null</code> in case of problem.
	 * @throws IOException In case of problem reading the file.
	 */
	public static List<SIMSEntry> readSIMSFromCSV(File csvFile) throws IOException {

		CSVParser parser = new CSVParser(new FileReader(csvFile), CSVFormat.TDF.withQuote(null).withHeader().withIgnoreEmptyLines());

		List<SIMSEntry> sims = new ArrayList<SIMSEntry>();

		for (CSVRecord record : parser) {
			String notation = record.get("Concept Notation").trim();
			if (notation.length() == 0) {
				logger.error("Concept notation empty for record " + record.getRecordNumber());
				continue;
			}
			else {
				SIMSEntry entry = new SIMSEntry(notation);
				entry.setCode(record.get("Concept Code").trim());
				entry.setName(record.get("Concept Name").trim());
				String type = record.get("concept").trim().toLowerCase();
				if (type.equals("category")) entry.setType(EntryType.CATEGORY);
				else if (type.equals("dimension")) entry.setType(EntryType.DIMENSION);
				logger.debug("Entry read: " + entry);
				sims.add(entry);
			}
		}
		parser.close();

		return sims;
	}
}
