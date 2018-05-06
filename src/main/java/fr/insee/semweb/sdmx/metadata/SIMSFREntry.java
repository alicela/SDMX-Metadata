package fr.insee.semweb.sdmx.metadata;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;

/**
 * Represents an entry in the SIMSFr structure.
 * 
 * @author Franck Cotton
 */
public class SIMSFrEntry extends SIMSEntry {

	private String title; // TODO This variable should be named otherwise
	private String metric; // Unfortunate naming here also, this is more the type of the metric (indicator)
	private String frenchName;
	private String frenchDescription;
	private String origin;
	private String inseeRepresentation;

	public SIMSFrEntry(String notation) {
		super(notation);
	}

	/**
	 * Reads a SIMSFr entry from a row in the base Excel spreadsheet.
	 * 
	 * @param row The row (POI user model object) storing the characteristics of the entry.
	 * @return The SIMSFr entry read.
	 */
	public static SIMSFrEntry readFromRow(Row row) {

		SIMSEntry baseEntry = SIMSEntry.readFromRow(row, true);
		if (baseEntry == null) return null;

		SIMSFrEntry entry = (SIMSFrEntry) baseEntry;

		// Title is in column A, should not be empty
		String cellValue = row.getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		entry.setTitle(cellValue);

		// Metric is in column D, often empty
		cellValue = row.getCell(3, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim().replace("\n", " ");
		if (cellValue.length() > 0) entry.setMetric(cellValue);

		// French concept name is in column H, never empty
		cellValue = row.getCell(7, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		entry.setFrenchName(cellValue);

		// French description is in column I, can be empty (only S.3)
		cellValue = row.getCell(8, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		if (cellValue.length() > 0) entry.setFrenchDescription(cellValue);

		// Origin is in column K, often empty (only present for French additions)
		cellValue = row.getCell(10, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		if (cellValue.length() > 0) entry.setOrigin(cellValue);

		// Insee representation is in column L, can be empty
		cellValue = row.getCell(11, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		if (cellValue.length() > 0) entry.setInseeRepresentation(cellValue);

		return entry;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(super.toString()).append("\n");
		builder.append("Title: ").append(title).append(", ");
		builder.append("French name: ").append(frenchName);
		if (inseeRepresentation != null) builder.append(", ").append("Insee representation: ").append(inseeRepresentation);
		return builder.toString(); // Long string members are omitted
	}

	/**
	 * Indicates if the entry corresponds to a direct property (directly attached to the target) or is attached through a SIMSFr report.
	 * 
	 * @return <code>true</code> if the entry corresponds to a direct property, <code>false</code> otherwise.
	 */
	public boolean isDirect() {

		return ((this.getNotation().equals("I.1") || this.getNotation().startsWith("I.1.")) || this.getNotation().startsWith("C.1."));
	}

	/**
	 * Indicates if the entry belongs to the original SIMS v2.
	 * 
	 * @return <code>true</code> if the entry belongs to the original SIMS v2, <code>false</code> otherwise.
	 */
	public boolean isOriginal() {

		// The original entries have no "Origin" property, curiously
		return (this.origin == null);
	}

	/**
	 * Indicates if the entry is added in SIMSFr or if its representation is modified (compared to the original SIMS v2).
	 * 
	 * @return <code>true</code> if the entry belongs to the original SIMS v2, <code>false</code> otherwise.
	 */
	public boolean isAddedOrModified() {

		if (this.isOriginal() == false) return true;
		// Original properties are retyped if there is an Insee representation, unless it is 'Rich text' (which is the same as text)
		if (this.inseeRepresentation == null) return false;
		if (this.inseeRepresentation.toLowerCase().trim().equals("rich text")) return false;
		return true;
	}

	/**
	 * Indicates if the entry is just a presentational concept (no value by itself).
	 * 
	 * @return <code>true</code> if the entry is presentational, <code>false</code> otherwise.
	 */
	public boolean isPresentational() {

		// Presentational original attributes have no representation, and no Insee representation for added attributes
		// There is no case of presentational attribute in SIMS given which is given a Insee representation
		return (this.isOriginal() ? (this.representation == null) : (this.inseeRepresentation == null));
	}

	/**
	 * Indicates if the entry is a quality metric as defined in DQV.
	 * 
	 * @return <code>true</code> if the entry is a metric, <code>false</code> otherwise.
	 */
	public boolean isQualityMetric() {

		// What is typed 'Quality indicator' in the SIMSFr is what is actually called Metric in DQV
		return (this.type == EntryType.METRIC);
	}

	// Getters and setters
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getMetric() {
		return metric;
	}

	public void setMetric(String metric) {
		this.metric = metric;
	}

	public String getFrenchName() {
		return frenchName;
	}

	public void setFrenchName(String frenchName) {
		this.frenchName = frenchName;
	}

	public String getFrenchDescription() {
		return frenchDescription;
	}

	public void setFrenchDescription(String frenchDescription) {
		this.frenchDescription = frenchDescription;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getInseeRepresentation() {
		return inseeRepresentation;
	}

	public void setInseeRepresentation(String inseeRepresentation) {
		this.inseeRepresentation = inseeRepresentation;
	}
}
