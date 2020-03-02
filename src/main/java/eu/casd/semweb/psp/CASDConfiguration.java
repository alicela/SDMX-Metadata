package eu.casd.semweb.psp;

import fr.insee.semweb.utils.Utils;

public class CASDConfiguration {

	/** Excel file containing the information on operations */
	public static String OPERATIONS_XLSX_FILE_NAME = "src/main/resources/data/Liste sources_20170612_CASD.xlsx";

	/** Base URI for CASD products */
	public static String CASD_PRODUCTS_BASE_URI = "http://id.casd.eu/produits/";

	/** URI of a CASD dataset */
	public static String datasetURI(String name, String operation) {
		return CASD_PRODUCTS_BASE_URI + "dataset/" + Utils.slug(operation) + "-" + Utils.slug(name);
	}

	/** URI of an operation */
	public static String operationURI(PSPOperationEntry entry) {
		return "http://id.insee.fr/operations/operation/" + entry.getType().operationURIPathElement() + "/" + entry.getCode().toLowerCase();
	}

	// TODO Move here configuration parameters defined in the different classes of the project
}
