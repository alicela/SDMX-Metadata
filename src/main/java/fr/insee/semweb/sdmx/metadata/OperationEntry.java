package fr.insee.semweb.sdmx.metadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;

import eu.casd.semweb.psp.PSPModelMaker;

/**
 * Represents an entry in the operation spreadsheet.
 * 
 * @author Franck Cotton
 */
public class OperationEntry {

	public static Logger logger = LogManager.getLogger(OperationEntry.class);

	// It's a bit of a hack redefining the codes statically here, but that will do for now
	static Map<String, Property> typeProperties = new HashMap<String, Property>();
	static {
		typeProperties.put("enquête", ResourceFactory.createProperty(Configuration.inseeCodeURI("S", "type operation")));
		typeProperties.put("source administrative", ResourceFactory.createProperty(Configuration.inseeCodeURI("A", "type operation")));
		typeProperties.put("synthèse", ResourceFactory.createProperty(Configuration.inseeCodeURI("C", "type operation")));
		typeProperties.put("indicateurs", ResourceFactory.createProperty(Configuration.inseeCodeURI("I", "type operation")));
		typeProperties.put("panel", ResourceFactory.createProperty(Configuration.inseeCodeURI("P", "type operation")));
		typeProperties.put("modélisation", ResourceFactory.createProperty(Configuration.inseeCodeURI("M", "type operation")));
	}
	static Map<String, Property> periodicityProperties = new HashMap<String, Property>();
	static {
		periodicityProperties.put("annuelle", ResourceFactory.createProperty(Configuration.inseeCodeURI("A", "periodicite")));
		periodicityProperties.put("apériodique", ResourceFactory.createProperty(Configuration.inseeCodeURI("P", "periodicite")));
		periodicityProperties.put("ponctuelle", ResourceFactory.createProperty(Configuration.inseeCodeURI("P", "periodicite"))); // Punctual merged with aperiodic
		periodicityProperties.put("en continu", ResourceFactory.createProperty(Configuration.inseeCodeURI("C", "periodicite"))); // Added
		periodicityProperties.put("mensuelle", ResourceFactory.createProperty(Configuration.inseeCodeURI("M", "periodicite")));
		periodicityProperties.put("trimestrielle", ResourceFactory.createProperty(Configuration.inseeCodeURI("Q", "periodicite")));
		periodicityProperties.put("semestrielle", ResourceFactory.createProperty(Configuration.inseeCodeURI("S", "periodicite")));
		periodicityProperties.put("pluriannuelle", ResourceFactory.createProperty(Configuration.inseeCodeURI("U", "periodicite"))); // Added
	}
	static int LATEST_CASD_YEAR = 2016;

	private String familyName;
	private String seriesName;
	private String shortName;
	private String ddsIdentifier;
	private String operationType;
	private String operationInfo;
	private String periodicity;
	private String casdAvailability;
	private String casdProducts;

	/** Returns true if line is empty (line considered empty if family name, series name and operation info are empty) */
	public boolean isEmpty() {
		return ((familyName == null) && (seriesName == null) && (operationInfo == null));
	}

	/** Returns true if line is only represents an operation (operationInfo and optionally ddsIdentifier) */
	public boolean isOnlyOperationInfo() {
		boolean result = ((familyName == null) && (seriesName == null) && (shortName == null));
		result &= ((operationType == null) && (periodicity == null) && (casdAvailability == null) && (casdProducts == null));
		return result && (operationInfo != null);
	}

	/** Reads an entry from a spreadsheet row */
	public static OperationEntry readFromRow(Row row) {

		OperationEntry entry = new OperationEntry();

		// Family name is in column A
		String cellValue = row.getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		if (cellValue.length() > 0) entry.setFamilyName(row.getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim());

		// Series name is in column B
		cellValue = row.getCell(1, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		if (cellValue.length() > 0) entry.setSeriesName(cellValue);

		// Short name is in column C
		cellValue = row.getCell(2, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		if (cellValue.length() > 0) entry.setShortName(cellValue);

		// DDS identifier is in column D
		cellValue = row.getCell(3, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		if (cellValue.length() > 0) entry.setDdsIdentifier(cellValue);

		// Operation type is in column E
		cellValue = row.getCell(4, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		if (cellValue.length() > 0) entry.setOperationType(cellValue);

		// Operation information is in column F (we have to deal with numeric values)
		Cell opInfoCell = row.getCell(5, MissingCellPolicy.CREATE_NULL_AS_BLANK);
		if (opInfoCell.getCellTypeEnum() == CellType.NUMERIC) { // Both getCellType() and getCellTypeEnum() are deprecated...
			Integer numericCellValue = (int)opInfoCell.getNumericCellValue();
			entry.setOperationInfo(numericCellValue.toString());
		} else {
			cellValue = opInfoCell.toString().trim();
			if (cellValue.length() > 0) entry.setOperationInfo(cellValue);
		}

		// Periodicity is in column G
		cellValue = row.getCell(6, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim().toLowerCase();
		if (cellValue.length() > 0) {
			// A bit of normalisation
			if (cellValue.startsWith("annuelle")) cellValue = "annuelle"; // Deals with 'annuelle (3 enquêtes par an)'
			if (cellValue.startsWith("trimestrielle")) cellValue = "trimestrielle"; // Deals with 'trimestrielle et annuelle'
			if (cellValue.startsWith("tous les")) cellValue = "pluriannuelle"; // Deals with 'Tous les 5 ans depuis 1994' and such
			if (cellValue.equals("pluri-annuelle") || cellValue.equals("quadriennale")) cellValue = "pluriannuelle";
			if (cellValue.equals("panel") || cellValue.equals("périodique")) cellValue = "pluriannuelle";
			if (periodicityProperties.get(cellValue) != null) entry.setPeriodicity(cellValue);
			else logger.warn("Invalid value found for periodicity on row " + (row.getRowNum() + 1) + ": " + cellValue);
		}

		// CASD availability is in column H
		cellValue = row.getCell(7, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		if (cellValue.length() > 0) entry.setCASDAvailability(cellValue);

		// CASD products is in column I
		Cell casdProductsCell = row.getCell(8, MissingCellPolicy.CREATE_NULL_AS_BLANK);
		cellValue = getStringValue(casdProductsCell);
		if (cellValue.length() > 0) {
			entry.setCASDProducts(cellValue);
			// Check that the value can be interpreted
			if (entry.getCASDProductsYears() == null) logger.warn("CASD products specification could not be interpreted on row " + (row.getRowNum() + 1) + ": " + cellValue);
		}

		return entry;
	}

	public boolean isCASDAvailable() {

		if (casdAvailability == null) return false;
		if (casdAvailability.startsWith("oui")) return true;
		if (casdAvailability.equals("DARES")) return true;
		return false;
	}

	public Property getTypeProperty() {

		if (operationType == null) return null;
		return typeProperties.get(operationType.toLowerCase()); // Will return null if not found
	}

	public Property getPeriodicityProperty() {

		if (periodicity == null) return null;
		return periodicityProperties.get(periodicity); // There was a control on entry, so that should not return null;
	}

	public List<String> getCASDProductsYears() {

		if (casdProducts == null) return null;

		return PSPModelMaker.getYears(casdProducts);
	}

	@Override
	public String toString() {

		if (this.isEmpty()) return "(empty line)";
		StringBuilder builder = new StringBuilder();
		if (familyName != null) builder.append("Family: ").append(familyName);
		if (seriesName != null) builder.append((builder.length() == 0) ? "" : ", ").append("Series: ").append(seriesName);
		if (shortName != null) builder.append((builder.length() == 0) ? "" : ", ").append("Short name: ").append(shortName);
		if (ddsIdentifier != null) builder.append((builder.length() == 0) ? "" : ", ").append("DDS identifier: ").append(ddsIdentifier);
		if (operationType != null) builder.append((builder.length() == 0) ? "" : ", ").append("Operation type: ").append(operationType);
		if (operationInfo != null) builder.append((builder.length() == 0) ? "" : ", ").append("Operation info: ").append(operationInfo);
		if (periodicity != null) builder.append((builder.length() == 0) ? "" : ", ").append("Periodicity: ").append(periodicity);
		if (casdAvailability != null) builder.append((builder.length() == 0) ? "" : ", ").append("CASD availability: ").append(casdAvailability);
		if (casdProducts != null) builder.append((builder.length() == 0) ? "" : ", ").append("CASD products: ").append(casdProducts);
		return builder.toString();
	}

	private static String getStringValue(Cell cell) {

		if (cell.getCellTypeEnum() == CellType.NUMERIC) { // Both getCellType() and getCellTypeEnum() are deprecated...
			Integer numericCellValue = (int)cell.getNumericCellValue();
			return numericCellValue.toString();
		} else return cell.toString().trim();
	}

	// Getters and setters

	public String getFamilyName() {
		return familyName;
	}

	public void setFamilyName(String familyName) {
		this.familyName = familyName;
	}

	public String getSeriesName() {
		return seriesName;
	}

	public void setSeriesName(String seriesName) {
		this.seriesName = seriesName;
	}

	public String getShortName() {
		return shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public String getDdsIdentifier() {
		return ddsIdentifier;
	}

	public void setDdsIdentifier(String ddsIdentifier) {
		this.ddsIdentifier = ddsIdentifier;
	}

	public String getOperationType() {
		return operationType;
	}

	public void setOperationType(String operationType) {
		this.operationType = operationType;
	}

	public String getOperationInfo() {
		return operationInfo;
	}

	public void setOperationInfo(String operationInfo) {
		this.operationInfo = operationInfo;
	}

	public String getPeriodicity() {
		return periodicity;
	}

	public void setPeriodicity(String periodicity) {
		this.periodicity = periodicity;
	}

	public String getCASDAvailability() {
		return casdAvailability;
	}

	public void setCASDAvailability(String casdAvailability) {
		this.casdAvailability = casdAvailability;
	}

	public String getCASDProducts() {
		return casdProducts;
	}

	public void setCASDProducts(String casdProducts) {
		this.casdProducts = casdProducts;
	}

}
