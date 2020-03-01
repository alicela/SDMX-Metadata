package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Selector;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
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

	/**
	 * Runs basic reporting on M0 families and returns a text report.
	 * 
	 * @param m0FamiliesModel The Jena model containing M0 information about operations.
	 * @return A <code>String</code> containing the report.
	 */
	public static String checkFamilies(Model m0FamiliesModel) {

		SortedMap<Integer, SortedSet<String>> attributesById = new TreeMap<Integer, SortedSet<String>>(); // Attributes used for each family identifier
		SortedSet<String> allAttributes = new TreeSet<String>(); // All attributes that exist in the model

		StringWriter report = new StringWriter().append("Checks on information about families in the M0 model\n\n");

		ResIterator subjectsIterator = m0FamiliesModel.listSubjects();
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

		StringWriter report = new StringWriter().append("Checks on information about series in the M0 model\n\n");

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
		StringWriter report = new StringWriter().append("Checks on information about operations in the M0 model\n\n");

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

		StringWriter report = new StringWriter().append("Checks on information about of documemtations in the M0 model\n\n");

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
	 * 
	 * @param m0DocumentationsModel The Jena model containing M0 information about documentations.
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
	 * Runs basic counts on the 'liens' model and exports values for given attributes.
	 * 
	 * @param m0LinksModel The Jena model containing M0 information about links.
	 * @param m0AssociationsModel The Jena model containing M0 information about associations.
	 * @param export <code>File</code> object for an Excel file that will contain the properties of the links.
	 * @param attributesToExport The list of attributes that will be included in the Excel export.
	 * @return A <code>String</code> containing the report.
	 */
	public static String checkLinks(Model m0LinksModel, Model m0AssociationsModel, File export, List<String> attributesToExport) {

		String baseLinkURI = "http://baseUri/liens/lien/";

		SortedSet<String> ignoredAttributes = new TreeSet<>(Arrays.asList("ID", "ID_METIER", "VALIDATION_STATUS")); // Technical attributes not taken into consideration
		SortedSet<String> directAttributes = new TreeSet<>(Arrays.asList("SUMMARY", "TITLE", "TYPE", "URI")); // These attributes contain direct properties of the links

		StringWriter report = new StringWriter().append("Study of the links in the M0 model\n\n");

		SortedSet<String> attributeList = new TreeSet<String>(); // List of all SIMS attributes that appear in the 'liens' model
		SortedMap<Integer, SortedSet<String>> attributesByLinkId = new TreeMap<Integer, SortedSet<String>>(); // List of attributes used for each link

		// List all M0 attributes used in the 'liens' model, globally and for each link
		StmtIterator statementIterator = m0LinksModel.listStatements();
		statementIterator.forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String subjectURI = statement.getSubject().getURI();
				String[] uriComponents = subjectURI.split("/");
				String attributeName = uriComponents[uriComponents.length - 1];
				try {
					Integer.parseInt(attributeName); // Will raise an exception for the URIs ending with attribute name, which is what we are looking for
				} catch (NumberFormatException e) {
					if (!"sequence".equals(attributeName)) {
						attributeList.add(attributeName);
						if (!ignoredAttributes.contains(attributeName)) {
							Integer serialNumber = Integer.parseInt(uriComponents[uriComponents.length - 2]);
							if (!attributesByLinkId.containsKey(serialNumber)) attributesByLinkId.put(serialNumber, new TreeSet<String>());
							attributesByLinkId.get(serialNumber).add(attributeName);							
						}
					}
				}
			}
		});
		report.append("Attributes used in the 'liens' model:\n" + String.join(", ", attributeList));
		report.append("\n\nList of attributes for each link (" + String.join(", ", ignoredAttributes) + " are ignored): ");
		for (Integer linkId : attributesByLinkId.keySet()) report.append("\n" + linkId + "\t" + attributesByLinkId.get(linkId));
		report.append("\n\nList of non-direct attributes for each link (excluded: " + String.join(", ", directAttributes) + "): ");
		for (Integer linkId : attributesByLinkId.keySet()) {
			attributesByLinkId.get(linkId).removeAll(directAttributes);
			report.append("\n" + linkId + "\t" + attributesByLinkId.get(linkId));
		}

		// Selectors on the French and English associations starting from a 'lien' resource (and pointing to a 'documentation' resource)
		Selector frenchSelector = new SimpleSelector(null, Configuration.M0_RELATED_TO, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return (statement.getSubject().getURI().startsWith(baseLinkURI));
	        }
	    };
		Selector englishSelector = new SimpleSelector(null, Configuration.M0_RELATED_TO_EN, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return (statement.getSubject().getURI().startsWith(baseLinkURI));
	        }
	    };
	    // Links (number/attribute) for each documentation (number/attribute)
		SortedMap<String, SortedSet<String>> linksByDocumentation = new TreeMap<String, SortedSet<String>>();
	    // Documentations (number/attribute) for each link (number/attribute)
		SortedMap<String, SortedSet<String>> documentationsByLink = new TreeMap<String, SortedSet<String>>();
		// Fill the two maps for French associations
		m0AssociationsModel.listStatements(frenchSelector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String documentationPart = statement.getObject().toString().substring(Configuration.M0_SIMS_BASE_URI.length());
				String linkPart = statement.getSubject().toString().substring(baseLinkURI.length());
				if (!linksByDocumentation.containsKey(documentationPart)) linksByDocumentation.put(documentationPart, new TreeSet<String>());
				linksByDocumentation.get(documentationPart).add(linkPart);
				if (!documentationsByLink.containsKey(linkPart)) documentationsByLink.put(linkPart, new TreeSet<String>());
				documentationsByLink.get(linkPart).add(documentationPart);
			}
		});
		report.append("\n\nAssociations between documentations and French links:");
		for (String documentationPart : linksByDocumentation.keySet()) report.append("\n" + documentationPart + "\t" + linksByDocumentation.get(documentationPart));
		report.append("\n\nAssociations between French links and documentations:");
		for (String linkPart : documentationsByLink.keySet()) report.append("\n" + linkPart + "\t" + documentationsByLink.get(linkPart));

		// Fill the two maps for English associations
		m0AssociationsModel.listStatements(englishSelector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String documentationPart = statement.getObject().toString().substring(Configuration.M0_SIMS_BASE_URI.length());
				String linkPart = statement.getSubject().toString().substring(baseLinkURI.length());
				if (!linksByDocumentation.containsKey(documentationPart)) linksByDocumentation.put(documentationPart, new TreeSet<String>());
				linksByDocumentation.get(documentationPart).add(linkPart);
				if (!documentationsByLink.containsKey(linkPart)) documentationsByLink.put(linkPart, new TreeSet<String>());
				documentationsByLink.get(linkPart).add(documentationPart);
			}
		});
		report.append("\n\nAssociations between documentations and English links:");
		for (String documentationPart : linksByDocumentation.keySet()) report.append("\n" + documentationPart + "\t" + linksByDocumentation.get(documentationPart));
		report.append("\n\nAssociations between English links and documentations:");
		for (String linkPart : documentationsByLink.keySet()) report.append("\n" + linkPart + "\t" + documentationsByLink.get(linkPart));

		// Creation of the Excel report
		if ((export != null) && (attributesToExport != null) && (attributesToExport.size() > 0)) {
			logger.debug("Exporting " + attributesToExport + " to Excel spreadsheet");
			Workbook workbook = new XSSFWorkbook();
			Sheet docSheet = workbook.createSheet("Links");
			Row headerRow = docSheet.createRow(0);
			// Create header
			headerRow.createCell(0, CellType.STRING).setCellValue("Number");
			for (String attribute : attributesToExport) {
				headerRow.createCell(attributesToExport.indexOf(attribute) + 1, CellType.STRING).setCellValue(attribute);
			}
			// Create all the rows and first column
			SortedMap<Integer, Integer> rowIndexes = new TreeMap<Integer, Integer>();
			m0LinksModel.listStatements(new SimpleSelector(null, RDF.type, SKOS.Concept)).forEachRemaining(new Consumer<Statement>() {
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
			m0LinksModel.listStatements().forEachRemaining(new Consumer<Statement>() {
				@Override
				public void accept(Statement statement) {
					// Select statements with 'values' and 'valuesGb' properties
					if (!(statement.getPredicate().equals(Configuration.M0_VALUES) || statement.getPredicate().equals(Configuration.M0_VALUES_EN))) return;
					// All subjects should start with the base links URI
					String variablePart = statement.getSubject().toString().replace(baseLinkURI, "");
					if (variablePart.length() == statement.getSubject().toString().length()) logger.warn("Unexpected subject URI in statement " + statement);
					String attributeName = variablePart.split("/")[1];
					if (!attributesToExport.contains(attributeName)) return;
					Integer documentNumber = Integer.parseInt(variablePart.split("/")[0]);
					// Create cell
					String attributeValue = statement.getObject().toString();
					docSheet.getRow(rowIndexes.get(documentNumber)).createCell(attributesToExport.indexOf(attributeName) + 1, CellType.STRING).setCellValue(attributeValue);
				}
			});
			// Adjust columns before writing the spreadsheet
			for (index = 0 ; index <= attributesToExport.size(); index++) docSheet.autoSizeColumn(index);
			try {
				workbook.write(new FileOutputStream(export));
				logger.debug("Excel export written to " + export.getAbsolutePath());
			} catch (IOException e) {
				logger.error("Error: could not write Excel export");
			} finally {
				try {
					workbook.close();
				} catch (Exception ignored) { }
			}
		}
		return report.toString();
	}

	/**
	 * Runs basic counts on the 'documents' model and exports values for given attributes.
	 * 
	 * @param m0DocumentsModel The Jena model containing M0 information about documents.
	 * @param m0AssociationsModel The Jena model containing M0 information about associations.
	 * @param export <code>File</code> object for an Excel file that will contain the properties of the documents.
	 * @return A <code>String</code> containing the report.
	 */
	public static String checkDocuments(Model m0DocumentsModel, Model m0AssociationsModel, File export) {

		StringWriter report = new StringWriter().append("Study of the documents in the M0 model\n\n");

		SortedSet<String> attributeSetFr = new TreeSet<String>(); // Set of SIMS attributes to which French documents are attached
		SortedSet<String> attributeSetEn = new TreeSet<String>(); // Set of SIMS attributes to which English documents are attached
		SortedMap<String, Integer> attributeCounts = new TreeMap<String, Integer>();

		String baseDocumentURI = "http://baseUri/documents/document/";

		List<String> ignoredAttributes = Arrays.asList("ID", "ID_METIER", "VALIDATION_STATUS");

		// Selectors on the French and English associations starting from a 'document' resource (and pointing to a 'documentation' resource)
		Selector selectorFr = new SimpleSelector(null, Configuration.M0_RELATED_TO, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return (statement.getSubject().getURI().startsWith(baseDocumentURI));
	        }
	    };
		Selector selectorEn = new SimpleSelector(null, Configuration.M0_RELATED_TO_EN, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return (statement.getSubject().getURI().startsWith(baseDocumentURI));
	        }
	    };

	    // List the document attributes associated to documentations in French and English
		m0AssociationsModel.listStatements(selectorFr).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				attributeSetFr.add(StringUtils.substringAfterLast(statement.getSubject().toString(), "/"));
			}
		});
		m0AssociationsModel.listStatements(selectorEn).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				attributeSetEn.add(StringUtils.substringAfterLast(statement.getSubject().toString(), "/"));
			}
		});

		// List all documents (NB: the selection on skos:Concept eliminates the 'sequence' resource)
		SortedMap<Integer, SortedSet<String>> attributesByDocument = new TreeMap<Integer, SortedSet<String>>(); // List of attributes used for each document
		m0DocumentsModel.listStatements(new SimpleSelector(null, RDF.type, SKOS.Concept)).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				Integer documentNumber = Integer.parseInt(StringUtils.substringAfterLast(statement.getSubject().toString(), "/"));
				attributesByDocument.put(documentNumber, new TreeSet<String>());
			}
		});
		// Now list all non-SIMS attributes for each document (NB: no M0_VALUES_EN properties in the 'documents' model
		// Take this opportunity to create the list of all direct attributes (appearing in the document model but not in the association model)
		Set<String> allDirectAttributes = new TreeSet<String>(ignoredAttributes);
		m0DocumentsModel.listStatements(new SimpleSelector(null, Configuration.M0_VALUES, (RDFNode) null)).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String variablePart = statement.getSubject().toString().substring(baseDocumentURI.length());
				String attributeName = variablePart.split("/")[1];
				if (attributeSetFr.contains(attributeName) || attributeSetEn.contains(attributeName)) return; // We only want the direct attributes
				if (ignoredAttributes.contains(attributeName)) return; // We are not interested in the validation status, ID, or redundant ID_METIER attribute
				allDirectAttributes.add(attributeName);
				// Increment attribute count
				if (!attributeCounts.containsKey(attributeName)) attributeCounts.put(attributeName, 0);
				attributeCounts.put(attributeName, attributeCounts.get(attributeName) + 1);
				Integer documentNumber = Integer.parseInt(variablePart.split("/")[0]);
				if (!attributesByDocument.containsKey(documentNumber)) logger.error("Error: document number " + documentNumber + " not found (appears in " + baseDocumentURI + variablePart + ")");
				else attributesByDocument.get(documentNumber).add(attributeName);
			}
		});

		report.append("Number of documents: " + attributesByDocument.size());
		report.append("\n\nSIMS attributes to which French documents are attached:\n" + attributeSetFr);
		report.append("\nSIMS attributes to which English documents are attached:\n" + attributeSetEn);
		report.append("\nAll direct document attributes: " + allDirectAttributes);
		report.append("\nDetail of direct attributes by document (" + String.join(", ", ignoredAttributes) + " are ignored):");
		for (Integer number : attributesByDocument.keySet()) report.append("\nDocument number " + number + ":\t" + attributesByDocument.get(number));
		report.append("\nFrequencies of use of the attributes:\n");
		for (String attribute : attributeCounts.keySet()) report.append("\n" + attribute + " is used in " + attributeCounts.get(attribute) + " documents");

		// Go over the 'documents' model again to find the list of SIMS attributes for each document
		attributesByDocument.clear();
		m0DocumentsModel.listStatements(new SimpleSelector(null, Configuration.M0_VAR_SIMS, (RDFNode) null)).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String variablePart = statement.getSubject().toString().substring(baseDocumentURI.length());
				if (!variablePart.contains("/")) return; // That is a root document URI
				String attributeName = variablePart.split("/")[1];
				if (allDirectAttributes.contains(attributeName)) return; // We only want the SIMS attributes
				Integer documentNumber = Integer.parseInt(variablePart.split("/")[0]);
				if (!attributesByDocument.containsKey(documentNumber)) attributesByDocument.put(documentNumber, new TreeSet<String>());
				attributesByDocument.get(documentNumber).add(attributeName);
			}
		});
		report.append("\nDetail of SIMS attributes by document (" + String.join(", ", ignoredAttributes) + " are ignored):");
		for (Integer number : attributesByDocument.keySet()) report.append("\nDocument number " + number + ":\t" + attributesByDocument.get(number));
		// Same thing, eliminating the ASSOCIE_A attribute
		Set<String> attributesToRemove = new TreeSet<String>(Arrays.asList("ASSOCIE_A"));
		int exclusions = 0;
		for (Integer number : attributesByDocument.keySet()) if (attributesByDocument.get(number).removeAll(attributesToRemove)) exclusions++;
		report.append("\n\nDetail of SIMS attributes by document (further excluded: " + attributesToRemove + ", " + exclusions + " exclusions):");
		for (Integer number : attributesByDocument.keySet()) report.append("\nDocument number " + number + ":\t" + attributesByDocument.get(number));

		// Finally, let us check that the association endpoints in 'documents' and 'associations' match
		SortedSet<String> orphans = new TreeSet<String>();
		// We have to keep track of already paired endpoints to avoid problem when 'relatedTo' and 'relatedToGb' exist for the same document/attribute
		SortedSet<String> paired = new TreeSet<String>();
		m0AssociationsModel.listStatements().forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				// All properties in the 'associations' model are 'relatedTo' or 'relatedToGb', so no selection is needed
				if (!statement.getSubject().toString().startsWith(baseDocumentURI)) return; // Select statements about documents
				String variablePart = statement.getSubject().toString().substring(baseDocumentURI.length());
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
		report.append("\n\nCoherence between 'documents' and 'associations' graphs:\n");
		if (orphans.size() == 0) report.append("All endpoints found in the 'associations' graph match an endpoint in the 'documents' graph");
		else {
			report.append(orphans.size() + " endpoints referenced in the 'associations' graph but missing from the 'documents' graph\n");
			for (String orphan : orphans) report.append("\n" + orphan);
		}
		orphans.clear();
		for (Integer documentNumber : attributesByDocument.keySet()) if (attributesByDocument.get(documentNumber).size() != 0) orphans.add(String.valueOf(documentNumber + ": " + attributesByDocument.get(documentNumber)));
		if (orphans.size() == 0) report.append("\n\nAll endpoints found in the 'documents' graph match an endpoint in the 'associations' graph");
		else {
			report.append("\n\n" + orphans.size() + " documents with endpoints referenced in the 'documents' graph but not matching an endpoint in the 'associations' graph:");
			for (String orphan : orphans) report.append("\n" + orphan);
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
		m0DocumentsModel.listStatements(new SimpleSelector(null, Configuration.M0_VALUES, (RDFNode) null)).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String variablePart = statement.getSubject().toString().replace(baseDocumentURI, "");
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

		if (orphans.size() == 0) report.append("\n\nAll document found in the 'documents' graph have at least one SIMS attribute");
		else {
			report.append("\n\n" + orphans.size() + " documents present in the 'documents' graph have no SIMS attribute, and thus no correspondence in the 'associations' graph: " + orphans);
			if (export != null) report.append("\nDetails on these documents can be found at the end of the export spreadsheet");
		}

		if (export != null) {
			// Adjust columns before writing the spreadsheet
			for (index = 0 ; index < attributeCounts.keySet().size(); index++) docSheet.autoSizeColumn(index);
			try {
				workbook.write(new FileOutputStream(export));
				logger.debug("Excel export written to " + export.getAbsolutePath());
			} catch (IOException e) {
				logger.error("Error: could not write Excel export");
			} finally {
				try {
					workbook.close();
				} catch (Exception ignored) { }
			}				
		}
		return report.toString();
	}

	/**
	 * Lists the cases of presence of DATE and a DATE_PUBLICATION attributes on documents, and compares the values when both are present.
	 * 
	 * @param m0DocumentsModel The Jena model containing M0 information about documents.
	 */
	public static String checkDocumentDates(Model m0DocumentsModel) {

		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
		StringWriter report = new StringWriter().append("Study of the document dates in the M0 model\n\n");

		// First create the list of documents that have a DATE attribute
		SortedMap<Integer, String> documentDates = new TreeMap<>();
		Selector selector = new SimpleSelector(null, Configuration.M0_VALUES, (RDFNode) null);
		m0DocumentsModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
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
						logger.error("Unparseable date value: '" + dateString + "' for attribute DATE in document number " + documentNumber);
					}
				}
			}
		});
		// Then get the list of documents that have a DATE_PUBLICATION attribute
		SortedMap<Integer, String> documentPublicationDates = new TreeMap<>();
		m0DocumentsModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
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
						logger.error("Unparseable date value: '" + datePublicationString + "' for attribute DATE_PUBLICATION in document number " + documentNumber);
					}
				}
			}
		});

		SortedSet<Integer> commonIds = new TreeSet<Integer>(CollectionUtils.intersection(documentDates.keySet(), documentPublicationDates.keySet())); // Keep only document numbers which are in both maps
		if (commonIds.size() == 0) report.append("No documents have both a DATE and a DATE_PUBLICATION");
		else report.append("Both DATE and DATE_PUBLICATION attributes are defined for the following documents:");
		for (Integer documentNumber : commonIds) {
			report.append("\n" + documentNumber + "\t" + documentDates.get(documentNumber) + "\t" + documentPublicationDates.get(documentNumber) + "\t");
			report.append((documentDates.get(documentNumber).equals(documentPublicationDates.get(documentNumber))) ? "(=)" : "(≠)");
		}

		documentDates.keySet().removeAll(commonIds); // Eliminate common numbers from the list of documents that have a DATE
		if (documentDates.keySet().size() == 0) report.append("\n\nNo documents have a DATE and no DATE_PUBLICATION");
		else report.append("\n\nThe following documents have a DATE but no DATE_PUBLICATION:");
		for (Integer documentNumber : documentDates.keySet()) report.append("\n" + documentNumber + "\t" + documentDates.get(documentNumber));

		documentPublicationDates.keySet().removeAll(commonIds); // Eliminate common numbers from the list of documents that have a DATE_PUBLICATION
		if (documentPublicationDates.keySet().size() == 0) report.append("\n\nNo documents have a DATE_PUBLICATION and no DATE");
		else report.append("\n\nThe following documents have a DATE_PUBLICATION but no DATE:");
		for (Integer documentNumber : documentPublicationDates.keySet()) report.append("\n" + documentNumber + "\t" + documentPublicationDates.get(documentNumber));

		return report.toString();
	}

	/**
	 * Checks that the values of the direct attributes of series or operations have the same values than in the 'documentations' part.
	 * Detected differences are written to diff files for each documentation identifier and attribute name.
	 * 
	 * @param m0Dataset The Jena dataset containing all M0 information.
	 */
	public static void checkModelCoherence(Dataset m0Dataset, boolean includeIndicators) {

		SortedSet<String> comparedAttributes = new TreeSet<String>(Configuration.propertyMappings.keySet()); // Can't directly use the key set which is immutable
		if (includeIndicators) comparedAttributes.add("FREQ_DISS"); // TODO Actually some series also have this attribute, which is a bug

		// Read the mappings between operations/series and SIMS 'documentations'
		Model m0AssociationModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "associations");
		Map<String, String> attachmentMappings = M0Extractor.extractSIMSAttachments(m0AssociationModel, includeIndicators); // Associations SIMS -> resources
		m0AssociationModel.close();

		// Make a model containing both series and operations, and possibly indicators (families have no SIMS attached)
		Model m0Model = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "series");
		m0Model.add(m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "operations"));
		if (includeIndicators) m0Model.add(m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "indicators"));

		// Select the 'documentation' triples where the subject corresponds to a SIMSFr attribute to compare and the predicate is M0_VALUES
		Model m0DocumentationsModel = m0Dataset.getNamedModel(Configuration.M0_BASE_GRAPH_URI + "documentations");
		Selector m0DocumentationSelector = new SimpleSelector(null, Configuration.M0_VALUES, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return comparedAttributes.contains(StringUtils.substringAfterLast(statement.getSubject().getURI(), "/"));
	        }
	    };
	    // Go through the selector
	    m0DocumentationsModel.listStatements(m0DocumentationSelector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String simsAttributeURI = statement.getSubject().getURI();
				String simsDocumentationURI = StringUtils.substringBeforeLast(simsAttributeURI, "/");
				String attributeName = StringUtils.substringAfterLast(simsAttributeURI, "/");
				if (!attachmentMappings.containsKey(simsDocumentationURI)) {
					logger.error("Documentation " + simsDocumentationURI + " is not attached to any resource");
					return;
				}
				// Eliminate the statements whose object is a 0-length string literal
				if ((statement.getObject().isLiteral()) && (statement.getObject().toString().trim().length() == 0)) return;
				// Get the value of the same attribute as a direct attribute of the operations-like resource
				String documentedResourceURI = attachmentMappings.get(simsDocumentationURI);
				Resource directAttributeResource = m0Model.createResource(documentedResourceURI + "/" + attributeName);
				StmtIterator directValuesIterator = m0Model.listStatements(directAttributeResource, Configuration.M0_VALUES, (RDFNode) null);
				if (!directValuesIterator.hasNext()) {
					logger.error("SIMS attribute resource " + simsAttributeURI + " has no correspondance as direct attribute in resource " + documentedResourceURI);
					return;
				}
				while (directValuesIterator.hasNext()) { // There should be exactly one occurrence of the attribute at this point
					// Compare objects of both statements
					Statement m0DirectStatement = directValuesIterator.next();
					Node directNode = m0DirectStatement.getObject().asNode();
					if (!statement.getObject().asNode().matches(directNode)) {
						String logMessage = "Different values for " + simsAttributeURI + " and " + directAttributeResource.getURI() + ": '";
						logMessage += nodeToAbbreviatedString(statement.getObject()) + "' versus '" + nodeToAbbreviatedString(m0DirectStatement.getObject()) + "'";
						logger.error(logMessage);
						// Create the diff file for the documentation id and the attribute name
						String diffFileName = "src/main/resources/data/diffs/diff-" + StringUtils.substringAfterLast(simsDocumentationURI, "/") + "-" + attributeName + ".txt";
						printDiffs(statement.getObject(), m0DirectStatement.getObject(), diffFileName);
					}					
				}
			}
		});
	    m0Model.close();
	    m0DocumentationsModel.close();
	}

	/**
	 * Returns the list of distinct values of a given attribute in the 'documentations' graph, sorted alphabetically.
	 * 
	 * @param m0DocumentationsModel The Jena model containing M0 information about documentations.
	 * @param attributeName The name of the attribute to look for.
	 * @return The sorted set of the distinct values of the attribute.
	 */
	public static SortedSet<String> listAttributeValues(Model m0DocumentationsModel, String attributeName) {

		SortedSet<String> valueSet = new TreeSet<String>();

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
	 * @param m0DocumentationsModel The Jena model containing M0 information about documentations.
	 * @param propertyName The name of the property to check.
	 * @param validValues The set of valid values of the property to check.
	 * @return A Jena <code>Model</code> containing the M0 statements where the set of the distinct values of the property.
	 */
	public static Model checkCodedAttributeValues(Model m0DocumentationsModel, String propertyName, Set<String> validValues) {

		Model invalidCodesM0Model = ModelFactory.createDefaultModel();

		Selector selector = new SimpleSelector(null, Configuration.M0_VALUES, (RDFNode) null) {
			// Override the 'selects' method to retain only statements whose subject URI ends with the expected property name
	        public boolean selects(Statement statement) {
	        	if (statement.getSubject().getURI().endsWith("/" + propertyName)) return true;
	        	return false;
	        }
	    };
	    // Lists values with the given property as subject and extract those whose object value is not in the expected list
	    m0DocumentationsModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String codeValue = statement.getObject().toString();
				if (!validValues.contains(codeValue)) invalidCodesM0Model.add(statement);
			}
		});

		return invalidCodesM0Model;
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
	public static SortedSet<String> getModelAttributes(Model m0Model) {
	
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
