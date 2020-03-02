package eu.casd.semweb.psp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import fr.insee.semweb.sdmx.metadata.Configuration;

public class PSPModelMaker {

	public static Logger logger = LogManager.getLogger(PSPModelMaker.class);

	// Create some properties and classes that will be used
	public static Property wasGeneratedBy = ResourceFactory.createProperty("http://www.w3.org/ns/prov#wasGeneratedBy");
	// TODO The following property should be created in the Insee vocabulary
	public static Property methodologicalNote = ResourceFactory.createProperty(PSPModelMaker.INSEE_ONTO_BASE_URI + "methodologicalNote");

	public static void main(String[] args) {

		// Main model: information on the programs, sources and products
		Model pspModel = ModelFactory.createDefaultModel();
		pspModel.setNsPrefix("rdfs", RDFS.getURI());
		pspModel.setNsPrefix("skos", SKOS.getURI());
		pspModel.setNsPrefix("dcterms", DCTerms.getURI());
		pspModel.setNsPrefix("insee", PSPModelMaker.INSEE_ONTO_BASE_URI);
		pspModel.setNsPrefix("dcat", DCAT.getURI());
		pspModel.setNsPrefix("prov", "http://www.w3.org/ns/prov#");

		// We can also create a basic DCAT catalog
		Model dcatModel = ModelFactory.createDefaultModel();
		dcatModel.setNsPrefix("dcat", DCAT.getURI());
		dcatModel.setNsPrefix("dcterms", DCTerms.getURI());
		dcatModel.setNsPrefix("prov", "http://www.w3.org/ns/prov#");

		Resource pspCatalog = dcatModel.createResource(eu.casd.semweb.psp.CASDConfiguration.CASD_PRODUCTS_BASE_URI + "catalog", DCAT.Catalog);
		pspCatalog.addProperty(DCTerms.title, dcatModel.createLiteral("Produits mis à disposition par le CASD", "fr"));
		Calendar now = Calendar.getInstance();
		pspCatalog.addProperty(DCTerms.issued, dcatModel.createTypedLiteral(now));
		pspCatalog.addProperty(DCTerms.modified, dcatModel.createTypedLiteral(now));
		pspCatalog.addProperty(DCTerms.language, dcatModel.createResource("http://id.loc.gov/vocabulary/iso639-1/fr"));

		// Open the Excel file and get the relevant sheet
		Workbook pdpWorkbook = null;
		Sheet simsSheet = null;
		try {
			pdpWorkbook = WorkbookFactory.create(new File(PSPModelMaker.EXCEL_FILE));
			simsSheet = pdpWorkbook.getSheetAt(4);
		} catch (Exception e) {
			logger.fatal("Error while opening Excel file - " + e.getMessage());
		}

		Iterator<Row> rows = simsSheet.rowIterator();
		rows.next(); rows.next(); rows.next(); // Skip 3 title lines
		
		while (rows.hasNext()) {
			Row row = rows.next();

			// First cell is the name of the StatisticalOperationFamily
			String sofName = row.getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			// Create the resource without type to be able to test existence test later
			Resource sofResource = pspModel.createResource(fr.insee.semweb.sdmx.metadata.Configuration.statisticalOperationFamilyURI(sofName));

			if (!pspModel.contains(sofResource, RDF.type)) {
				// Family not yet created in the model: create with attributes
				sofResource.addProperty(RDF.type, Configuration.STATISTICAL_OPERATION_FAMILY);
				// Add the statistical operation family name as both rdfs:label and skos:prefLabel
				sofResource.addProperty(RDFS.label, pspModel.createLiteral(sofName, "fr"));
				sofResource.addProperty(SKOS.prefLabel, pspModel.createLiteral(sofName, "fr"));
				// Thematic coverage is described in column B. For now we create a unique literal value of dcterms:subject
				// TODO Separate the different themes into several dcterms:subject values, or even use a controlled vocabulary ?
				String cellValue = row.getCell(1, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
				sofResource.addProperty(DCTerms.subject, pspModel.createLiteral(cellValue, "fr"));
				// Methodological documentation is in column C, always empty for now
				cellValue = row.getCell(2, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
				if (cellValue.length() > 0) sofResource.addProperty(methodologicalNote, pspModel.createLiteral(cellValue, "en"));
			}
			// Create the statistical operation series; prefLabel in column E, altLabel in column D
			String sosShortName = row.getCell(3, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			Resource sosResource = pspModel.createResource(fr.insee.semweb.sdmx.metadata.Configuration.statisticalOperationSeriesURI(sosShortName), Configuration.STATISTICAL_OPERATION_SERIES);
			sosResource.addProperty(SKOS.altLabel, pspModel.createLiteral(sosShortName, "fr"));
			sosResource.addProperty(SKOS.prefLabel, pspModel.createLiteral(row.getCell(4, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim(), "fr"));
			sosResource.addProperty(DCTerms.isPartOf, sofResource);

			// Gets the value that specifies the different dcat:Dataset instances (column H)
			// TODO Add also as dc:temporal value? What to do when value is empty?
			String temporal = row.getCell(7, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			List<String> datasetYears = null;
			if (temporal.length() > 0) {
				datasetYears = getYears(temporal);
				// Create a dcat:Dataset for each year or span
				for (String datasetYear : datasetYears) {
					String datasetURI = eu.casd.semweb.psp.CASDConfiguration.datasetURI(datasetYear, sosShortName);
					Resource datasetResource = pspModel.createResource(datasetURI, DCAT.Dataset);
					String label = (datasetYear.length() == 4) ? "Millésime " + datasetYear : "Millésimes " + datasetYear.replace('_', '-');
					datasetResource.addProperty(DCTerms.title, pspModel.createLiteral(label, "fr"));
					label = (datasetYear.length() == 4) ? "Year " + datasetYear : "Years " + datasetYear.replace('_', '-');
					datasetResource.addProperty(DCTerms.title, pspModel.createLiteral(label, "en"));
					datasetResource.addProperty(wasGeneratedBy, sosResource);
					// Copy the dcat:Dataset resource and its properties to the DCAT model and add to the Catalog
					dcatModel.add(pspModel.listStatements(datasetResource, (Property) null, (RDFNode) null)); // Casts to avoid ambiguity error message
					pspCatalog.addProperty(DCAT.dataset, dcatModel.getResource(datasetURI));
				}
			}

			try {
				pspModel.write(new FileWriter(PSPModelMaker.PSP_TURTLE_FILE), "TTL");
				dcatModel.write(new FileWriter(PSPModelMaker.DCAT_TURTLE_FILE), "TTL");
			} catch (IOException e) {
				logger.fatal("");
			}
			try { pdpWorkbook.close(); } catch (Exception ignored) { }
		}
	}


	/**
	 * Breaks down a global year specifications into atomic year specifications.
	 * Atomic specifications are: a year (ex: '2017') or a span of two years (underscore separator, ex: '2016_2017').
	 * Atomic specifications are grouped in a global specification as follows:
	 * . / separator indicates a combination (ex: 2016/2017 is 2016 and 2017)
	 * . -> separator indicates a range (ex: '2014 -> 2017' is 2014/2015/2016/2017)
	 * 
	 * @param yearsAsString The global specification as a string.
	 * @return A list of strings containing the atomic specifications (the order is preserved), or <code>null</code> in case of problem.
	 */
	public static List<String> getYears(String yearsAsString) {

		List<String> years = new ArrayList<String>();

		String[] tokens = yearsAsString.split("/");

		for (String token : tokens) {
			String trimmedToken = token.trim();
			if (trimmedToken.length() < 4) return null; // Years should have 4 digits
			if (trimmedToken.length() == 4) {
				if (trimmedToken.matches("^\\d{4}$")) years.add(trimmedToken); // Simple case of a single year specification
				else return null;
			}
			else {
				// Case of a two-year span
				if (trimmedToken.matches("^\\d{4}_\\d{4}$")) {
					years.add(trimmedToken);
					continue;
				}
				// The token should contain the '->' sequence, separating the start and end years
				String[] startEnd = trimmedToken.split("->");
				if (startEnd.length != 2) return null;
				String startString = startEnd[0].trim();
				String endString = startEnd[1].trim();
				if ((startString.length() != 4) || (startString.length() != 4)) return null; // Malformed span specification
				Year start = Year.parse(startString);
				Year end = Year.parse(endString);
				if (start.isAfter(end)) return null; // Start year should be before end year
				for (Year year = start; year.isBefore(end); year = year.plusYears(1)) {
					years.add(year.toString());
				}
				years.add(end.toString()); // Add the final year
			}
		}
		return years;
	}


	public static String EXCEL_FILE = "src/main/resources/liste_produits_par_producteurs_RDF.xlsx";

	public static String PSP_TURTLE_FILE = "src/main/resources/casd-psp.ttl";

	public static String DCAT_TURTLE_FILE = "src/main/resources/casd-dcat.ttl";

	public static String INSEE_ONTO_BASE_URI = "http://rdf.insee.fr/def/base#";
}
