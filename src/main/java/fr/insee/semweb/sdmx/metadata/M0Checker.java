package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

/**
 * Methods for checking and reporting on information in the interim format ("M0 model").
 * 
 * @author Franck
 */
public class M0Checker {

	public static Logger logger = LogManager.getLogger(M0Checker.class);

	static Dataset dataset = null;

	/**
	 * Runs basic reporting on M0 families and returns a text report.
	 * 
	 * @param m0Families The Jena model containing M0 information about operations.
	 * @return A <code>String</code> containing the report.
	 */
	public static String checkFamilies(Model m0Families) {

		SortedMap<Integer, SortedSet<String>> attributesById = new TreeMap<Integer, SortedSet<String>>(); // Attributes used for each family identifier
		SortedSet<String> allAttributes = new TreeSet<String>(); // All attributes that exist in the model

		StringWriter report = new StringWriter().append("Study of families in the M0 model\n\n");

		ResIterator subjectsIterator = m0Families.listSubjects();
		while (subjectsIterator.hasNext()) {
			String familyURI = subjectsIterator.next().getURI();
			String[] uriComponents = familyURI.split("/");
			String familyId = uriComponents[uriComponents.length-2];
			String attributeId = uriComponents[uriComponents.length-1];
			// Family identifier should be an integer, with one exception (the "sequence" triple)
			try {
				Integer familyIntId = Integer.parseInt(familyId);
				if (!attributesById.containsKey(familyIntId)) attributesById.put(familyIntId, new TreeSet<String>());
				attributesById.get(familyIntId).add(attributeId);
			} catch (NumberFormatException e) {
				if ("sequence".equalsIgnoreCase(attributeId)) logger.error("Invalid family URI " + familyURI);
			}
		}
		report.append("Attributes filled for each family identifier\n");
		for (Integer familyId : attributesById.keySet()) {
			report.append(familyId + "\t");
			report.append(attributesById.get(familyId).toString()).append(System.lineSeparator());
			allAttributes.addAll(attributesById.get(familyId));
		}
		report.append("\nAll attributes used in the families\n" + allAttributes);

		return report.toString();
	}

	/**
	 * Runs basic reporting on M0 series and returns a text report.
	 * 
	 * @param m0SeriesModel The Jena model containing M0 information about series.
	 * @return A <code>String</code> containing the report.
	 */
	public static String checkSeries(Model m0SeriesModel) {

		SortedMap<Integer, SortedSet<String>> attributesById = new TreeMap<Integer, SortedSet<String>>(); // Attributes used for each series identifier
		SortedSet<String> allAttributes = new TreeSet<String>(); // All attributes that exist in the model

		StringWriter report = new StringWriter().append("Study of series in the M0 model\n\n");

		ResIterator subjectsIterator = m0SeriesModel.listSubjects(); // Lists all series URIs
		while (subjectsIterator.hasNext()) {
			String seriesURI = subjectsIterator.next().getURI();
			String[] uriComponents = seriesURI.split("/");
			String seriesId = uriComponents[uriComponents.length-2];
			String attributeId = uriComponents[uriComponents.length-1];
			// Series identifier should be an integer, with one exception (the "sequence" triple)
			try {
				Integer seriesIntId = Integer.parseInt(seriesId);
				if (!attributesById.containsKey(seriesIntId)) attributesById.put(seriesIntId, new TreeSet<String>());
				attributesById.get(seriesIntId).add(attributeId);
			} catch (NumberFormatException e) {
				if ("sequence".equalsIgnoreCase(attributeId)) logger.error("Invalid series URI " + seriesURI);
			}
		}
		report.append("Attributes filled for each series identifier\n");
		for (Integer seriesId : attributesById.keySet()) {
			report.append(seriesId + "\t");
			report.append(attributesById.get(seriesId).toString()).append(System.lineSeparator());
			allAttributes.addAll(attributesById.get(seriesId));
		}
		report.append("\nAll attributes used in the series\n" + allAttributes);

		return report.toString();
	}

	/**
	 * Runs basic reporting on M0 operations and returns a text report.
	 * 
	 * @param m0OperationsModel The Jena model containing M0 information about operations.
	 * @param attributeNames Names of M0 attributes for which values will be included in the report.
	 * @return A <code>String</code> containing the report.
	 */
	public static String checkOperations(Model m0OperationsModel, String... attributeNames) {

		SortedMap<Integer, SortedSet<String>> attributesById = new TreeMap<Integer, SortedSet<String>>(); // Attributes used for each operation identifier
		SortedSet<String> allAttributes = new TreeSet<String>(); // All attributes that exist in the model
		SortedMap<String, SortedMap<Integer, List<String>>> valuesByNameAndId = new TreeMap<String, SortedMap<Integer, List<String>>>();
		for (String attributeName : attributeNames) valuesByNameAndId.put(attributeName, new TreeMap<Integer, List<String>>());

		// Iterate on all subject resources that are operations (characterised by their type skos:Concept)
		m0OperationsModel.listStatements(null, RDF.type, SKOS.Concept).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement operationStatement) {
				Resource operationResource = operationStatement.getSubject();
				String operationURI = operationResource.getURI();
				String[] operationURIComponents = operationURI.split("/");
				String operationId = operationURIComponents[operationURIComponents.length - 1];
				try {
					Integer operationIntId = Integer.parseInt(operationId);
					SortedSet<String> operationAttributes = new TreeSet<>();
					// Now iterate on all statements linking the operation to its attributes via the 'varSims' predicate
					m0OperationsModel.listStatements(operationResource, Configuration.M0_VAR_SIMS, (RDFNode)null).forEachRemaining(new Consumer<Statement>() {
						@Override
						public void accept(Statement attributeStatement) {
							// Extract attribute name at the end of the object URI
							Resource attributeResource = attributeStatement.getObject().asResource(); // Should always be a resource
							String attributeURI = attributeResource.getURI();
							String[] attributeURIComponents = attributeURI.split("/");
							String attributeName = attributeURIComponents[attributeURIComponents.length - 1];
							operationAttributes.add(attributeName);
							if (valuesByNameAndId.containsKey(attributeName)) {
								// Iterate over the French values of the attribute
								List<String> attributeValues = new ArrayList<String>();
								m0OperationsModel.listStatements(attributeResource, Configuration.M0_VALUES, (RDFNode)null).forEachRemaining(new Consumer<Statement>() {
									@Override
									public void accept(Statement valueStatement) {
										attributeValues.add(valueStatement.getObject().asLiteral().getString() + "(fr)");
									}
								});
								m0OperationsModel.listStatements(attributeResource, Configuration.M0_VALUES_EN, (RDFNode)null).forEachRemaining(new Consumer<Statement>() {
									@Override
									public void accept(Statement valueStatement) {
										attributeValues.add(valueStatement.getObject().asLiteral().getString() + "(en)");
									}
								});
								valuesByNameAndId.get(attributeName).put(operationIntId, attributeValues);
							}
						}
					});
					attributesById.put(operationIntId, operationAttributes);
					allAttributes.addAll(operationAttributes);
				} catch (NumberFormatException e) {
					logger.error("Invalid operation URI " + operationURI);
				}
			}
		});

		// Write and return the report
		StringWriter report = new StringWriter().append("Study of operations in the M0 model\n\n");

		report.append("Attributes filled for each operation identifier\n");
		for (Integer operationId : attributesById.keySet()) {
			report.append(operationId + "\t");
			report.append(attributesById.get(operationId).toString()).append(System.lineSeparator());
			allAttributes.addAll(attributesById.get(operationId));
		}
		report.append("\nAll attributes used in the operations\n" + allAttributes);

		// Print values for specified attributes
		for (String attributeName : valuesByNameAndId.keySet()) {
			report.append("\nAll values for attribute " + attributeName + " by operation");
			for (Integer operationId : valuesByNameAndId.get(attributeName).keySet()) report.append("\n").append(operationId + "\t").append(valuesByNameAndId.get(attributeName).get(operationId).toString());
		}

		return report.toString();
	}

	/**
	 * Runs basic counts and coherence checks on M0 documentations and returns a text report.
	 * 
	 * @param m0DocumentationsModel The Jena model containing M0 information about documentations.
	 * @return A <code>String</code> containing the report.
	 */
	public static String checkDocumentations(Model m0DocumentationsModel) {

		StringWriter report = new StringWriter().append("Study of documemtations in the M0 model\n\n");

		// Build the mapping between documentation id (number) and the list of associated SIMS attributes.
		report.append("SIMS attributes by documentation identifier\n");
		SortedMap<Integer, SortedSet<String>> attributesByDocumentation = new TreeMap<Integer, SortedSet<String>>();
		ResIterator subjectsIterator = m0DocumentationsModel.listSubjects();
		while (subjectsIterator.hasNext()) {
			String documentationM0URI = subjectsIterator.next().getURI();
			String[] pathComponents = documentationM0URI.substring(Configuration.M0_SIMS_BASE_URI.length()).split("/");
			String documentationId = pathComponents[0]; // pathComponents is normally of the form [DOC_ID, ATTRIBUTE_NAME]
			// Documentation identifiers are integers (but careful with the sequence number)
			try {
				Integer documentationIntId = Integer.parseInt(documentationId);
				// Create map entry if it does not exist already
				if (!attributesByDocumentation.containsKey(documentationIntId)) attributesByDocumentation.put(documentationIntId, new TreeSet<String>());
				// In this case we make lists of attribute names, not full URIs
				if (pathComponents.length == 1) {
					report.append(Arrays.toString(pathComponents)).append(System.lineSeparator());
					if (!attributesByDocumentation.get(documentationIntId).add(pathComponents[1])) logger.warn("Duplicate values: " + Arrays.toString(pathComponents));
				}
				else logger.error("Invalid documentation URI: " + documentationM0URI);
			} catch (NumberFormatException e) {
				// Should be the sequence number resource: http://baseUri/documentations/documentation/sequence
				if (!("sequence".equals(documentationId))) logger.error("Invalid documentation URI: " + documentationM0URI);
			}
		}
		report.append("Found a total of " + attributesByDocumentation.size() + " documentations in the M0 model\n\n");

		// Build the list of all properties used in the M0 documentation model
		SortedSet<String> m0Attributes = new TreeSet<String>();
		for (Integer docId : attributesByDocumentation.keySet()) {
			report.append("Documentation #" + docId + " uses " + attributesByDocumentation.get(docId).size() + " properties\n");
			m0Attributes.addAll(attributesByDocumentation.get(docId));
		}
		report.append(m0Attributes.size() + " attributes are used in M0 'documentations' graph: " + m0Attributes);

		// Find the differences between the properties listed here and the SIMS/SIMSFr properties
		SIMSFrScheme simsFrScheme = null;
		try {
			simsFrScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		} catch (Exception e) {
			logger.error("Error while reading SIMSFr Excel file " + Configuration.SIMS_XLSX_FILE_NAME + " - " + e.getMessage());
			return report.toString();
		}
		SortedSet<String> simsAttributes = new TreeSet<String>();
		for (SIMSFrEntry entry : simsFrScheme.getEntries()) simsAttributes.add(entry.getCode()); // Sorted set of the attributes in the SIMSFr scheme
		SortedSet<String> deltaList = new TreeSet<String>(simsAttributes); // Make a copy in order to find duplicates without modifying the original

		report.append("\n\nProperties in SIMSFr and not in M0: " + deltaList.removeAll(m0Attributes));
		deltaList = new TreeSet<String>();
		report.append("\n\nProperties in M0 and not in SIMSFr: " + deltaList.removeAll(simsAttributes));

		simsAttributes = new TreeSet<String>();
		for (SIMSFrEntry entry : simsFrScheme.getEntries()) {
			if (!entry.isOriginal()) continue; // Ignore Insee's additions
			simsAttributes.add(entry.getCode());
		}

		return report.toString();
	}

	/**
	 * Checks that all attributes referenced in a M0 'documentations' model are valid SIMSFr attributes.
	 * An error will be logged for each attribute found in the model and not defined in SIMSFr.
	 */
	public static void checkSIMSAttributes(Model m0DocumentationsModel) {

		// Create the list of SIMSFr attribute names and other known attributes
		SIMSFrScheme simsFRScheme = SIMSFrScheme.readSIMSFrFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		SortedSet<String> knownAttributes = new TreeSet<String>();
		for (SIMSFrEntry entry : simsFRScheme.getEntries())	knownAttributes.add(entry.getCode());
		// Add the 'technical' attributes
		knownAttributes.addAll(Arrays.asList("ID", "ID_DDS", "ID_METIER", "ASSOCIE_A", "sequence", "VALIDATION_STATUS"));

		// Iterates over the 'documentations' model subjects
		ResIterator resourceIterator = m0DocumentationsModel.listSubjects();
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
	}

	/**
	 * Study of the 'liens' model.
	 * 
	 * @param export <code>File</code> object for an Excel file that will contain the properties of the links.
	 * @param report <code>File</code> object for a text file that will contain the report of the study.
	 */
	public static void studyLinks(File export, PrintStream report) {

		if (report == null) report = System.out;

		String baseURILink = "http://baseUri/liens/lien/";
		String baseURIDoc = "http://baseUri/documentations/documentation/";
		List<String> ignoredAttributes = Arrays.asList("ID", "ID_METIER", "VALIDATION_STATUS");
		List<String> directAttributes = Arrays.asList("SUMMARY", "TITLE", "TYPE", "URI");
		List<String> exportedAttributes = Arrays.asList("TITLE","TYPE", "URI", "SUMMARY"); // Attributes that will be included in the Excel export

		if (dataset == null) dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model links = dataset.getNamedModel("http://rdf.insee.fr/graphe/liens");
		Model associations = dataset.getNamedModel("http://rdf.insee.fr/graphe/associations");

		SortedSet<String> attributeList = new TreeSet<String>(); // List of all SIMS attributes that appear in the 'liens' model
		SortedMap<Integer, SortedSet<String>> attributesByLink = new TreeMap<Integer, SortedSet<String>>(); // List of attributes used for each link

		// List all M0 attributes used in the 'liens' model, globally and for each link
		StmtIterator statementIterator = links.listStatements();
		statementIterator.forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String subjectURI = statement.getSubject().getURI();
				String endPath = subjectURI.substring(subjectURI.lastIndexOf("/") + 1);
				try {
					Integer.parseInt(endPath); // Will raise an exception for the URIs ending with attribute name
				} catch (NumberFormatException e) {
					if (!"sequence".equals(endPath)) {
						attributeList.add(subjectURI.substring(subjectURI.lastIndexOf("/") + 1));
						String[] lastPathElements = subjectURI.substring(baseURILink.length()).split("/");
						if (!ignoredAttributes.contains(lastPathElements[1])) {
							Integer serialNumber = Integer.parseInt(lastPathElements[0]);
							if (!attributesByLink.containsKey(serialNumber)) attributesByLink.put(serialNumber, new TreeSet<String>());
							attributesByLink.get(serialNumber).add(lastPathElements[1]);							
						}
					}
				}
			}
		});
		report.println("Attributes used in the 'liens' model:");
		for (String attributeName : attributeList) report.println(attributeName);
		report.println("\nList of attributes for each link (ID, ID_METIER and VALIDATION_STATUS are ignored): ");
		for (Integer linkIndex : attributesByLink.keySet()) report.println(linkIndex + "\t\t" + attributesByLink.get(linkIndex));
		report.println("\nList of non-direct attributes for each link (excluded: " + directAttributes + "): ");
		for (Integer linkIndex : attributesByLink.keySet()) {
			attributesByLink.get(linkIndex).removeAll(directAttributes);
			report.println(linkIndex + "\t\t" + attributesByLink.get(linkIndex));
		}

		// Selectors on the French and English associations starting from a 'lien' resource 
		Selector selectorFr = new SimpleSelector(null, Configuration.M0_RELATED_TO, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return (statement.getSubject().getURI().startsWith(baseURILink));
	        }
	    };
		Selector selectorEn = new SimpleSelector(null, Configuration.M0_RELATED_TO_EN, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return (statement.getSubject().getURI().startsWith(baseURILink));
	        }
	    };
	    // Links (number/attribute) for each documentation (number/attribute)
		SortedMap<String, SortedSet<String>> linksByDocumentation = new TreeMap<String, SortedSet<String>>();
	    // Documentations (number/attribute) for each link (number/attribute)
		SortedMap<String, SortedSet<String>> documentationsByLink = new TreeMap<String, SortedSet<String>>();
		associations.listStatements(selectorFr).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String documentationPart = statement.getObject().toString().replaceAll(baseURIDoc, "");
				String linkPart = statement.getSubject().toString().replaceAll(baseURILink, "");
				if (!linksByDocumentation.containsKey(documentationPart)) linksByDocumentation.put(documentationPart, new TreeSet<String>());
				linksByDocumentation.get(documentationPart).add(linkPart);
				if (!documentationsByLink.containsKey(linkPart)) documentationsByLink.put(linkPart, new TreeSet<String>());
				documentationsByLink.get(linkPart).add(documentationPart);
			}
		});
		report.println("\nAssociations between documentations and French links:");
		for (String documentationPart : linksByDocumentation.keySet()) report.println(documentationPart + "\t" + linksByDocumentation.get(documentationPart));
		report.println("\nAssociations between French links and documentations:");
		for (String linkPart : documentationsByLink.keySet()) report.println(linkPart + "\t" + documentationsByLink.get(linkPart));

		associations.listStatements(selectorEn).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String documentationPart = statement.getObject().toString().replaceAll(baseURIDoc, "");
				String linkPart = statement.getSubject().toString().replaceAll(baseURILink, "");
				if (!linksByDocumentation.containsKey(documentationPart)) linksByDocumentation.put(documentationPart, new TreeSet<String>());
				linksByDocumentation.get(documentationPart).add(linkPart);
				if (!documentationsByLink.containsKey(linkPart)) documentationsByLink.put(linkPart, new TreeSet<String>());
				documentationsByLink.get(linkPart).add(documentationPart);
			}
		});
		report.println("\nAssociations between documentations and English links:");
		for (String documentationPart : linksByDocumentation.keySet()) report.println(documentationPart + "\t" + linksByDocumentation.get(documentationPart));
		report.println("\nAssociations between English links and documentations:");
		for (String linkPart : documentationsByLink.keySet()) report.println(linkPart + "\t" + documentationsByLink.get(linkPart));

		// Creation of the Excel report
		if (export != null) {
			Workbook workbook = new XSSFWorkbook();
			Sheet docSheet = workbook.createSheet("Documents");
			Row headerRow = docSheet.createRow(0);
			// Create header
			headerRow.createCell(0, CellType.STRING).setCellValue("Number");
			for (String attribute : exportedAttributes) {
				headerRow.createCell(exportedAttributes.indexOf(attribute) + 1, CellType.STRING).setCellValue(attribute);
			}
			// Create all the rows and first column
			SortedMap<Integer, Integer> rowIndexes = new TreeMap<Integer, Integer>();
			links.listStatements(new SimpleSelector(null, RDF.type, SKOS.Concept)).forEachRemaining(new Consumer<Statement>() {
				@Override
				public void accept(Statement statement) {
					Integer linkNumber = Integer.parseInt(StringUtils.substringAfterLast(statement.getSubject().toString(), "/"));
					rowIndexes.put(linkNumber, 0);
				}
			});
			int index = 1;
			for (Integer number : rowIndexes.keySet()) {
				docSheet.createRow(index).createCell(0, CellType.NUMERIC).setCellValue(number);
				rowIndexes.put(number, index++);
			}

			// Create cells for the values of the exported attributes
			links.listStatements().forEachRemaining(new Consumer<Statement>() {
				@Override
				public void accept(Statement statement) {
					// Select statements with 'values' and 'valuesGb' properties
					if (!(statement.getPredicate().equals(Configuration.M0_VALUES) || statement.getPredicate().equals(Configuration.M0_VALUES_EN))) return;
					// All subjects should start with the base links URI
					String variablePart = statement.getSubject().toString().replace(baseURILink, "");
					if (variablePart.length() == statement.getSubject().toString().length()) logger.warn("Unexpected subject URI in statement " + statement);
					String attributeName = variablePart.split("/")[1];
					if (!exportedAttributes.contains(attributeName)) return;
					Integer documentNumber = Integer.parseInt(variablePart.split("/")[0]);
					// Create cell
					String attributeValue = statement.getObject().toString();
					docSheet.getRow(rowIndexes.get(documentNumber)).createCell(exportedAttributes.indexOf(attributeName) + 1, CellType.STRING).setCellValue(attributeValue);
				}
			});
			// Adjust columns before writing the spreadsheet
			for (index = 0 ; index <= exportedAttributes.size(); index++) docSheet.autoSizeColumn(index);
			try {
				workbook.write(new FileOutputStream(export));
				report.println("\nExcel export written to " + export.getAbsolutePath());
			} catch (IOException e) {
				report.println("\nError: could not write Excel export");
			} finally {
				try {
					workbook.close();
				} catch (Exception ignored) { }
			}
		}
	}

	/**
	 * Study of the 'documents' model.
	 * 
	 * @param export <code>File</code> object for an Excel file that will contain the properties of the links.
	 * @param report <code>File</code> object for a text file that will contain the report of the study.
	 */
	public static void studyDocuments(File export, PrintStream report) {

		if (report == null) report = System.out;

		SortedSet<String> attributeSetFr = new TreeSet<String>(); // Set of SIMS attributes to which French documents are attached
		SortedSet<String> attributeSetEn = new TreeSet<String>(); // Set of SIMS attributes to which English documents are attached
		SortedMap<String, Integer> attributeCounts = new TreeMap<String, Integer>();

		String baseURI = "http://baseUri/documents/document/";

		List<String> ignoredAttributes = Arrays.asList("ID", "ID_METIER", "VALIDATION_STATUS");

		if (dataset == null) dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model documents = dataset.getNamedModel("http://rdf.insee.fr/graphe/documents");
		Model associations = dataset.getNamedModel("http://rdf.insee.fr/graphe/associations");

		// Selectors on the French and English associations starting from a 'document' resource 
		Selector selectorFr = new SimpleSelector(null, Configuration.M0_RELATED_TO, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return (statement.getSubject().getURI().startsWith(baseURI));
	        }
	    };
		Selector selectorEn = new SimpleSelector(null, Configuration.M0_RELATED_TO_EN, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return (statement.getSubject().getURI().startsWith(baseURI));
	        }
	    };

	    // List the document attributes associated to documentations in French and English
		associations.listStatements(selectorFr).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				attributeSetFr.add(StringUtils.substringAfterLast(statement.getSubject().toString(), "/"));
			}
		});
		associations.listStatements(selectorEn).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				attributeSetEn.add(StringUtils.substringAfterLast(statement.getSubject().toString(), "/"));
			}
		});

		// List all documents (NB: the selection on skos:Concept eliminates the 'sequence' resource
		SortedMap<Integer, SortedSet<String>> attributesByDocument = new TreeMap<Integer, SortedSet<String>>(); // List of attributes used for each document
		documents.listStatements(new SimpleSelector(null, RDF.type, SKOS.Concept)).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				Integer documentNumber = Integer.parseInt(StringUtils.substringAfterLast(statement.getSubject().toString(), "/"));
				attributesByDocument.put(documentNumber, new TreeSet<String>());
			}
		});
		// Now list all non-SIMS attributes for each document (NB: no M0_VALUES_EN properties in the 'documents' model
		// Take this opportunity to create the list of all direct attributes (appearing in the document model but not in the association model)
		Set<String> allDirectAttributes = new TreeSet<String>(ignoredAttributes);
		documents.listStatements(new SimpleSelector(null, Configuration.M0_VALUES, (RDFNode) null)).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String variablePart = statement.getSubject().toString().replace(baseURI, "");
				String attributeName = variablePart.split("/")[1];
				if (attributeSetFr.contains(attributeName) || attributeSetEn.contains(attributeName)) return; // We only want the direct attributes
				if (ignoredAttributes.contains(attributeName)) return; // We are not interested in the validation status, ID, or redundant ID_METIER attribute
				allDirectAttributes.add(attributeName);
				// Increment attribute count
				if (!attributeCounts.containsKey(attributeName)) attributeCounts.put(attributeName, 0);
				attributeCounts.put(attributeName, attributeCounts.get(attributeName) + 1);
				Integer documentNumber = Integer.parseInt(variablePart.split("/")[0]);
				if (!attributesByDocument.containsKey(documentNumber)) logger.error("Error: document number " + documentNumber + " not found (appears in " + baseURI + variablePart + ")");
				else attributesByDocument.get(documentNumber).add(attributeName);
			}
		});

		report.println("Number of documents: " + attributesByDocument.size());
		report.println("\nSIMS attributes to which French documents are attached:\n" + attributeSetFr);
		report.println("SIMS attributes to which English documents are attached:\n" + attributeSetEn);
		report.println("All direct document attributes: " + allDirectAttributes);
		report.println("\nDetail of direct attributes by document (excluding ID, ID_METIER and VALIDATION_STATUS):\n");
		for (Integer number : attributesByDocument.keySet()) report.println("Document number " + number + ":\t" + attributesByDocument.get(number));
		report.println("\nFrequencies of use of the attributes:\n");
		for (String attribute : attributeCounts.keySet()) report.println(attribute + " is used in " + attributeCounts.get(attribute) + " documents");

		// Go over the 'documents' model again to find the list of SIMS attributes for each document
		attributesByDocument.clear();
		Property varSIMSProperty = ResourceFactory.createProperty("http://rem.org/schema#varSims");
		documents.listStatements(new SimpleSelector(null, varSIMSProperty, (RDFNode) null)).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String variablePart = statement.getSubject().toString().replace(baseURI, "");
				if (!variablePart.contains("/")) return; // That is a root document URI
				String attributeName = variablePart.split("/")[1];
				if (allDirectAttributes.contains(attributeName)) return; // We only want the SIMS attributes
				Integer documentNumber = Integer.parseInt(variablePart.split("/")[0]);
				if (!attributesByDocument.containsKey(documentNumber)) attributesByDocument.put(documentNumber, new TreeSet<String>());
				attributesByDocument.get(documentNumber).add(attributeName);
			}
		});
		report.println("\nDetail of SIMS attributes by document (excluded: " + ignoredAttributes + "):\n");
		for (Integer number : attributesByDocument.keySet()) report.println("Document number " + number + ":\t" + attributesByDocument.get(number));
		// Same thing, eliminating the ASSOCIE_A attribute
		Set<String> attributesToRemove = new TreeSet<String>(Arrays.asList("ASSOCIE_A"));
		int exclusions = 0;
		for (Integer number : attributesByDocument.keySet()) if (attributesByDocument.get(number).removeAll(attributesToRemove)) exclusions++;
		report.println("\nDetail of SIMS attributes by document (further excluded: " + attributesToRemove + ", " + exclusions + " exclusions):\n");
		for (Integer number : attributesByDocument.keySet()) report.println("Document number " + number + ":\t" + attributesByDocument.get(number));

		// Finally, let us check that the association endpoints in 'documents' and 'associations' match
		SortedSet<String> orphans = new TreeSet<String>();
		// We have to keep track of already paired endpoints to avoid problem when 'relatedTo' and 'relatedToGb' exist for the same document/attribute
		SortedSet<String> paired = new TreeSet<String>();
		associations.listStatements().forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				// All properties in the 'associations' model are 'relatedTo' or 'relatedToGb', so no selection is needed
				if (!statement.getSubject().toString().startsWith(baseURI)) return; // Select statements about documents
				String variablePart = statement.getSubject().toString().replace(baseURI, "");
				String attributeName = variablePart.split("/")[1];
				if (allDirectAttributes.contains(attributeName)) return; // We only want the SIMS attributes
				Integer documentNumber = Integer.parseInt(variablePart.split("/")[0]);
				if (!attributesByDocument.containsKey(documentNumber)) {
					orphans.add("Document " + documentNumber);
					return;
				}
				if (!attributesByDocument.get(documentNumber).remove(attributeName)) {
					// When 'relatedTo' and 'relatedToGb' exist for the same attribute, the endpoint might already have been removed
					if (paired.contains(variablePart)) return;
					orphans.add("Document " + documentNumber + ", attribute " + attributeName);
					return;
				}
				paired.add(variablePart);
			}
		});
		report.println("\nCoherence between 'documents' and 'associations' graphs:\n");
		if (orphans.size() == 0) report.println("All endpoints found in the 'associations' graph match an endpoint in the 'documents' graph");
		else {
			report.println(orphans.size() + " endpoints referenced in the 'associations' graph but missing from the 'documents' graph");
			for (String orphan : orphans) report.println(orphan);
		}
		report.println();
		orphans.clear();
		for (Integer documentNumber : attributesByDocument.keySet()) if (attributesByDocument.get(documentNumber).size() != 0) orphans.add(String.valueOf(documentNumber + ": " + attributesByDocument.get(documentNumber)));
		if (orphans.size() == 0) report.println("All endpoints found in the 'documents' graph match an endpoint in the 'associations' graph");
		else {
			report.println(orphans.size() + " documents with endpoints referenced in the 'documents' graph but not matching an endpoint in the 'associations' graph");
			for (String orphan : orphans) report.println(orphan);
		}
		
		// For convenience reasons, the workbook is created even if it is not saved at the end
		Workbook workbook = new XSSFWorkbook();
		Sheet docSheet = workbook.createSheet("Documents");
		Row headerRow = docSheet.createRow(0);
		// Create header
		int index = 0;
		headerRow.createCell(index++, CellType.STRING).setCellValue("Number");
		for (String attribute : attributeCounts.keySet()) {
			headerRow.createCell(index, CellType.STRING).setCellValue(attribute);
			attributeCounts.put(attribute, index++); // Reuse attributeCounts for column indexes
		}
		// Create all the rows and first column
		SortedMap<Integer, Integer> rowIndexes = new TreeMap<Integer, Integer>();
		index = 1;
		for (Integer number : attributesByDocument.keySet()) {
			docSheet.createRow(index).createCell(0, CellType.NUMERIC).setCellValue(number);
			rowIndexes.put(number, index++);
		}
		orphans.clear();
		documents.listStatements(new SimpleSelector(null, Configuration.M0_VALUES, (RDFNode) null)).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String variablePart = statement.getSubject().toString().replace(baseURI, "");
				String attributeName = variablePart.split("/")[1];
				if (attributeSetFr.contains(attributeName) || attributeSetEn.contains(attributeName)) return; // Same as above
				if (ignoredAttributes.contains(attributeName) || attributesToRemove.contains(attributeName)) return; // Same as above
				Integer documentNumber = Integer.parseInt(variablePart.split("/")[0]);
				// Create cell
				String attributeValue = statement.getObject().toString();
				if (!rowIndexes.containsKey(documentNumber)) {
					// This is the case when a document has only direct attributes (no SIMS attributes)
					if (!orphans.contains(String.valueOf(documentNumber))) {
						// Avoids to create a new line for each direct attribute
						orphans.add(String.valueOf(documentNumber));
						int lastIndex = docSheet.getLastRowNum() + 1;
						rowIndexes.put(documentNumber, lastIndex);
						Row row = docSheet.createRow(lastIndex);
						row.createCell(0, CellType.NUMERIC).setCellValue(documentNumber);
					}
				}
				docSheet.getRow(rowIndexes.get(documentNumber)).createCell(attributeCounts.get(attributeName), CellType.STRING).setCellValue(attributeValue);
			}
		});

		if (orphans.size() == 0) report.println("\nAll document found in the 'documents' graph have at least one SIMS attribute");
		else {
			report.println("\n" + orphans.size() + " documents present in the 'documents' graph have no SIMS attribute, and thus no correspondence in the 'associations' graph: " + orphans);
			if (export != null) report.println("Details on these documents can be found at the end of the export spreadsheet");
		}

		if (export != null) {
			// Adjust columns before writing the spreadsheet
			for (index = 0 ; index < attributeCounts.keySet().size(); index++) docSheet.autoSizeColumn(index);
			try {
				workbook.write(new FileOutputStream(export));
				report.println("\nExcel export written to " + export.getAbsolutePath());
			} catch (IOException e) {
				report.println("\nError: could not write Excel export");
			} finally {
				try {
					workbook.close();
				} catch (Exception ignored) { }
			}				
		}
	}

	/**
	 * Selects the cases where documents have both a DATE and a DATE_PUBLICATION attributes, and compares the values.
	 */
	public static void checkDocumentDates() {

		if (dataset == null) dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentModel = dataset.getNamedModel("http://rdf.insee.fr/graphe/documents");
		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");

		// First create the list of documents that have a DATE attribute
		SortedMap<Integer, String> documentDates = new TreeMap<>();
		Selector selector = new SimpleSelector(null, Configuration.M0_VALUES, (RDFNode) null);
		m0DocumentModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String documentURI = statement.getSubject().getURI();
				if (documentURI.endsWith("/DATE")) {
					String dateString = statement.getObject().toString();
					Integer documentNumber = Integer.parseInt(StringUtils.substringAfterLast(documentURI.replace("/DATE", ""), "/"));
					documentDates.put(documentNumber, dateString);
					try {
						dateFormat.parse(dateString);
					} catch (ParseException e) {
						System.out.println("Unparseable date value: '" + dateString + "' for attribute DATE in document number " + documentNumber);
						return;
					}
				}
			}
		});
		// Then get the list of documents that have a DATE_PUBLICATION attribute
		SortedMap<Integer, String> documentPublicationDates = new TreeMap<>();
		m0DocumentModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String documentURI = statement.getSubject().getURI();
				if (documentURI.endsWith("/DATE_PUBLICATION")) {
					String datePublicationString = statement.getObject().toString();
					Integer documentNumber = Integer.parseInt(StringUtils.substringAfterLast(documentURI.replace("/DATE_PUBLICATION", ""), "/"));
					documentPublicationDates.put(documentNumber, datePublicationString);
					try {
						dateFormat.parse(datePublicationString);
					} catch (ParseException e) {
						System.out.println("Unparseable date value: '" + datePublicationString + "' for attribute DATE_PUBLICATION in document number " + documentNumber);
						return;
					}
				}
			}
		});

		documentDates.keySet().retainAll(documentPublicationDates.keySet()); // Keep only document numbers which are in both maps
		if (documentDates.size() > 0) System.out.println("\nBoth DATE and DATE_PUBLICATION attributes are defined for the following documents:");
		for (Integer documentNumber : documentDates.keySet()) {
			if (documentPublicationDates.containsKey(documentNumber)) {
				System.out.println(documentNumber + "\t" + documentDates.get(documentNumber) + "\t" + documentPublicationDates.get(documentNumber));
			}
		}
	}

	/**
	 * Checks that the values of the direct properties of series or operations have the same values than in the 'documentations' part.
	 */
	public static void checkCoherence(boolean includeIndicators) {

		List<String> comparedAttributes = Configuration.propertyMappings.keySet().stream().collect(Collectors.toList()); // Can't directly use the key set which is immutable
		if (includeIndicators) comparedAttributes.add("FREQ_DISS"); // TODO Actually some series also have this attribute, which is a bug

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);

		// Read the mappings between operations/series and SIMS 'documentations'
		Model m0AssociationModel = dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		Map<String, String> attachmentMappings = M0Extractor.extractSIMSAttachments(m0AssociationModel, includeIndicators); // Associations SIMS -> resources
		m0AssociationModel.close();

		// Make model containing both series and operations, and possibly indicators (families have no SIMS attached)
		Model m0Model = dataset.getNamedModel("http://rdf.insee.fr/graphe/series");
		m0Model.add(dataset.getNamedModel("http://rdf.insee.fr/graphe/operations"));
		if (includeIndicators) m0Model.add(dataset.getNamedModel("http://rdf.insee.fr/graphe/indicators"));
		Model documentationM0Model = dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations");
		// Select 'documentation' triples where subject corresponds to a SIMSFr attribute to compare and predicate is M0_VALUES
		Selector selector = new SimpleSelector(null, Configuration.M0_VALUES, (RDFNode) null) {
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
				StmtIterator directIterator = m0Model.listStatements(directAttributeResource, Configuration.M0_VALUES, (RDFNode) null);
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
	 * Returns the list of distinct values of a given attribute in the 'documentations' graph, sorted alphabetically.
	 * 
	 * @param m0DocumentationsModel The Jena model containing M0 information about documentations.
	 * @param attributeName The name of the attribute to look for.
	 * @return The sorted set of the distinct values of the attribute.
	 */
	public static SortedSet<String> listAttributeValues(Model m0DocumentationsModelf, String attributeName) {

		SortedSet<String> valueSet = new TreeSet<String>();

		Dataset dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
		Model m0DocumentationsModel = dataset.getNamedModel("http://rdf.insee.fr/graphe/documentations");

		Selector selector = new SimpleSelector(null, Configuration.M0_VALUES, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject URI ends with the expected property name
	        public boolean selects(Statement statement) {
	        	if (statement.getSubject().getURI().endsWith("/" + attributeName)) return true; // To avoid mixing STATUS and VALIDATION_STATUS, for example
	        	return false;
	        }
	    };
	    m0DocumentationsModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
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

		Selector selector = new SimpleSelector(null, Configuration.M0_VALUES, (RDFNode) null) {
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
        Patch<String> patch;
		try {
			patch = DiffUtils.diff(Arrays.asList(baseString.split("\n")), Arrays.asList(comparedString.split("\n")));
	        for (AbstractDelta<String> delta: patch.getDeltas()) {
	        	diffWriter.println(delta);
	        }
		} catch (DiffException e) {
			logger.error("Error while calculating the differences", e);
		}

        diffWriter.close();
	}

	/**
	 * Returns the set of all attributes used in a M0 model, sorted alphabetically.
	 * M0 attributes are those which correspond to the last path element of subject resources in the M0 model.
	 * 
	 * @param m0Model The M0 model to check.
	 * @return The sorted set of the M0 attributes used in the model.
	 */
	public static SortedSet<String> checkModelAttributes(Model m0Model) {
	
		SortedSet<String> attributes = new TreeSet<String>();
		m0Model.listStatements().forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String attributeName = StringUtils.substringAfterLast(statement.getSubject().getURI(), "/");
				 // Avoid base resources and special attribute 'sequence' (used to increment M0 identifier)
				if (!StringUtils.isNumeric(attributeName) && !("sequence".equals(attributeName)) && !attributes.contains(attributeName)) attributes.add(attributeName);
			}
		});
		return attributes;
	}
}
