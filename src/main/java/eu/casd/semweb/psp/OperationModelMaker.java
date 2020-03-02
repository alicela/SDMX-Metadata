package eu.casd.semweb.psp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import fr.insee.semweb.sdmx.metadata.Configuration;

/**
 * Creates a Jena model corresponding to the list of operations, series and families.
 * 
 * @author Franck Cotton
 */
public class OperationModelMaker {

	public static Logger logger = LogManager.getLogger(OperationModelMaker.class);

	public static final Property ddsIdentifier = ResourceFactory.createProperty(Configuration.BASE_INSEE_ONTO_URI + "ddsIdentifier");
	public static final Property casdAvailable = ResourceFactory.createProperty(Configuration.BASE_INSEE_ONTO_URI + "casdAvailable");
	public static final Property wasGeneratedBy = ResourceFactory.createProperty("http://www.w3.org/ns/prov#wasGeneratedBy");

	// Controls if all CASD products are attached to the series, or just those that have no corresponding operation
	public static final boolean ATTACH_ALL_PRODUCTS_TO_SERIES = true;

	public static Model productModel = null;

	public static void main(String[] args) {

		// Create operation and product models and set their namespaces
		Model opeModel = ModelFactory.createDefaultModel();
		opeModel.setNsPrefix("rdfs", RDFS.getURI());
		opeModel.setNsPrefix("dcterms", DCTerms.getURI());
		opeModel.setNsPrefix("skos", SKOS.getURI());
		opeModel.setNsPrefix("insee", Configuration.BASE_INSEE_ONTO_URI);
		productModel = ModelFactory.createDefaultModel();
		productModel.setNsPrefixes(opeModel.getNsPrefixMap());
		productModel.setNsPrefix("dcat", DCAT.getURI()); // Product model will also need DCAT and PROV
		productModel.setNsPrefix("prov", "http://www.w3.org/ns/prov#");

		Workbook opeWorkbook = null;
		Sheet sheet = null;
		try {
			opeWorkbook = WorkbookFactory.create(new File(CASDConfiguration.OPERATIONS_XLSX_FILE_NAME));
			sheet = opeWorkbook.getSheetAt(0);
		} catch (Exception e) {
			logger.fatal("Error while opening Excel file - " + e.getMessage());
			System.exit(1);
		}

		Iterator<Row> rows = sheet.rowIterator();
		rows.next(); // Skip the title line
		String currentFamily = "Init value";
		List<OperationEntry> familyBlock = null;

		while (rows.hasNext()) {
			Row row = rows.next();
			OperationEntry entry = OperationEntry.readFromRow(row);
			if (entry.isEmpty()) continue;
			String familyName = entry.getFamilyName();
			// If family name is not empty and differs from current value, start new family bloc
			if ((familyName != null) && (!familyName.equalsIgnoreCase(currentFamily))) {
				if (familyBlock != null) {
					opeModel.add(getFamilyModel(familyBlock));
					logger.debug("Closing family " + currentFamily);
				}
				familyBlock = new ArrayList<OperationEntry>(); // Start new block
				logger.debug("Opening family " + familyName);
				currentFamily = familyName;
			}
			familyBlock.add(entry);
		}
		// Process last family block
		opeModel.add(getFamilyModel(familyBlock));
		logger.debug("Closing family " + currentFamily);

		try { opeWorkbook.close(); } catch (IOException ignored) { }

		try {
			opeModel.write(new FileWriter("src/main/resources/data/operations.ttl"), "TTL");
			productModel.write(new FileWriter("src/main/resources/data/products.ttl"), "TTL");
		} catch (IOException e) {
			logger.error("Error writing models to files");
		}
	}

	/**
	 * Produces a Jena model corresponding to a family, based on a corresponding bloc of entries.
	 */
	public static Model getFamilyModel(List<OperationEntry> familyChunk) {

		Model familyModel = ModelFactory.createDefaultModel();

		String familyName = familyChunk.get(0).getFamilyName(); // Can't be empty
		logger.debug("Processing family " + familyName + " (" + familyChunk.size() + " line(s))");

		// Add family resource to model
		Resource family = familyModel.createResource(Configuration.statisticalOperationFamilyURI(familyName), Configuration.STATISTICAL_OPERATION_FAMILY);
		family.addProperty(SKOS.prefLabel, familyModel.createLiteral(familyName, "fr"));
		family.addProperty(DCTerms.abstract_, familyModel.createLiteral("Description pour la famille " + familyName, "fr")); // HACK: we have no descriptions for now
		
		// Two cases where series name is empty on the first line (Logement, Patrimoine): leave aside for now
		// This should not be the case anymore
		if (familyChunk.get(0).getSeriesName() == null) {
			logger.warn("Series name is missing on first line for family " + familyName + " - no series created");
			return familyModel; // Limited to the family
		}

		String currentSeries = "Init value";
		List<OperationEntry> seriesBlock = null;
		for (OperationEntry entry : familyChunk) {
			String seriesName = entry.getSeriesName();
			if ((seriesName != null) && (!seriesName.equalsIgnoreCase(currentSeries))) {
				// New series: close current series block and attach series to family
				if (seriesBlock != null) {
					familyModel.add(getSeriesModel(seriesBlock));
					Resource series = familyModel.createResource(Configuration.statisticalOperationSeriesURI(seriesName)); // Should exist already
					family.addProperty(DCTerms.hasPart, series);
					series.addProperty(DCTerms.isPartOf, family);
					logger.debug("Closing series " + currentSeries);
				}
				seriesBlock = new ArrayList<OperationEntry>(); // Start new series block
				logger.debug("Opening series " + seriesName);
				currentSeries = seriesName;
			}
			seriesBlock.add(entry); // Running series: just add the current entry to the series block
			if ((seriesName == null) && (!entry.isOnlyOperationInfo())) {
				System.out.println("Invalid line\n    " + entry);
			}
		}
		// Process last series block
		familyModel.add(getSeriesModel(seriesBlock));
		Resource series = familyModel.createResource(fr.insee.semweb.sdmx.metadata.Configuration.statisticalOperationSeriesURI(currentSeries)); // Should exist already
		family.addProperty(DCTerms.hasPart, series);
		series.addProperty(DCTerms.isPartOf, family);
		logger.debug("Closing series " + currentSeries);

		return familyModel;
	}

	/**
	 * Produces a Jena model corresponding to a series, based on a corresponding bloc of entries.
	 */
	public static Model getSeriesModel(List<OperationEntry> seriesChunk) {

		Model seriesModel = ModelFactory.createDefaultModel();

		OperationEntry seriesEntry = seriesChunk.get(0);
		String seriesName = seriesEntry.getSeriesName(); // Can't be empty
		logger.debug("Processing series " + seriesName + " (" + seriesChunk.size() + " line(s))");

		// Add series to model
		Resource series = seriesModel.createResource(fr.insee.semweb.sdmx.metadata.Configuration.statisticalOperationSeriesURI(seriesName), fr.insee.semweb.sdmx.metadata.Configuration.STATISTICAL_OPERATION_SERIES);
		series.addProperty(SKOS.prefLabel, seriesModel.createLiteral(seriesName, "fr"));
		series.addProperty(DCTerms.abstract_, seriesModel.createLiteral("Description pour la serie " + seriesName, "fr")); // HACK: we have no descriptions for now
		if (seriesEntry.getShortName() != null) series.addProperty(SKOS.altLabel, seriesEntry.getShortName());
		if (seriesEntry.getDdsIdentifier() != null) series.addProperty(ddsIdentifier, seriesEntry.getDdsIdentifier());
		series.addProperty(casdAvailable, seriesModel.createTypedLiteral(seriesEntry.isCASDAvailable()));
		// Add operation type with property DCTerms.type
		Property operationType = seriesEntry.getTypeProperty();
		if (operationType == null) logger.warn("Missing or unknown operation type: " + seriesEntry.getOperationType());
		else series.addProperty(DCTerms.type, operationType);
		// Add periodicity with property DCTerms.accrualPeriodicity
		Property periodicityType = seriesEntry.getPeriodicityProperty();
		if (periodicityType == null) logger.warn("Missing or unknown periodicity: " + seriesEntry.getPeriodicity());
		else series.addProperty(DCTerms.accrualPeriodicity, periodicityType);

		if (seriesChunk.size() == 1) {
			// There are no operations defined for that series: if CASD products exist, they are attached to the series
			if (seriesEntry.getCASDProductsYears() != null) {
				for (String casdYear : seriesEntry.getCASDProductsYears()) {
					String datasetURI = eu.casd.semweb.psp.CASDConfiguration.datasetURI(casdYear, seriesName);
					Resource datasetResource = productModel.createResource(datasetURI, DCAT.Dataset);
					datasetResource.addProperty(DCTerms.title, productModel.createLiteral(seriesName + " - Fichier " + casdYear, "fr"));
					datasetResource.addProperty(wasGeneratedBy, series);
				}	
			}
		} else {
			// Make a non-null copy of the CASD product list that we can modify
			List<String> casdProducts = (seriesEntry.getCASDProductsYears() != null) ? new ArrayList<String>(seriesEntry.getCASDProductsYears()) : new ArrayList<String>();
			for (OperationEntry entry : seriesChunk) {
				if (entry.getSeriesName() == null) { // Checked: if series name is empty, then we have an operation line (isOnlyOperationInfo is true)
					String operationName = seriesName + " " + entry.getOperationInfo();
					Resource operation = seriesModel.createResource(fr.insee.semweb.sdmx.metadata.Configuration.statisticalOperationURI(operationName), fr.insee.semweb.sdmx.metadata.Configuration.STATISTICAL_OPERATION);
					operation.addProperty(SKOS.prefLabel, seriesModel.createLiteral(operationName, "fr"));
					operation.addProperty(DCTerms.abstract_, seriesModel.createLiteral("Description pour l'opération " + operationName, "fr")); // HACK: we have no descriptions for now
					if (entry.getDdsIdentifier() != null) operation.addProperty(ddsIdentifier, entry.getDdsIdentifier()); // Rare, but happens
					series.addProperty(DCTerms.hasPart, operation);
					operation.addProperty(DCTerms.isPartOf, series);
					// if operation has a corresponding CASD product, create the product and link it to the operation, and to the series if requested
					if (casdProducts.contains(entry.getOperationInfo())) {
						String datasetURI = eu.casd.semweb.psp.CASDConfiguration.datasetURI(operationName, seriesName);
						Resource datasetResource = productModel.createResource(datasetURI, DCAT.Dataset);
						datasetResource.addProperty(DCTerms.title, productModel.createLiteral(seriesName + " - Fichier " + operationName, "fr"));
						datasetResource.addProperty(wasGeneratedBy, operation);
						if (ATTACH_ALL_PRODUCTS_TO_SERIES) datasetResource.addProperty(wasGeneratedBy, series);
						casdProducts.remove(entry.getOperationInfo());
					}
				}
			}
			// Create remaining CASD products and attach them to the series
			for (String casdYear : casdProducts) {
				String datasetURI = eu.casd.semweb.psp.CASDConfiguration.datasetURI(casdYear, seriesName);
				Resource datasetResource = productModel.createResource(datasetURI, DCAT.Dataset);
				datasetResource.addProperty(DCTerms.title, productModel.createLiteral(seriesName + " - Fichier " + casdYear, "fr"));
				datasetResource.addProperty(wasGeneratedBy, series);
			}	
		}
		return seriesModel;
	}
}
