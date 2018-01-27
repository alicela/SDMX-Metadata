package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Selector;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;

import eu.casd.semweb.psp.PSPOperationEntry;
import eu.casd.semweb.psp.PSPOperationEntry.OperationType;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public class SourceConverter {

	public static Logger logger = LogManager.getLogger(SourceConverter.class);

	static String workingDir = "D:\\Documents\\Travail\\Sources\\";
	public static String OPERATION_LIST_FILE_NAME = "src/main/resources/Comparaison_Insee-sources-methodes-CASD-GD.xlsx";

	/** List of all URIs (read from a text file produced by XSLT on M0 DDS export */
	static List<String> uris = null;
	static List<String> operations = null;
	static List<String> properties = null;
	static List<String> simsProps = null; // List of properties defined in SIMS+, read from a file copied from the relevant column of the spreadsheet specification

	static Model m0Model = null;
	// The ubiquitous property in M0
	static Property m0Values = ResourceFactory.createProperty("http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#values");

	public static void main(String[] args) throws IOException {

		// Read all the URIs in a list
		try (Stream<String> stream = Files.lines(Paths.get(workingDir + "uris.txt"))) {
			uris = stream.collect(Collectors.toList());
		}
		// Read all the SIMS+ properties in a list
		try (Stream<String> stream = Files.lines(Paths.get(workingDir + "sims2plus.txt"))) {
			simsProps = stream.collect(Collectors.toList());
		}

		// Fill the lists of operations and properties from the list of unique URIs, filtering out the OPE- ones
		List<String> resources = uris.stream().filter(name -> !name.startsWith("http://baseUri/OPE-")).distinct().collect(Collectors.toList());
		operations = resources.stream().map(uri -> getOperationId(uri)).distinct().collect(Collectors.toList());
		properties = resources.stream().map(uri -> getPropertyCode(uri)).filter(name -> name != null).distinct().collect(Collectors.toList());

		//printStatistics();

		// Read the source M0 model
		m0Model = ModelFactory.createDefaultModel();
		m0Model.read(new FileInputStream(workingDir + "ExportSourcesDDS_All_20170309.rdf"), null);

		// Produce the model for the list of operations
		createOperationModel();

		// Produce a small model for each operation
		//splitModel(operations);
	}

	/**
	 * Creates the model corresponding to the list of operation with family/series structure and basic identity properties.
	 * 
	 * @return The list of operations as a Jena <code>Model</code>.
	 */
	public static Model createOperationModel() {

		final String MAPPING_FILE_NAME = "D:\\Documents\\Travail\\Sources\\code-list-title-mappings.txt";

		// Create useful ontology class resources (classes corresponding to the different operation types: operation, series, family).
		Map<OperationType, Resource> operationClassURIs = new HashMap<OperationType, Resource>();
		for (OperationType operationType : OperationType.values()) operationClassURIs.put(operationType, ResourceFactory.createResource(operationType.operationClassURI()));
		operationClassURIs.remove(OperationType.UNKNOWN);

		// Read the mappings between operation (list) names and identifiers into a map
		// This should be rendered useless by integrating the operation identifiers in the Excel file. 
		Map<String, String> mappings = null;
		// The input file must be UTF-8 encoded
		try (Stream<String> stream = Files.lines(Paths.get(MAPPING_FILE_NAME))) {
			mappings = stream.collect(Collectors.toMap(line -> line.split("\t")[1].trim(), line -> line.split("\t")[0].trim()));
			logger.info(mappings.size() + " mappings read from file " + MAPPING_FILE_NAME);
		} catch (IOException e) {
			logger.fatal("Error while opening mapping file - " + e.getMessage());
			System.exit(1);
		}

		// Read the Excel file listing the operations with their types, parent, CASD indicator, etc.
		Workbook simsWorkbook = null;
		Sheet simsSheet = null;
		try {
			simsWorkbook = WorkbookFactory.create(new File(OPERATION_LIST_FILE_NAME));
			simsSheet = simsWorkbook.getSheetAt(0);
			logger.debug("Spreadsheet opened: " + OPERATION_LIST_FILE_NAME);
		} catch (Exception e) {
			logger.fatal("Error while opening Excel file " + OPERATION_LIST_FILE_NAME + " - " + e.getMessage());
			System.exit(1);
		}

		// For each row in the spreadsheet, create an OperationEntry object and add it to a map
		Map<String, PSPOperationEntry> typedOperations = new HashMap<String, PSPOperationEntry>();
		Iterator<Row> rows = simsSheet.rowIterator();
		rows.next(); // Skip the title line
		while (rows.hasNext()) {
			Row row = rows.next();
			String operationName = row.getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			// The title should be found in the mappings, in order to retrieve the operation code
			if (!mappings.containsKey(operationName)) {
				logger.error("Operation title absent from mapping file: " + operationName);
				continue;
			}
			String operationIdentifier = mappings.get(operationName);
			logger.debug("Reading data for operation " + operationIdentifier);
			PSPOperationEntry operation = new PSPOperationEntry(operationIdentifier);
			operation.setType(row.getCell(2, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim());
			if (operation.getType() == OperationType.UNKNOWN) {
				logger.error("Unrecognise type '" + row.getCell(2, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString() + "' for operation " + operationName);
				continue;				
			}
			String parentCode = row.getCell(3, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			if (parentCode.length() > 0) operation.setParentCode(parentCode.substring(4)); // To get rid of the 'OPE-' prefix
			String casdInfo = row.getCell(1, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			if (casdInfo.length() > 0) operation.setCasdIndicator(casdInfo); // Refine treatment of the CASD info

			typedOperations.put(operationIdentifier, operation);
		}
		try { simsWorkbook.close(); } catch (IOException ignored) { }
		logger.debug(typedOperations.size() + " operation read from spreadsheet");
		// Check if every declared parent exists in the list
		for (String operationCode : typedOperations.keySet()) {
			String parentCode = typedOperations.get(operationCode).getParentCode();
			if ((parentCode != null) && (!typedOperations.containsKey(parentCode))) {
				logger.error("For child " + operationCode + ": parent code " + parentCode + " could not be found");
			}
		}

		// We now have all the elements to create the model containing the list of operations
		Model opModel = ModelFactory.createDefaultModel();
		for (String operation : operations) {
			PSPOperationEntry entry = typedOperations.get(operation);
			if (entry == null) continue; // TODO Log a problem
			// Create the resource representing the operation
			Resource operationResource = opModel.createResource(Configuration.operationURI(entry), operationClassURIs.get(entry.getType()));
			// For each property in the 'Identity' part of the SIMS+ model, try to get the value in M0
			for (String propertyName : Configuration.propertyMappings.keySet()) {
				// Get the value in French
				String frenchPropertyName = "http://baseUri/FR-" + entry.getCode() + "/" + propertyName;
				Resource frenchProperty = m0Model.createResource(frenchPropertyName);
				List<RDFNode> valueList = m0Model.listObjectsOfProperty(frenchProperty, m0Values).toList();
				// There should be at most one value
				logger.debug("Number of values for property " + frenchPropertyName + ": ", valueList.size());
				// All is supposed to be text for now
				if (valueList.size() > 0) {
					operationResource.addProperty(Configuration.propertyMappings.get(propertyName), valueList.get(0).toString()); // Take first value only for now
				}
			}
			
		}
		
		return opModel;
	}

	/**
	 * Splits the base model into smaller models related to each operation of a list passed as parameter and saves the smaller models to disk.
	 * 
	 * @param operationNames A <code>List</code> of operation URI.
	 * @throws IOException In case of problem while writing the model to disk.
	 */
	public static void splitModel(List<String> operationNames) throws IOException {

		logger.debug("Splitting M0 model into " + operationNames.size() + " models");
		for (String operationName : operationNames) {
			// Create model for the current source
			Model sourceModel = extractModel(operationName);
			sourceModel.write(new FileOutputStream(workingDir + "models/"+ operationName.toLowerCase() + ".ttl"), "TTL");
			sourceModel.close();
		}
		m0Model.close();
	}

	/**
	 * Extracts from the base model all the statements related to a given operation.
	 * 
	 * @param operationURI URI of the operation.
	 * @return A Jena <code>Model</code> containing the statements related to the operation.
	 */
	public static Model extractModel(String operationURI) {

		logger.debug("Extracting model for operation: " + operationURI);
		Model operationModel = ModelFactory.createDefaultModel();
		Selector selector = new SimpleSelector(null, null, (RDFNode) null) {
						        public boolean selects(Statement statement)
					            {return getOperationId(statement.getSubject().getURI()).equals(operationURI);}
						    };
		// Copy the relevant statements to the extract model
		operationModel.add(m0Model.listStatements(selector));

		return operationModel;
	}

	@SuppressWarnings("unused")
	private static void printStatistics() {

		System.out.println(uris.size() + " URI lues");
		List<String> multipleURIs = findMultiples(uris);
		System.out.println(multipleURIs.size() + " URI multiples");

		List<String> uniques = uris.stream().distinct().collect(Collectors.toList());
		System.out.println(uniques.size() + " URI uniques");

		List<String> pathStarts = uniques.stream().map(uri -> uri.substring(15).split("/")[0]).distinct().collect(Collectors.toList());
		System.out.println(pathStarts.size() + " débuts de chemin uniques");

		List<String> frOperations = pathStarts.stream().filter(name -> name.startsWith("FR-")).map(name -> name.substring(3)).collect(Collectors.toList());
		List<String> enOperations = pathStarts.stream().filter(name -> name.startsWith("EN-")).map(name -> name.substring(3)).collect(Collectors.toList());
		System.out.println(" . dont FR- : " + frOperations.size());
		System.out.println(" . dont EN- : " + enOperations.size());
		System.out.println(" . dont OPE- : " + pathStarts.stream().filter(name -> name.startsWith("OPE-")).collect(Collectors.toList()).size());

		// Check that operations starting with FR- are the same as operations starting with EN-
		List<String> check = new ArrayList<String>(frOperations);
		check.removeAll(enOperations);
		System.out.println(" . FR- sans EN- " + check.size());
		check = new ArrayList<String>(enOperations);
		check.removeAll(frOperations);
		System.out.println(" . EN- sans FR- " + check.size());

		// Print the number of operations and properties
		System.out.println(operations.size() + " opérations");
		System.out.println(properties.size() + " propriétés");

		// Print the list of properties
		System.out.println("\nPropriétés utilisées dans les descriptions de ressources");
		for (String each : properties) System.out.println(each);

		// Print the properties that are used but not defined in SIMS+
		List<String> propsCopy = new ArrayList<String>(properties);
		propsCopy.removeAll(simsProps);
		System.out.println("\nPropriétés non définies dans SIMS+");
		for (String each : propsCopy) System.out.println(each);

	}

	@SuppressWarnings("unused")
	private static void printOneOperation(String opeName) {

		System.out.println("\n");
		List<String> one = uris.stream().filter(name -> name.startsWith("http://baseUri/FR-" + opeName)).collect(Collectors.toList());
		for (String each : one) System.out.println(each);
		System.out.println("\n");
		one = uris.stream().filter(name -> name.startsWith("http://baseUri/EN-" + opeName)).collect(Collectors.toList());
		for (String each : one) System.out.println(each);
	}

	public static <T> List<T> findMultiples(Iterable<T> all) {

		Set<T> set = new HashSet<T>();
		List<T> multiples = new ArrayList<T>();
		// Set#add returns false if the set does not change.
		for (T each: all) if (!set.add(each)) multiples.add(each);

		return multiples;
	}

	/**
	 * Extracts the operation identifier from a resource URI.
	 * 
	 * @param uri The resource URI.
	 * @return The identifier of the operation.
	 */
	public static String getOperationId(String uri) {

		// Assuming URIs of the type 'http://baseUri/FR-ACCES-FINANCEMENT-PME-10-PERSONNES/SOURCE_CODE'
		return uri.substring(18).split("/")[0];
	}

	/**
	 * Extracts the SIMS+ property code from a resource URI.
	 */
	public static String getPropertyCode(String uri) {

		// Assuming URIs of the type 'http://baseUri/FR-ACCES-FINANCEMENT-PME-10-PERSONNES/SOURCE_CODE'
		String[] pathElements = uri.substring(18).split("/");

		return (pathElements.length > 1) ? pathElements[1] : null;
	}
}


