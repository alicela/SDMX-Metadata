package fr.insee.semweb.sdmx.metadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.DCTypes;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;

import eu.casd.semweb.psp.PSPOperationEntry;

public class Configuration {

	// Input files
	/** Excel file containing the SIMS/SIMSFr models */
	public static String SIMS_XLSX_FILE_NAME = "src/main/resources/data/SIMSFR_V20181210.xlsx";
	/** Excel file containing the code lists */
	public static String CL_XLSX_FILE_NAME = "src/main/resources/data/CODE_LISTS_20180110.xlsx";
	/** Excel file containing the themes code list */
	public static String THEMES_XLSX_FILE_NAME = "src/main/resources/data/Themes.xlsx";
	/** Turtle file containing the SDMX metadata model vocabulary */
	public static String SDMX_MM_TURTLE_FILE_NAME = "src/main/resources/data/sdmx-metadata.ttl";
	/** TriG file containing the "M0" (temporary model) RDF dataset */
	public static String M0_FILE_NAME = "src/main/resources/data/sauvegardeGSM_20181023.trig";
	/** Excel file containing the information on operations */
	public static String OPERATIONS_XLSX_FILE_NAME = "src/main/resources/data/Liste sources_20170612_CASD.xlsx";
	/** Excel file containing the information on organizations */
	public static String ORGANIZATIONS_XLSX_FILE_NAME = "src/main/resources/data/OrganisationScheme_20170719.xlsx";
	/** Excel file containing the links between families and themes */
	public static String FAMILY_THEMES_XLSX_FILE_NAME = "src/main/resources/data/themes-familles.xlsx";

	// Output files
	/** Concepts and concept schemes associated to the SIMS attributes */
	public static String SIMS_CS_TURTLE_FILE_NAME = "src/main/resources/data/sims-cs.ttl";
	/** Concepts and concept schemes associated to the SIMSFr attributes */
	public static String SIMS_FR_CS_TURTLE_FILE_NAME = "src/main/resources/data/sims-fr-cs.ttl";
	/** SIMS metadata structure definition and related resources */
	public static String SIMS_MSD_TURTLE_FILE_NAME = "src/main/resources/data/sims-msd.ttl";
	/** SIMS-FR metadata structure definition and related resources */
	public static String SIMS_FR_MSD_TURTLE_FILE_NAME = "src/main/resources/data/sims-fr-msd.ttl";
	/** SIMSFr code lists */
	public static String SIMS_CL_TURTLE_FILE_NAME = "src/main/resources/data/sims-cl.ttl";
	/** Concepts and concept schemes for the categorization of the operations and products */
	public static String THEMES_TURTLE_FILE_NAME = "src/main/resources/data/themes.ttl";

	// Constants for naming
	/** Base URI for Insee's base ontology */
	public static String BASE_INSEE_ONTO_URI = "http://rdf.insee.fr/def/base#";
	/** Base URI for SIMSv2 resources */ // TODO Officialize SIMS namespace
	public static String BASE_SIMS_URI = "http://ec.europa.eu/eurostat/simsv2/";
	/** Base URI for SIMSv2FR resources */
	public static String BASE_SIMS_FR_URI = "http://id.insee.fr/qualite/simsv2fr/";
	/** Base URI for SIMS metadata reports */
	protected static String REPORT_BASE_URI = "http://id.insee.fr/qualite/rapport/";
	/** Base URI for SIMS quality metrics */
	protected static String METRIC_BASE_URI = BASE_SIMS_URI + "metric/";
	/** Prefix for the SDMX metadata model vocabulary */
	public static String SDMX_MM_PREFIX = "sdmx-mm";
	/** Base URI for the SDMX metadata model vocabulary */
	public static String SDMX_MM_BASE_URI = "http://www.w3.org/ns/sdmx-mm#";
	/** Base URI for the SDMX code lists */
	public static String SDMX_CODE_BASE_URI = "http://purl.org/linked-data/sdmx/2009/code#";
	/** Base URI for CASD products */
	public static String CASD_PRODUCTS_BASE_URI = "http://id.casd.eu/produits/";
	/** Base URI for Insee operations (and families, series) */
	public static String INSEE_OPS_BASE_URI = "http://id.insee.fr/operations/";
	/** Base URI for Insee codes */
	public static String INSEE_CODES_BASE_URI = "http://id.insee.fr/codes/";
	/** Base URI for Insee code concepts */
	public static String INSEE_CODE_CONCEPTS_BASE_URI = INSEE_CODES_BASE_URI + "concept/";
	/** Base URI for organizations */
	public static String INSEE_ORG_BASE_URI = "http://id.insee.fr/organisations/";
	// Numbers of the columns where the SIMS information is stored in SIMS and SIMSFr Excel formats
	// Notation and name, Concept name, Concept code, Description, Representation, ESS guidelines, Quality indicators
	public static int[] SIMS_COLUMNS_SIMS = {0, 1, 2, 3, 4, 5, 6};
	public static int[] SIMS_COLUMNS_SIMS_FR = {1, 5, 4, 6, 9, -1, -1};

	/** The range of metadata attribute properties corresponding to 'rich text' attributes */
	public static Resource RICH_TEXT_MAP_RANGE = DCTypes.Text;

	/** Static mapping between direct attributes of operations and RDF Properties */
	public static Map<String, Property> propertyMappings;
	static {
		propertyMappings = new HashMap<String, Property>();
		propertyMappings.put("TITLE", SKOS.prefLabel);
		propertyMappings.put("ALT_LABEL", SKOS.altLabel);
		propertyMappings.put("SOURCE_CATEGORY", DCTerms.type);
		propertyMappings.put("SUMMARY", DCTerms.abstract_);
		propertyMappings.put("HISTORY", SKOS.historyNote);
		propertyMappings.put("FREQ_COLL", DCTerms.accrualPeriodicity);
		propertyMappings.put("ORGANISATION", DCTerms.creator);
		propertyMappings.put("STAKEHOLDERS", DCTerms.contributor);
		propertyMappings.put("DATA_COLLECTOR", DCTerms.contributor); // Using dcterms:contributor for now, should be a sub-property
		propertyMappings.put("REPLACES", DCTerms.replaces);
		propertyMappings.put("RELATED_TO", RDFS.seeAlso);
	}
	/** Those of the direct attributes of operations that have string values (and can have an English version) */
	public static List<String> stringProperties = Arrays.asList("TITLE", "ALT_LABEL", "SUMMARY", "HISTORY");

	/** Correspondence between DDS identifiers and Web4G identifiers (for series) */
	public static Map<String, String> dds2Web4GIdMappings = null;
	static {
		try (Stream<String> stream = Files.lines(Paths.get("src/main/resources/data/idSources.csv"))) {
			dds2Web4GIdMappings = stream.filter(line -> line.startsWith("FR-")).map(line -> line.substring(3)).collect(Collectors.toMap(line -> line.split(",")[0], line -> line.split(",")[1]));
			// HACK Delete three lines to correct errors
			dds2Web4GIdMappings.remove("ENQUETE-PATRIMOINE"); // Series 125, web4G id 1282 is instead attributed to the 2014 survey (operation 158)
			dds2Web4GIdMappings.remove("ENQ-SDF"); // Series 85, web4G id 1267 is instead attributed to the 2001 survey (operation 189)
			dds2Web4GIdMappings.remove("ENQ-TRAJECTOIRES-2008-TEO"); // Series 118, web4G id 1276 is instead attributed to the survey (operation 199)
		} catch (IOException ignored) {
			// Do nothing, we will have an exception when trying to use the mapping
		}
	}

	/** Correspondence between M0 identifiers and Web4G identifiers (for operations) */
	public static Map<Integer, String> m02Web4GIdMappings = null;
	static {
		try (Stream<String> stream = Files.lines(Paths.get("src/main/resources/data/idOperations.csv"))) {
			m02Web4GIdMappings = stream.collect(Collectors.toMap(line -> Integer.parseInt(line.split(",")[0]), line -> line.split(",")[1]));
		} catch (IOException ignored) {
			// Do nothing, we will have an exception when trying to use the mapping
		}
	}

	// Methods for MSD components

	/** URI of the SIMSv2/SIMSFr metadata structure definition */
	public static String simsMSDURI(boolean simsStrict) { // TODO Add a path segment?
		return (simsStrict ? BASE_SIMS_URI : BASE_SIMS_FR_URI) + "msd";
	}

	/** Label of the SIMSv2/SIMSFr metadata structure definition */
	public static String simsMSDLabel(boolean simsStrict, boolean inFrench) {
		if (inFrench) return "Définition de structre de métadonnées SIMSv2" + (simsStrict ? "" : " - extension française");
		else return "SIMSv2 Metadata Structure Definition"  + (simsStrict ? "" : " - French extension");
	}

	/** URI of the SIMSv2/SIMSFr structure report */
	public static String simsStructureReportURI(boolean simsStrict) { // TODO Add a path segment?
		return (simsStrict ? BASE_SIMS_URI : BASE_SIMS_FR_URI) + "reportStructure";
	}

	/** Label of the SIMSv2/SIMSFr structure report */
	public static String simsReportStructureLabel(boolean simsStrict, boolean inFrench) {
		if (inFrench) return "Structure de rapport de métadonnées SIMSv2" + (simsStrict ? "" : " - extension française");
		else return "SIMSv2 Metadata Structure Report"  + (simsStrict ? "" : " - French extension");
	}

	/** URI of a SIMSv2/SIMSFr metadata attribute specification */
	public static String simsAttributeSpecificationURI(SIMSFrEntry entry, boolean simsStrict) {
		if (entry.isAddedOrModified() && (simsStrict == false)) return BASE_SIMS_FR_URI + "specificationAttribut/" + entry.getNotation();
		else return BASE_SIMS_URI + "attributeSpecification/" + entry.getNotation();
	}

	/** URI of a SIMSv2/SIMSFr metadata attribute property */
	public static String simsAttributePropertyURI(SIMSFrEntry entry, boolean simsStrict) {
		if (entry.isAddedOrModified() && (simsStrict == false)) return BASE_SIMS_FR_URI + "attribut/" + entry.getNotation();
		return BASE_SIMS_URI + "attribute/" + entry.getNotation();
	}

	// Methods for concept schemes components

	/** URI of the SIMSv2/SIMSFr concept scheme */
	public static String simsConceptSchemeURI(boolean simsStrict) {
		return (simsStrict ? BASE_SIMS_URI : BASE_SIMS_FR_URI) + "sims"; // TODO Add a path segment?
	}

	/** Name of the SIMSv2/SIMSFr concept scheme */
	public static String simsConceptSchemeName(boolean simsStrict, boolean inFrench) {
		if (inFrench) return "Structure Unique Intégrée de Métadonnées v2" + (simsStrict ? "" : " - extension française");
		return "Single Metadata Integrated Structure v2" + (simsStrict ? "" : " - French extension");
	}

	/** URI of the statistical themes concept scheme */
	public static String themeSchemeURI() {
		return "http://id.insee.fr/concepts/themes";
	}

	/** URI of the statistical theme class */
	public static String themeClassURI() {
		return "http://id.insee.fr/concepts/themes/Theme"; // Could also go in base ontology
	}

	/** URI of a statistical theme as a function of its code */
	public static String themeURI(String themeNotation) {
		return "http://id.insee.fr/concepts/theme/" + themeNotation.toLowerCase();
	}

	/** @deprecated */
	public static String themeNotation(int topThemeNumber, int themeNumber) {
		return String.format("%d", topThemeNumber) + ((themeNumber > 0) ? String.format("%d", themeNumber) : "");
	}

	/** @deprecated */
	public static String themeURI(int topThemeNumber, int themeNumber) {
		return "http://id.insee.fr/concepts/theme/" + themeNotation(topThemeNumber, themeNumber);
	}

	/** URI of a code list */
	public static String codelistURI(String conceptName) {
		return INSEE_CODES_BASE_URI + camelCase(conceptName, true, true); // Lower camel case and plural
	}

	/** URI of the concept associated to a code list */
	public static String codeConceptURI(String conceptName) {
		return INSEE_CODE_CONCEPTS_BASE_URI + camelCase(conceptName, false, false); // Upper camel case and singular
	}

	/** URI of the concept associated to a SDMX code list */
	public static String sdmxCodeConceptURI(String codeListName) {
		// See https://github.com/UKGovLD/publishing-statistical-data/blob/master/specs/src/main/vocab/sdmx-code.ttl
		// Hack: for CL_REF_AREA, the vocabulary referenced above uses CL_AREA
		return SDMX_CODE_BASE_URI + (codeListName.equalsIgnoreCase("CL_REF_AREA") ? "Area" : codeListNameToConceptName(codeListName));
	}

	/** URI of a code list element */
	public static String inseeCodeURI(String notation, String conceptName) {
		return INSEE_CODES_BASE_URI + camelCase(conceptName, true, false) + "/" + notation;
	}

	/** URI of a SIMSv2 concept as a function of its notation */
	public static String simsConceptURI(String notation) {
		return BASE_SIMS_URI + "concept/" + notation;
	}

	/** URI of the graph containing a SIMSv2Fr report */
	public static String simsReportGraphURI(String reportId) {
		return "http://rdf.insee.fr/graphes/qualite/rapport/" + reportId;
	}

	/** URI of a SIMSv2/SIMSv2Fr represented by a SIMSFrEntry */
	public static String simsConceptURI(SIMSFrEntry entry) {
		if (entry.isOriginal()) return simsConceptURI(entry.getNotation());
		return "http://id.insee.fr/concepts/simsv2fr/" + entry.getNotation();
	}

	/** URI of an operation */
	public static String operationURI(PSPOperationEntry entry) {
		return "http://id.insee.fr/operations/operation/" + entry.getType().operationURIPathElement() + "/" + entry.getCode().toLowerCase();
	}

	/** URI of an organization */
	public static String organizationURI(String organizationId) {
		return INSEE_ORG_BASE_URI + slug(organizationId);
	}

	/** URI of an Insee organizational unit */
	public static String inseeUnitURI(String timbre) {
	
		return INSEE_ORG_BASE_URI + "insee/" + timbre.toLowerCase();
	}

	/** Returns a concept name associated to a code list name of the type CL_XXX_YYY */
	public static String codeListNameToConceptName(String codeListName) {
		// Assuming code list names have the form CL_TERM_OTHERTERM_ETC, will return TermOthertermEtc
		StringBuilder conceptNameBuilder = new StringBuilder();
		String[] terms = codeListName.substring(3).toUpperCase().split("_"); // Skipping initial CL_
		for (String term : terms) {
			conceptNameBuilder.append(term.charAt(0)).append(term.substring(1).toLowerCase());
		}
		return conceptNameBuilder.toString();
	}

	/**
	 * Target URI of a statistical operation, family, series or indicator.
	 * 
	 * @param web4GNumber The Web4G source number (4-digit integer)
	 * @param type Type of resource under consideration: should be 'famille', 'serie', 'operation' or 'indicateur'.
	 * @return The target URI of the resource (for example http://id.insee.fr/operations/serie/s1234)
	 */
	public static String operationResourceURI(String web4GNumber, String type) {
		if ("indicateur".equals(type)) return indicatorURI("p" + web4GNumber);
		return INSEE_OPS_BASE_URI + type + "/s" + web4GNumber;
	}

	/** URI of a statistical operation */
	public static String statisticalOperationURI(String name) {
		return INSEE_OPS_BASE_URI + "operation/" + slug(name);
	}

	/** URI of a statistical operation series */
	public static String statisticalOperationSeriesURI(String name) {
		return INSEE_OPS_BASE_URI + "serie/" + slug(name);
	}

	/** URI of a statistical operation family */
	public static String statisticalOperationFamilyURI(String name) {
		return INSEE_OPS_BASE_URI + "famille/" + slug(name);
	}

	/** URI of a statistical indicator */
	public static String indicatorURI(String indicatorId) {
		return "http://id.insee.fr/produits/indicateur/" + indicatorId;
	}

	/** URI of a SIMS quality report */
	public static String simsReportURI(String documentationId) {
		return REPORT_BASE_URI + documentationId;
	}

	/** URI of a SIMS quality metric (called actually indicator) */
	public static String simsQualityMetricURI(String metricId) {
		return METRIC_BASE_URI + metricId;
	}

	/** URI of the FOAF document representing a 'link' object */
	public static String linkURI(int linkNumber) {
		return "http://id.insee.fr/documents/page/" + linkNumber;
	}

	/** URI of the FOAF document representing a 'document' object */
	public static String documentURI(int documentNumber) {
		return "http://id.insee.fr/documents/document/" + documentNumber;
	}

	/** URI of a CASD dataset */
	public static String datasetURI(String name, String operation) {
		return CASD_PRODUCTS_BASE_URI + "dataset/" + slug(operation) + "-" + slug(name);
	}

	// Utility string manipulation methods

	/**
	 * String "sanitization": removes diacritics, trims and replaces spaces by dashes.
	 * 
	 * @param string The original string.
	 * @return The resulting string.
	 */
	public static String slug(String string) {
		
	    return Normalizer.normalize(string.toLowerCase(), Form.NFD)
	            .replaceAll("\\p{InCombiningDiacriticalMarks}|[^\\w\\s]", "") // But see http://stackoverflow.com/questions/5697171/regex-what-is-incombiningdiacriticalmarks
	            .replaceAll("[\\s-]+", " ")
	            .trim()
	            .replaceAll("\\s", "-");
	}

	/** Removes diacritic marks */
	public static String removeDiacritics(String original) {

		return Normalizer.normalize(original, Form.NFD).replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
	}

	/**
	 * Transforms a group of words into a CamelCase token.
	 * 
	 * @param original The group of words to process.
	 * @param lower If true, the result will be lowerCamelCase (otherwise UpperCamelCase).
	 * @param plural If true, the result will be in plural form (first token only, and 's' is the only form supported)
	 * @return The CamelCase token.
	 */
	public static String camelCase(String original, boolean lower, boolean plural) {

		final List<String> VOID_TOKENS = Arrays.asList("de", "l");

		if (original == null) return null;

		String[] tokens = removeDiacritics(original).trim().replace("'",  " ").split("\\s"); // Replace quote with space and separate the tokens

		StringBuilder builder = new StringBuilder();
		boolean nextSingular = false;
		for (String token : tokens) {
			if (token.length() > 0) {
				if (VOID_TOKENS.contains(token.toLowerCase())) {
					// The word following a void token stays singular
					nextSingular = true;
					continue;
				}
				if (plural) {
					token += (nextSingular ? "" : "s");
					nextSingular = false;
				}
				StringUtils.capitalize(token);
				builder.append(StringUtils.capitalize(token));
			}
		}
		String result = builder.toString();
		if (lower) result = StringUtils.uncapitalize(result);

		return result;
	}
}
