package fr.insee.semweb.sdmx.metadata;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;

/**
 * Represents an entry in the SIMS structure.
 * 
 * @author Franck Cotton
 */
public class SIMSEntry {

	protected String notation;
	protected String code;
	protected String name;
	protected String description;
	protected String representation;
	protected String guidelines;
	protected String indicators;
	protected EntryType type = EntryType.UNKNOWN;

	/**
	 * Creates a <code>SIMSEntry</code> from a spreadsheet row.
	 * 
	 * @param row A SIMS spreadsheet row as a <code>Row</code> object.
	 * @param fromFr Indicates if the row is in SIMSFr format or just SIMS format.
	 * @return A <code>SIMSEntry</code> with values read in the row.
	 */
	public static SIMSEntry readFromRow(Row row, boolean fromFr) {

		int[] indexes = (fromFr ? Configuration.SIMS_COLUMNS_SIMS_FR : Configuration.SIMS_COLUMNS_SIMS);

		String notationAndName = row.getCell(indexes[0], MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		if (notationAndName.length() == 0) return null; // Avoids a trailing empty line in the "Plus" format
		String notation = notationAndName.split(" ")[0];
		SIMSEntry entry = (fromFr ? new SIMSFrEntry(notation) : new SIMSEntry(notation));
		// Concepts with only one '.' in their notation code are DQV categories
		entry.setType((notation.split("\\.").length == 2) ? EntryType.CATEGORY : EntryType.DIMENSION);

		// Concept name is in column B (SIMSFr: F), never empty
		String cellValue = row.getCell(indexes[1], MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		entry.setName(cellValue);

		// Concept code is in column C (SIMSFr: E), never empty
		cellValue = row.getCell(indexes[2], MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		entry.setCode(cellValue);

		// Description is in column D (SIMSFr: G), can be empty (only case S.3)
		cellValue = row.getCell(indexes[3], MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		if (cellValue.length() > 0) entry.setDescription(cellValue);

		// Representation is in column E (SIMSFr: J), can be empty
		cellValue = row.getCell(indexes[4], MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		if (cellValue.length() > 0) entry.setRepresentation(cellValue);

		// The next attributes are only available in the base format
		if (!fromFr) {
			cellValue = row.getCell(5, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			if (cellValue.length() > 0) entry.setGuidelines(cellValue); // Guidelines are in column F, can be empty
			cellValue = row.getCell(6, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			if (cellValue.length() > 0) entry.setIndicators(cellValue); // Quality indicators are in column G, often empty
		}

		return entry;
	}

	/**
	 * Returns the notation of the parent concept or <code>null</code> for top level concepts.
	 * 
	 * It is assumed that the SIMS notations are composite codes with dot separators.
	 * 
	 * @param notation The notation of the concept whose parent is sought.
	 * @return The notation of the parent concept or <code>null</code> for top level concepts.
	 */
	public static String getParentNotation(String notation) {

		if (notation ==  null) return null;
		int lastDot = notation.lastIndexOf('.');
		if (lastDot >= 0) return notation.substring(0, lastDot);

		return null;
	}

	/**
	 * Returns the notation of the highest ancestor of a concept.
	 * 
	 * The highest ancestor is a DQV Category instance.
	 * 
	 * @param notation The notation of the concept whose category is sought.
	 * @return The notation of the category (highest ancestor) for the concept.
	 */
	public static String getCategoryNotation(String notation) {

		if (notation ==  null) return null;
		String[] tokens = notation.split("\\.");
		if (tokens.length > 1) return tokens[0] + "." + tokens[1];
		return null;
	}

	public SIMSEntry(String notation) {
		this.notation = notation;
	}

	@Override
	public boolean equals(Object compareObject) {

		if (!(compareObject instanceof SIMSEntry)) return false;
		if (compareObject == this) return true;

		SIMSEntry compareEntry = (SIMSEntry) compareObject;
		EqualsBuilder builder = new EqualsBuilder();
		builder.append(notation, compareEntry.notation);
		builder.append(code, compareEntry.code);
		builder.append(name, compareEntry.name);
		builder.append(description, compareEntry.description);
		builder.append(representation, compareEntry.representation);
		builder.append(guidelines, compareEntry.guidelines);
		builder.append(indicators, compareEntry.indicators);
		builder.append(type, compareEntry.type);
		return builder.isEquals();
	}

	@Override
	public int hashCode() {

		HashCodeBuilder builder = new HashCodeBuilder(53, 11);
		builder.append(notation).append(code).append(name).append(description).append(representation);
		builder.append(guidelines).append(indicators).append(type);
		return builder.hashCode();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Notation: ").append(notation).append(", ");
		builder.append("Code: ").append(code).append(", ");
		builder.append("Name: ").append(name).append(", ");
		if (representation != null) builder.append("Representation: ").append(representation).append(", ");
		builder.append("Type: ").append(type);
		return builder.toString(); // Long string members are omitted
	}

	/**
	 * Returns the notation of the parent concept or <code>null</code> for top level concepts.
	 * 
	 * @return The notation of the parent concept or <code>null</code> for top level concepts.
	 */
	public String getParentNotation() {

		return getParentNotation(this.notation);
	}

	// Getters and setters

	public String getNotation() {
		return notation;
	}

	public void setNotation(String notation) {
		this.notation = notation;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public EntryType getType() {
		return type;
	}

	public void setType(EntryType type) {
		this.type = type;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getRepresentation() {
		return representation;
	}

	public void setRepresentation(String representation) {
		this.representation = representation;
	}

	public String getIndicators() {
		return indicators;
	}

	public void setIndicators(String indicators) {
		this.indicators = indicators;
	}

	public String getGuidelines() {
		return guidelines;
	}

	public void setGuidelines(String guidelines) {
		this.guidelines = guidelines;
	}

	public enum EntryType {
		CATEGORY,
		DIMENSION,
		UNKNOWN;

		@Override
		public String toString() {
			switch(this) {
				case CATEGORY: return "category";
				case DIMENSION: return "dimension";
				default: return "unknown";
			}
		}
	}
}

