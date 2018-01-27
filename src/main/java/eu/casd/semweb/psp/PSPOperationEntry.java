package eu.casd.semweb.psp;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import fr.insee.semweb.sdmx.metadata.Configuration;

/**
 * Gathers useful characteristic of a statistical operation.
 * 
 * @author Franck Cotton
 */
public class PSPOperationEntry {

	protected String code;
	protected String parentCode;
	protected String casdIndicator;
	protected OperationType type = OperationType.UNKNOWN;


	public PSPOperationEntry(String code) {
		this.code = code;
	}

	@Override
	public boolean equals(Object compareObject) {

		if (!(compareObject instanceof PSPOperationEntry)) return false;
		if (compareObject == this) return true;

		PSPOperationEntry compareEntry = (PSPOperationEntry) compareObject;
		EqualsBuilder builder = new EqualsBuilder();
		builder.append(code, compareEntry.code);
		builder.append(parentCode, compareEntry.parentCode);
		builder.append(casdIndicator, compareEntry.casdIndicator);
		builder.append(type, compareEntry.type);
		return builder.isEquals();
	}

	@Override
	public int hashCode() {

		HashCodeBuilder builder = new HashCodeBuilder(53, 11);
		builder.append(code).append(parentCode).append(casdIndicator).append(type);
		return builder.hashCode();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Code: ").append(code).append(", ");
		if (parentCode != null) builder.append("Parent code: ").append(parentCode).append(", ");
		builder.append("Type: ").append(type);
		return builder.toString(); // Long string members are omitted
	}

	// Getters and setters

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getParentCode() {
		return parentCode;
	}

	public void setParentCode(String parentCode) {
		this.parentCode = parentCode;
	}

	public String getCasdIndicator() {
		return casdIndicator;
	}

	public void setCasdIndicator(String casdIndicator) {
		this.casdIndicator = casdIndicator;
	}

	public OperationType getType() {
		return type;
	}

	public void setType(OperationType type) {
		this.type = type;
	}

	// Additional type setter
	public void setType(String type) {

		if (type != null) {
			String normalizedString = type.trim().toUpperCase();
			if (normalizedString.equals("OP")) this.type = OperationType.OPERATION;
			else if (normalizedString.equals("SOS")) this.type = OperationType.SERIES;
			else if (normalizedString.equals("FOS")) this.type = OperationType.FAMILY;
			else this.type = OperationType.UNKNOWN;
		}
		else this.type = OperationType.UNKNOWN;
	}

	public enum OperationType {
		OPERATION,
		SERIES,
		FAMILY,
		UNKNOWN;

		@Override
		public String toString() {
			switch(this) {
				case OPERATION: return "operation";
				case SERIES: return "operation series";
				case FAMILY: return "operation family";
				default: return "unknown";
			}
		}

		/**
		 * Returns the URI of the ontology class corresponding to the type of operation.
		 * 
		 * @return The URI of the ontology class (e.g. http://rdf.insee.fr/def/base/StatisticalOperation).
		 */
		public String operationClassURI() {
			switch(this) {
				case OPERATION: return Configuration.BASE_INSEE_ONTO_URI + "StatisticalOperation";
				case SERIES: return Configuration.BASE_INSEE_ONTO_URI + "StatisticalOperationSeries";
				case FAMILY: return Configuration.BASE_INSEE_ONTO_URI + "StatisticalOperationFamily";
				default: return null;
			}
		}

		/**
		* Returns the URI path element corresponding to the type of operation.
		 * 
		 * @return The URI path element corresponding to the type of operation (e.g. 'operation', 'serie').
		 */
		public String operationURIPathElement() {
			switch(this) {
				case OPERATION: return "operation";
				case SERIES: return "serie";
				case FAMILY: return "famille";
				default: return "";
			}
		}

	}

}
