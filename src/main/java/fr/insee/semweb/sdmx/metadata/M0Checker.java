package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;

public class M0Checker {

	public static Logger logger = LogManager.getLogger(M0Checker.class);

	static Dataset dataset = null;

	public static void main(String[] args) throws IOException {

		dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
//		extractModels(dataset);
//		studySeries();
//		studyFamilies();
//		studyOperations();
		studyDocumentations();
	}

	public static void studyDocumentations() {

		String baseURI = "http://baseUri/documentations/documentation/";

		Model documentations = dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations");

		// Build the mapping between documentation id (number) and the list of associated properties
		Map<Integer, List<String>> propertiesByDocumentation = new TreeMap<Integer, List<String>>();
		ResIterator subjectsIterator = documentations.listSubjects();
		while (subjectsIterator.hasNext()) {
			String documentationM0URI = subjectsIterator.next().getURI();
			String[] pathComponents = documentationM0URI.substring(baseURI.length()).split("/");
			String documentationId = pathComponents[0];
			// Documentation identifiers are integers (but careful with the sequence number)
			try {
				Integer documentationIntId = Integer.parseInt(documentationId);
				if (!propertiesByDocumentation.containsKey(documentationIntId)) propertiesByDocumentation.put(documentationIntId, new ArrayList<String>());
				// In this case we make lists of property names, not full URIs
				if (pathComponents.length > 1) propertiesByDocumentation.get(documentationIntId).add(pathComponents[1]);
			} catch (NumberFormatException e) {
				// Should be the sequence number resource: http://baseUri/documentations/documentation/sequence
				if (!("sequence".equals(documentationId))) System.out.println("Invalid documentation URI: " + documentationM0URI);
			}
		}
		System.out.println("Found a total of " + propertiesByDocumentation.size() + " documentations in the M0 model");

		// Build the list of all properties used in the M0 documentation model
		Set<String> m0Properties = new TreeSet<String>();
		for (Integer id : propertiesByDocumentation.keySet()) {
			System.out.println("Documentation #" + id + " uses " + propertiesByDocumentation.get(id).size() + " properties");
			m0Properties.addAll(propertiesByDocumentation.get(id));
		}
		System.out.println(m0Properties.size() + " properties used in M0 'documentation' graph: " + m0Properties);

		// Find the differences between the properties listed here and the SIMS/SIMS+ properties
		SIMSFrScheme simsPlusScheme = null;
		try {
			simsPlusScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		} catch (Exception e) {
			System.out.println("Error reading SIMS Plus Excel file");
			return;
		}
		List<String> simsProps = new ArrayList<String>();
		for (SIMSFrEntry entry : simsPlusScheme.getEntries()) {
			simsProps.add(entry.getCode());
		}
		List<String> testList = new ArrayList<String>(simsProps);
		testList.removeAll(m0Properties);
		Collections.sort(testList);
		System.out.println("Properties in SIMSFr and not in M0: " + testList);
		testList = new ArrayList<String>(m0Properties);
		testList.removeAll(simsProps);
		Collections.sort(testList);
		System.out.println("Properties in M0 and not in SIMSFr: " + testList);

		simsProps = new ArrayList<String>();
		for (SIMSFrEntry entry : simsPlusScheme.getEntries()) {
			if (!entry.isOriginal()) continue; // Ignore Insee's additions
			simsProps.add(entry.getCode());
		}
		testList = new ArrayList<String>(simsProps);
		testList.removeAll(m0Properties);
		Collections.sort(testList);
		System.out.println("Properties in SIMS and not in M0: " + testList);
		testList = new ArrayList<String>(m0Properties);
		testList.removeAll(simsProps);
		Collections.sort(testList);
		System.out.println("Properties in M0 and not in SIMS: " + testList);
	}

	public static void studySeries() {

		String baseURI = "http://baseUri/series/serie/";
		int baseURILength = baseURI.length();

		Model series = dataset.getNamedModel("http://rdf.insee.fr/graphe/series");

		Map<Integer, List<String>> uriList = new TreeMap<Integer, List<String>>();
		SortedSet<String> allProperties = new TreeSet<String>(); // All properties that exist in the model

		ResIterator subjectsIterator = series.listSubjects();
		while (subjectsIterator.hasNext()) {
			String uri = subjectsIterator.next().getURI();
			String seriesId = uri.substring(baseURILength).split("/")[0];
			// Series identifier appears to be an integer, with one exception (the "sequence" triple)
			try {
				Integer seriesIntId = Integer.parseInt(seriesId);
				if (!uriList.containsKey(seriesIntId)) uriList.put(seriesIntId, new ArrayList<String>());
				uriList.get(seriesIntId).add(uri);
			} catch (NumberFormatException e) {
				System.out.println("Invalid series URI " + uri);
			}
		}

		for (Integer id : uriList.keySet()) {
			List<String> properties = new ArrayList<String>();
			for (String propertyUri : uriList.get(id)) {
				String[] components = propertyUri.split("/");
				if (components.length == 7) properties.add(components[6]);
			}
			Collections.sort(properties);
			allProperties.addAll(properties);
			System.out.println(id + " " + properties);
		}
		System.out.println("All properties " + allProperties);
	}

	public static void studyFamilies() {

		String baseURI = "http://baseUri/familles/famille/";
		int baseURILength = baseURI.length();

		Model families = dataset.getNamedModel("http://rdf.insee.fr/graphe/familles");

		Map<Integer, List<String>> uriList = new TreeMap<Integer, List<String>>();

		ResIterator subjectsIterator = families.listSubjects();
		while (subjectsIterator.hasNext()) {
			String uri = subjectsIterator.next().getURI();
			String familyId = uri.substring(baseURILength).split("/")[0];
			// Family identifier appears to be an integer, with one exception
			try {
				Integer familyIntId = Integer.parseInt(familyId);
				if (!uriList.containsKey(familyIntId)) uriList.put(familyIntId, new ArrayList<String>());
				uriList.get(familyIntId).add(uri);
			} catch (NumberFormatException e) {
				System.out.println("Invalid family URI " + uri);
			}
		}

		for (Integer id : uriList.keySet()) {
			if (uriList.get(id).size() != 5) System.out.println("Invalid pattern for family " + id);
			System.out.println(id + " " + uriList.get(id));
		}
	}

	public static void studyOperations() {

		String baseURI = "http://baseUri/operations/operation/";
		int baseURILength = baseURI.length();

		Model operations = dataset.getNamedModel("http://rdf.insee.fr/graphe/operations");

		Map<Integer, List<String>> uriList = new TreeMap<Integer, List<String>>();

		ResIterator subjectsIterator = operations.listSubjects();
		while (subjectsIterator.hasNext()) {
			String uri = subjectsIterator.next().getURI();
			String operationId = uri.substring(baseURILength).split("/")[0];
			// Operation identifier appears to be an integer, with one exception
			try {
				Integer operationIntId = Integer.parseInt(operationId);
				if (!uriList.containsKey(operationIntId)) uriList.put(operationIntId, new ArrayList<String>());
				uriList.get(operationIntId).add(uri);
			} catch (NumberFormatException e) {
				System.out.println("Invalid operation URI " + uri);
			}
		}

		for (Integer id : uriList.keySet()) {
			if (uriList.get(id).size() != 5) System.out.println("Specific pattern for operation " + id);
			System.out.println(id + " " + uriList.get(id));
		}

		// Check values for ID_METIER
		Property sdmxValues = ResourceFactory.createProperty("http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#values");
		for (Integer id : uriList.keySet()) {
			Resource idMetierResource = ResourceFactory.createResource(baseURI + id + "/ID_DDS");
			List<RDFNode> idMetierValues = operations.listObjectsOfProperty(idMetierResource, sdmxValues).toList();
			if (idMetierValues.size() == 0) System.out.println("No DDS identifier for operation " + id);
			else if (idMetierValues.size() > 1) System.out.println("Invalid number of values for " + idMetierResource.getURI());
			else {
				if (!idMetierValues.get(0).toString().startsWith("OPE-")) System.out.println("Unexpected value for ID_DDS in operation " + id + ": " + idMetierValues.get(0).toString());
			}
		}
	}

	/**
	 * Study of the 'liens' model.
	 */
	public static void studyLinks() {

		String baseURI = "http://baseUri/liens/lien/";
		List<String> ignoredAttributes = Arrays.asList("VALIDATION_STATUS");

		if (dataset == null) dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model links = dataset.getNamedModel("http://rdf.insee.fr/graphe/liens");

		SortedSet<String> propertyList = new TreeSet<String>(); // List of all SIMS attributes that appear in the 'liens' model
		SortedSet<Integer> numberList = new TreeSet<Integer>(); // List of all the sequence numbers used to identify links
		SortedMap<Integer, SortedSet<String>> propertiesByLink = new TreeMap<Integer, SortedSet<String>>();

		// List all M0 attributes used in the 'liens' model
		StmtIterator statementIterator = links.listStatements();
		statementIterator.forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String subjectURI = statement.getSubject().getURI();
				String endPath = subjectURI.substring(subjectURI.lastIndexOf("/") + 1);
				try {
					Integer serialNumber = Integer.parseInt(endPath);
					numberList.add(serialNumber);
				} catch (NumberFormatException e) {
					if (!"sequence".equals(endPath)) {
						propertyList.add(subjectURI.substring(subjectURI.lastIndexOf("/") + 1));
						String[] lastPathElements = subjectURI.substring(baseURI.length()).split("/");
						if (!ignoredAttributes.contains(lastPathElements[1])) {
							Integer serialNumber = Integer.parseInt(lastPathElements[0]);
							if (!propertiesByLink.containsKey(serialNumber)) propertiesByLink.put(serialNumber, new TreeSet<String>());
							propertiesByLink.get(serialNumber).add(lastPathElements[1]);							
						}
					}
				}
			}
		});
		System.out.println("Attributes used in the 'liens' model:");
		for (String attributeName : propertyList) System.out.println(attributeName);
		System.out.println("\nSerial numbers used in the 'liens' model: " + numberList);
		System.out.println("\nList of properties for each link: ");
		for (Integer linkIndex : propertiesByLink.keySet()) System.out.println(linkIndex + "\t\t" + propertiesByLink.get(linkIndex));
	}

	/**
	 * Check that all attributes referenced in the SIMS M0 triples are valid SIMSFr attributes.
	 */
	public static void checkSIMSAttributes() {

		// Create the list of SIMSFr attribute names and other known attributes
		SIMSFrScheme simsFRScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		List<String> knownAttributes = new ArrayList<String>();
		for (SIMSFrEntry entry : simsFRScheme.getEntries())	knownAttributes.add(entry.getCode());
		// Add the 'technical' attributes
		knownAttributes.addAll(Arrays.asList("ID", "ID_DDS", "ID_METIER", "ASSOCIE_A", "sequence", "VALIDATION_STATUS"));

		// Open the 'documentations' model
		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model documentationM0Model = dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations");
		ResIterator resourceIterator = documentationM0Model.listSubjects();
		resourceIterator.forEachRemaining(new Consumer<Resource>() {
			@Override
			public void accept(Resource resource) {
				// Select last segment path of the URI and keep the non-numeric ones (otherwise it is a base resource)
				String lastSegment = StringUtils.substringAfterLast(resource.toString(), "/");
				if (!StringUtils.isNumeric(lastSegment)) {
					if (!knownAttributes.contains(lastSegment)) logger.error("Attribute not found in SIMSFr: " + lastSegment);
				}
			}
		});
		documentationM0Model.close();
	}

	/**
	 * Checks that the values of the direct properties of series or operations have the same values than in the 'documentations' part.
	 */
	public static void checkCoherence(boolean includeIndicators) {

		List<String> comparedAttributes = Configuration.propertyMappings.keySet().stream().collect(Collectors.toList()); // Can't directly use the key set which is immutable
		if (includeIndicators) comparedAttributes.add("FREQ_DISS"); // TODO Actually some series also have this attribute, which is a bug

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);

		// Read the mappings between operations/series and SIMS 'documentations'
		Model m0AssociationModel = dataset.getNamedModel(M0Converter.M0_BASE_GRAPH_URI + "associations");
		Map<String, String> attachmentMappings = M0SIMSConverter.extractSIMSAttachments(m0AssociationModel, includeIndicators); // Associations SIMS -> resources
		m0AssociationModel.close();

		// Make model containing both series and operations, and possibly indicators (families have no SIMS attached)
		Model m0Model = dataset.getNamedModel("http://rdf.insee.fr/graphe/series");
		m0Model.add(dataset.getNamedModel("http://rdf.insee.fr/graphe/operations"));
		if (includeIndicators) m0Model.add(dataset.getNamedModel("http://rdf.insee.fr/graphe/indicators"));
		Model documentationM0Model = dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations");
		// Select 'documentation' triples where subject corresponds to a SIMSFr attribute to compare and predicate is M0_VALUES
		Selector selector = new SimpleSelector(null, M0Converter.M0_VALUES, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return comparedAttributes.contains(StringUtils.substringAfterLast(statement.getSubject().getURI(), "/"));
	        }
	    };
	    documentationM0Model.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String simsURI = statement.getSubject().getURI();
				String simsResourceURI = StringUtils.substringBeforeLast(simsURI, "/");
				String attribute = StringUtils.substringAfterLast(simsURI, "/");
				if (!attachmentMappings.containsKey(simsResourceURI)) {
					logger.error("No URI mapping found for SIMSFr URI " + simsResourceURI);
					return;
				}
				// Eliminate the statements whose object is a 0-length string literal
				if ((statement.getObject().isLiteral()) && (statement.getObject().toString().trim().length() == 0)) return;
				// Get the value of the same attribute in the operations model
				String operationURI = attachmentMappings.get(simsResourceURI);
				Resource directAttributeResource = m0Model.createResource(operationURI + "/" + attribute);
				StmtIterator directIterator = m0Model.listStatements(directAttributeResource, M0Converter.M0_VALUES, (RDFNode) null);
				if (!directIterator.hasNext()) {
					logger.error("SIMS resource " + simsURI + " has no correspondance as direct attribute in resource " + operationURI);
					return;
				}
				while (directIterator.hasNext()) { // There should be exactly one occurrence of the attribute at this point
					// Compare objects of both statements
					Statement directM0Statement = directIterator.next();
					Node directNode = directM0Statement.getObject().asNode();
					if (!statement.getObject().asNode().matches(directNode)) {
						String logMessage = "Different values for " + simsURI + " and " + directAttributeResource.getURI() + ": '";
						logMessage += nodeToAbbreviatedString(statement.getObject()) + "' versus '" + nodeToAbbreviatedString(directM0Statement.getObject()) + "'";
						logger.error(logMessage);
						// Create the diff file
						String diffFileName = "src/main/resources/data/diffs/diff-" + StringUtils.substringAfterLast(simsResourceURI, "/") + "-" + attribute + ".txt";
						printDiffs(statement.getObject(), directM0Statement.getObject(), diffFileName);
					}					
				}
			}
		});
	    m0Model.close();
	    documentationM0Model.close();
	}

	/**
	 * Returns the list of distinct values of a given properties in the 'documentations' graph.
	 * 
	 * @param propertyName The name of the property to look for.
	 * @return The set of the distinct values of the property.
	 */
	public static Set<String> listPropertyValues(String propertyName) {

		Set<String> valueSet = new HashSet<String>();

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model documentations = dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations");

		Selector selector = new SimpleSelector(null, M0Converter.M0_VALUES, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject URI ends with the expected property name
	        public boolean selects(Statement statement) {
	        	if (statement.getSubject().getURI().endsWith("/" + propertyName)) return true; // To avoid mixing STATUS and VALIDATION_STATUS, for example
	        	return false;
	        }
	    };
	    documentations.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				valueSet.add(statement.getObject().toString());
			}
		});

		return valueSet;
	}

	/**
	 * Check that a given property in the 'documentations' graph takes its values from a list of valid values.
	 * 
	 * @param propertyName The name of the property to check.
	 * @return The set of the distinct values of the property.
	 */
	public static Model checkPropertyValues(String propertyName, Set<String> validValues) {

		Model invalidStatements = ModelFactory.createDefaultModel();

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model documentations = dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations");

		Selector selector = new SimpleSelector(null, M0Converter.M0_VALUES, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject URI ends with the expected property name
	        public boolean selects(Statement statement) {
	        	if (statement.getSubject().getURI().endsWith("/" + propertyName)) return true;
	        	return false;
	        }
	    };
	    documentations.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String codeValue = statement.getObject().toString();
				if (!validValues.contains(codeValue)) invalidStatements.add(statement);
			}
		});

		return invalidStatements;
	}

	private static String nodeToAbbreviatedString(RDFNode node) {

		if (node.isURIResource()) return node.asResource().getURI();
		if (node.isAnon()) return "<blank node>";
		// At this point, we have a literal node
		return StringUtils.abbreviateMiddle(node.asLiteral().getLexicalForm(), " (...) ", 100);
	}

	public static void printDiffs(RDFNode node1, RDFNode node2, String diffFileName) {

		if (!(node1.isLiteral() && node2.isLiteral())) return;
		PrintWriter diffWriter = null;
		try {
			diffWriter = new PrintWriter(diffFileName);
		} catch (FileNotFoundException e) {
			logger.error("Error creating the diff file", e);
			return;
		}
		String baseString = node1.asLiteral().getLexicalForm();
		String comparedString = node2.asLiteral().getLexicalForm();
		diffWriter.println("Base string\n" + baseString);
		diffWriter.println("\nCompared string\n" + comparedString);

		diffWriter.println("\nDifferences\n");
        Patch<String> patch = DiffUtils.diff(Arrays.asList(baseString.split("\n")), Arrays.asList(comparedString.split("\n")));
        for (Delta<String> delta: patch.getDeltas()) {
        	diffWriter.println(delta);
        }
        diffWriter.close();
	}

	public static void extractModels() throws IOException {

		dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);

		Iterator<String> nameIterator = dataset.listNames();
		while (nameIterator.hasNext()) System.out.println("Named graph: " + nameIterator.next());

		// Extract the code and codelists graphs
		dataset.getNamedModel("http://rdf.insee.fr/graphe/codelists").write(new FileWriter("src/main/resources/data/m0-codelists.ttl"), "TTL");
		dataset.getNamedModel("http://rdf.insee.fr/graphe/codes").write(new FileWriter("src/main/resources/data/m0-codes.ttl"), "TTL");

		// Extract the families, series and operations graphs
		dataset.getNamedModel("http://rdf.insee.fr/graphe/familles").write(new FileWriter("src/main/resources/data/m0-familles.ttl"), "TTL");
		dataset.getNamedModel("http://rdf.insee.fr/graphe/series").write(new FileWriter("src/main/resources/data/m0-series.ttl"), "TTL");
		dataset.getNamedModel("http://rdf.insee.fr/graphe/operations").write(new FileWriter("src/main/resources/data/m0-operations.ttl"), "TTL");

		// Extract other graphs
		dataset.getNamedModel("http://rdf.insee.fr/graphe/liens").write(new FileWriter("src/main/resources/data/m0-liens.ttl"), "TTL");
		dataset.getNamedModel("http://rdf.insee.fr/graphe/documents").write(new FileWriter("src/main/resources/data/m0-documents.ttl"), "TTL");
		dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations").write(new FileWriter("src/main/resources/data/m0-documentations.ttl"), "TTL");
		dataset.getNamedModel("http://rdf.insee.fr/graphe/associations").write(new FileWriter("src/main/resources/data/m0-associations.ttl"), "TTL");

		// New graphs in later versions
		dataset.getNamedModel("http://rdf.insee.fr/graphe/indicateurs").write(new FileWriter("src/main/resources/data/m0-indicateurs.ttl"), "TTL");
		dataset.getNamedModel("http://rdf.insee.fr/graphe/organismes").write(new FileWriter("src/main/resources/data/m0-organismes.ttl"), "TTL");

	}
}
