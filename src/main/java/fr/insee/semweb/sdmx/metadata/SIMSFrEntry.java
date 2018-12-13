package fr.insee.semweb.sdmx.metadata;

import java.util.Map;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.ORG;
import org.apache.jena.vocabulary.XSD;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;

import fr.insee.stamina.utils.DQV;

/**
 * Represents an entry in the SIMSFr structure.
 * 
 * @author Franck Cotton
 */
public class SIMSFrEntry extends SIMSEntry {

	private static Logger logger = LogManager.getLogger(SIMSFrEntry.class);

	// SIMSFrEntry components (in addition to the SIMSv2 base ones)
	private String dissemination;
	private String metric; // Unfortunate naming here, this is more the type of the metric (indicator)
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

		// Dissemination is in column A, should not be empty
		String cellValue = row.getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
		entry.setDissemination(cellValue);

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
		builder.append("Dissemination: ").append(dissemination).append(", ");
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
		// There is no case of presentational attribute in SIMS which is given a Insee representation
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

	/**
	 * Determines the range of the metadata attribute property corresponding to the entry, based on the information on the representation of its values.
	 * 
	 * @param simsStrict A boolean indicating if the context is restricted to the SIMS or extended to SIMSFr.
	 * @param clMappings The mappings between the code list notations and the associated concepts (can be null for strict mode).
	 * @return The range of the property represented as a (non-null) Jena <code>Resource</code>.
	 */
	public Resource getRange(boolean simsStrict, Map<String, Resource> clMappings) {

		Resource range = null;

		logger.debug("Calculating range for entry " + this.getNotation());

		// For presentational properties, so the metadata attribute property will have ReportedAttribute for range (valid both strict and not strict)
		if (this.isPresentational()) return ResourceFactory.createResource(Configuration.SDMX_MM_BASE_URI + "ReportedAttribute");

		// In the strict SIMS model, the only possible (non-null) types are Text (with variants Telephone, etc.), Date, Quality Indicator and code list (CL_...)
		String type = this.getRepresentation();
		if (type != null) { // type can be null for attributes added in SIMSFr
			type = type.trim().toLowerCase();
			if (type.equals("date")) range = XSD.date;
			else if (type.startsWith("quality")) range = DQV.Metric; // But we don't create attribute properties for quality indicators
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

		// In SIMSFr mode, we have to look at the Insee representation if it exists, but this field is not well normalized (see SIMSFrSchemeTest test class for listing the values)
		if (this.getInseeRepresentation() == null) return range; // No Insee representation: we stick with the range previously calculated
		type = this.getInseeRepresentation().trim().toLowerCase();

		// All that starts with 'text' or 'expression' (for 'expression régulière') is treated as string datatype property
		if (type.startsWith("text") || type.startsWith("expression")) return XSD.xstring;
		// 'Rich text' alone (without reference) is also string for now (could be HTLM text)
		if (type.equals("rich text")) return XSD.xstring;
		// The other cases of 'rich text' ('Rich text + other material...') are associated with references: the corresponding type is defined in the configuration class
		if (type.startsWith("rich text")) return Configuration.RICH_TEXT_MAP_RANGE;
		// If representation starts with 'code list', either the list is indicated with CL_*, or it is the list of SSMs
		// FIXME There are also cases where the representation contains only the code list name (CL_FREQ_FR, CL_COLLECTION_MODE)
		else if ((type.startsWith("code list")) || (type.startsWith("cl_"))) {
			int index = type.indexOf("cl_");
			if (index >= 0) {
				// Extract the name of the code list (can be followed by space or new line)
				int firstSpace = type.indexOf("\n", index);
				if (firstSpace == -1) firstSpace = type.indexOf(" ", index);
				String clNotation = (firstSpace < 0) ? type.substring(index) : type.substring(index, firstSpace);
				clNotation = clNotation.toUpperCase();
				// FIXME Some code lists do not have the correct notation in the SIMS spreadsheet
				if (clNotation.equals("CL_FREQ_FR")) clNotation = "CL_FREQ";
				if (clNotation.equals("CL_STATUS")) clNotation = "CL_SURVEY_STATUS";
				if (!clMappings.containsKey(clNotation)) {
					logger.error("No mapping concept found for code list " + clNotation);
					return null;
				} else return clMappings.get(clNotation);
			} else {
				// Code list is in fact a list of organizations, so the property range will be org:Organization
				return ORG.Organization;
			}
		}
		// All cases should be covered at this point
		return null;
	}

	// Getters and setters
	public String getDissemination() {
		return dissemination;
	}

	public void setDissemination(String dissemination) {
		this.dissemination = dissemination;
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
