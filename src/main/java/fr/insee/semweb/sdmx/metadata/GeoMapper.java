package fr.insee.semweb.sdmx.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Selector;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.SKOS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GeoMapper {

	final static String SPARQL_ENDPOINT = "http://rdf.insee.fr/sparql";

	final static String QUERY_STRING = "PREFIX igeo:<http://rdf.insee.fr/def/geo#> " + 
			"SELECT ?territoire ?nom WHERE {?territoire a ?type ; igeo:nom ?nom ; !igeo:suppression ?date} " + 
			"VALUES ?type {igeo:Region igeo:Departement}" ;

	/** Log4J2 logger */
	public static Logger logger = LogManager.getLogger();


	/**
	 * Returns the mappings between the M0 codes of territories (CODE_VALUE attribute of the CL_AREA codes) and target resources.
	 * 
	 * @return The mappings as a map where the keys are the M0 codes and the values the target resources, sorted on keys.
	 */
	public static SortedMap<String, Resource> createM0CodeToURIMappings() {

		// TODO Quick and dirty hardwired implementation for now
		// Used values are FHM, FR, FRE1, FRHDF01, FRY1, FRY3, FRY4, FRY5, MF and OTHER

		SortedMap<String, Resource> m0CodeToResourceMappings = new TreeMap<>();

		m0CodeToResourceMappings.put("FHM", ResourceFactory.createResource(Configuration.BASE_SIMS_URI + "franceHorsMayotte")); // TODO Check URI
		m0CodeToResourceMappings.put("FR", ResourceFactory.createResource("http://id.insee.fr/geo/pays/france"));
		m0CodeToResourceMappings.put("FRE1", ResourceFactory.createResource("http://id.insee.fr/geo/region/ab3afd3d-b2ef-433b-96d4-d38962c60b2f")); // Nord-Pas-de-Calais
		m0CodeToResourceMappings.put("FRHDF01", ResourceFactory.createResource("http://id.insee.fr/geo/region/70086d81-9af2-4aeb-8734-502658d6a93f")); // Hauts-de-France
		m0CodeToResourceMappings.put("FRY1", ResourceFactory.createResource("http://id.insee.fr/geo/region/598b3ed6-a7ea-44f8-a130-7a42e3630a8a")); // Guadeloupe (région créée en 2007)
		m0CodeToResourceMappings.put("FRY3", ResourceFactory.createResource("http://id.insee.fr/geo/region/465f8cf0-4b80-49b3-ba79-2ab47f4895e9")); // Guyane (région)
		m0CodeToResourceMappings.put("FRY4", ResourceFactory.createResource("http://id.insee.fr/geo/region/5bc4db56-216e-41be-90a1-6b84c0607184")); // La Réunion (région)
		m0CodeToResourceMappings.put("FRY5", ResourceFactory.createResource("http://id.insee.fr/geo/region/0e9f9adc-742d-4ab7-90bd-30e5aaf7b2ab")); // Mayotte (région)
		m0CodeToResourceMappings.put("MF", ResourceFactory.createResource("http://id.insee.fr/geo/territoireFrancais/franceMetropolitaine")); // France métropolitaine

		return m0CodeToResourceMappings;		
	}

	/**
	 * Returns the mappings between the M0 names of territories (TITLE attribute of the CL_AREA codes) and target URIs.
	 * 
	 * @return The mappings as a map where the keys are the M0 names and the values the target URIs, sorted on keys.
	 */
	public static SortedMap<String, String> createGeoURIMappings() {
		return null;
	}

	/**
	 * Returns the mappings between the M0 names and codes of territories (TITLE and CODE_VALUE attributes).
	 * 
	 * @return The mappings as a map where the keys are the M0 names and the values are the M0 codes, sorted on keys.
	 */
	public static SortedMap<String, String> createM0GeoNameCodeMappings() {

		SortedMap<String, String> m0GeoNameCodeMappings = new TreeMap<>();
		Model codeListsModel = M0Converter.convertCodeLists();
		// Get the M0 resource for the CL_AREA code list
		List<Resource> m0CLAreaResources = new ArrayList<>();
		codeListsModel.listStatements(null, SKOS.notation, "CL_AREA").forEachRemaining(statement -> m0CLAreaResources.add(statement.getSubject()));
		if (m0CLAreaResources.size() != 1) logger.error("There should be exactly one code list with notation CL_AREA, but found " + m0CLAreaResources.size());
		else {
			// Get all the code URIs
			Resource m0CLAreaResource = m0CLAreaResources.get(0);
			m0CLAreaResources.clear();
			codeListsModel.listSubjectsWithProperty(SKOS.inScheme, m0CLAreaResource).forEachRemaining(resource -> m0CLAreaResources.add(resource));
			// Now for each code get the notation and French label
			for (Resource codeResource : m0CLAreaResources) {
				String code = codeResource.listProperties(SKOS.notation).toList().get(0).getObject().toString();
				String label = codeResource.listProperties(SKOS.prefLabel, "fr").toList().get(0).getObject().asLiteral().getLexicalForm();
				m0GeoNameCodeMappings.put(code, label);
			}
		}
		codeListsModel.close();
		return m0GeoNameCodeMappings;
	}

	/**
	 * Returns the list of M0 area codes that are actually used in the M0 'documentations' model.
	 * 
	 * @param m0DocumentationsModel The 'documentations' model to use.
	 * @return The set of used area codes, sorted alphabetically.
	 */
	public static SortedSet<String> getUsedAreaCodes(Model m0DocumentationsModel) {

		SortedSet<String> usedValues = new TreeSet<>();

		// Select the triples that have a REF_AREA attribute URI as subject and 'values' as predicate
		Selector selector = new SimpleSelector(null, Configuration.M0_VALUES, (RDFNode) null) {
			public boolean selects(Statement statement) {
				return statement.getSubject().getURI().endsWith("/REF_AREA");
			}
		};

		// List the values of the attribute that are used in the documentation model
		m0DocumentationsModel.listStatements(selector).forEachRemaining(statement -> usedValues.add(statement.getObject().toString()));

		m0DocumentationsModel.close();
		return usedValues;
	}

	/**
	 * Queries the RDF endpoint for the mappings between territory names and URIs.
	 * 
	 * @return The mappings as a map where the keys are the territory names and their URIs, sorted on keys.
	 */
	public static SortedMap<String, String> queryNameURIMappings() {

		Query query = QueryFactory.create(QUERY_STRING);
		String var1 = query.getProjectVars().get(0).getVarName();
		String var2 = query.getProjectVars().get(1).getVarName();

		SortedMap<String, String> labelURIMappings = new TreeMap<>();

		try (QueryExecution execution = QueryExecutionFactory.sparqlService(SPARQL_ENDPOINT, query)) {
			ResultSet results = execution.execSelect();
			results.forEachRemaining(solution -> {
				String uri = solution.get(var1).toString();
				String name = solution.get(var2).toString();
				String previousValue = labelURIMappings.put(name, uri);
				if (previousValue != null) logger.warn("Multiple URIs for name " + name + ": previous value " + previousValue + " replaced by " + uri);
			});
		}
		return labelURIMappings;
	}

	/**
	 * Returns the list of M0 documentations statements for which REF_AREA value is 'OTHER'.
	 * 
	 * @param m0DocumentationsModel The 'documentations' model to use.
	 * @return The set of M0 statements, sorted alphabetically.
	 */
	public static SortedSet<String> getDocumentationsWithOherAreaCode(Model m0DocumentationsModel) {

		SortedSet<String> otherValues = new TreeSet<>();

		// Select the triples that have a REF_AREA attribute URI as subject, a 'values' as predicate and 'OTHER' as object
		Selector selector = new SimpleSelector(null, Configuration.M0_VALUES, "OTHER") {
			public boolean selects(Statement statement) {
				return statement.getSubject().getURI().endsWith("/REF_AREA");
			}
		};

		// List the statements with REF_AREA = 'OTHER'
		m0DocumentationsModel.listStatements(selector).forEachRemaining(statement -> otherValues.add(statement.toString()));

		return otherValues;
	}
}
