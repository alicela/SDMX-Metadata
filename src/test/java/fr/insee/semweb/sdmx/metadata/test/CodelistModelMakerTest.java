package fr.insee.semweb.sdmx.metadata.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.CodelistModelMaker;
import fr.insee.semweb.sdmx.metadata.Configuration;

/**
 * Test and launch methods for class <code>CodelistModelMaker</code>.
 * 
 * @author Franck
 */
public class CodelistModelMakerTest {


	/**
	 * Creates all the RDF code lists, including the 'themes' concept scheme, and writes them to a TriG file.
	 * 
	 * @throws IOException In case of problems writing the TriG file.
	 */
	@Test
	public void testReadCodelistDataset() throws IOException {

		Dataset codes = CodelistModelMaker.readCodelistDataset(new File(Configuration.CL_XLSX_FILE_NAME), "http://rdf.insee.fr/graphes/concepts", "http://rdf.insee.fr/graphes/codes");
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/sims-cl.trig"), codes, Lang.TRIG);
	}

	/**
	 * Creates all the RDF code lists except CL_AREA but including the 'themes' concept scheme, and writes them to a TriG file.
	 * 
	 * @throws IOException In case of problems writing the TriG file.
	 */
	@Test
	public void testReadCodelistDatasetExceptArea() throws IOException {

		Dataset codes = CodelistModelMaker.readCodelistDataset(new File(Configuration.CL_XLSX_FILE_NAME), "http://rdf.insee.fr/graphes/concepts", "http://rdf.insee.fr/graphes/codes", "CL_AREA");
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/sims-cl-x-area.trig"), codes, Lang.TRIG);
	}

	/**
	 * Creates all the RDF code lists except CL_AREA and CL_UNIT_MEASURE but including the 'themes' concept scheme, and writes them to a TriG file.
	 * 
	 * @throws IOException In case of problems writing the TriG file.
	 */
	@Test
	public void testReadCodelistDatasetExceptAreaAndMeasure() throws IOException {

		Dataset codes = CodelistModelMaker.readCodelistDataset(new File(Configuration.CL_XLSX_FILE_NAME), "http://rdf.insee.fr/graphes/concepts", "http://rdf.insee.fr/graphes/codes", "CL_AREA", "CL_UNIT_MEASURE");
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/sims-cl-x-area-measure.trig"), codes, Lang.TRIG);
	}

	/**
	 * Creates all the RDF code lists excluding the 'themes' concept scheme, and writes them to a TriG file.
	 * 
	 * @throws IOException In case of problems writing the TriG file.
	 */
	@Test
	public void testReadCodelistDatasetExceptThemes() throws IOException {

		Dataset codes = CodelistModelMaker.readCodelistDataset(new File(Configuration.CL_XLSX_FILE_NAME), "http://rdf.insee.fr/graphes/concepts", "http://rdf.insee.fr/graphes/codes", "CL_TOPICS");
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/sims-cl-x-themes.trig"), codes, Lang.TRIG);
	}

	/**
	 * Creates the RDF 'Frequency' code list and writes it to a Turtle file.
	 * 
	 * @throws IOException In case of problems writing the Turtle file.
	 */
	@Test
	public void testFrequencyCodelist() throws Exception {
		Workbook clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		Model code = CodelistModelMaker.readCodelist(clWorkbook.getSheetAt(0));
		code.write(new FileWriter("src/main/resources/data/cl-frequency.ttl"), "TTL");
	}

	/**
	 * Creates the RDF 'Source category' code list and writes it to a Turtle file.
	 * 
	 * @throws IOException In case of problems writing the Turtle file.
	 */
	@Test
	public void testSourceCategoryCodelist() throws Exception {
		Workbook clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		Model code = CodelistModelMaker.readCodelist(clWorkbook.getSheetAt(2));
		code.write(new FileWriter("src/main/resources/data/cl-source-category.ttl"), "TTL");
	}

	/**
	 * Creates the RDF 'Survey status' code list and writes it to a Turtle file.
	 * 
	 * @throws IOException In case of problems writing the Turtle file.
	 */
	@Test
	public void testSurveyStatusCodelist() throws Exception {
		Workbook clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		Model code = CodelistModelMaker.readCodelist(clWorkbook.getSheetAt(3));
		code.write(new FileWriter("src/main/resources/data/cl-survey-status.ttl"), "TTL");
	}

	/**
	 * Creates the RDF 'Survey unit' code list and writes it to a Turtle file.
	 * 
	 * @throws IOException In case of problems writing the Turtle file.
	 */
	@Test
	public void testSurveyUnitCodelist() throws Exception {
		Workbook clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		Model code = CodelistModelMaker.readCodelist(clWorkbook.getSheetAt(4));
		code.write(new FileWriter("src/main/resources/data/cl-survey-unit.ttl"), "TTL");
	}

	/**
	 * Creates the RDF 'Collection mode' code list and writes it to a Turtle file.
	 * 
	 * @throws IOException In case of problems writing the Turtle file.
	 */
	@Test
	public void testCollectionModeCodelist() throws Exception {
		Workbook clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		Model code = CodelistModelMaker.readCodelist(clWorkbook.getSheetAt(5));
		code.write(new FileWriter("src/main/resources/data/cl-collection-mode.ttl"), "TTL");
	}

	/**
	 * Creates the RDF 'Themes' concept schemes and writes it to a Turtle file.
	 * 
	 * @throws IOException In case of problems writing the Turtle file.
	 */
	@Test
	public void testThemesCodelist() throws Exception {

		Workbook clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		Model themes = CodelistModelMaker.readThemesConceptScheme(clWorkbook.getSheetAt(7));
		themes.write(new FileWriter(Configuration.THEMES_TURTLE_FILE_NAME), "TTL");
	}

	/**
	 * Writes to the console the mappings between a code list notation and the URI of the corresponding concept.
	 * E.g. CL_COLLECTION_MODE - http://id.insee.fr/codes/concept/ModeCollecte
	 */
	@Test
	public void testGetNotationConceptMappings() {

		Map<String, Resource> mappings = CodelistModelMaker.getNotationConceptMappings();
		for (String clNotation : mappings.keySet()) System.out.println(clNotation + " - " + mappings.get(clNotation));
	}
}
