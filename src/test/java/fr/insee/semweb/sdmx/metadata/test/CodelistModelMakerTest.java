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

public class CodelistModelMakerTest {

	@Test
	public void testReadCodelistDataset() throws IOException {

		Dataset codes = CodelistModelMaker.readCodelistDataset(new File(Configuration.CL_XLSX_FILE_NAME), "http://rdf.insee.fr/graphes/concepts", "http://rdf.insee.fr/graphes/codes");
		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/sims-cl.trig"), codes, Lang.TRIG);
	}

	@Test
	public void testFrequencyCodelist() throws Exception {
		Workbook clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		Model code = CodelistModelMaker.readCodelist(clWorkbook.getSheetAt(0));
		code.write(new FileWriter("src/main/resources/data/cl-frequency.ttl"), "TTL");
	}

	@Test
	public void testSourceCategoryCodelist() throws Exception {
		Workbook clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		Model code = CodelistModelMaker.readCodelist(clWorkbook.getSheetAt(2));
		code.write(new FileWriter("src/main/resources/data/cl-source-category.ttl"), "TTL");
	}

	@Test
	public void testSurveyStatusCodelist() throws Exception {
		Workbook clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		Model code = CodelistModelMaker.readCodelist(clWorkbook.getSheetAt(3));
		code.write(new FileWriter("src/main/resources/data/cl-survey-status.ttl"), "TTL");
	}

	@Test
	public void testSurveyUnitCodelist() throws Exception {
		Workbook clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		Model code = CodelistModelMaker.readCodelist(clWorkbook.getSheetAt(4));
		code.write(new FileWriter("src/main/resources/data/cl-survey-unit.ttl"), "TTL");
	}

	@Test
	public void testCollectionModeCodelist() throws Exception {
		Workbook clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		Model code = CodelistModelMaker.readCodelist(clWorkbook.getSheetAt(5));
		code.write(new FileWriter("src/main/resources/data/cl-collection-mode.ttl"), "TTL");
	}

	@Test
	public void testThemesCodelist() throws Exception {

		Workbook clWorkbook = WorkbookFactory.create(new File(Configuration.CL_XLSX_FILE_NAME));
		Model themes = CodelistModelMaker.readThemesConceptScheme(clWorkbook.getSheetAt(7));
		themes.write(new FileWriter(Configuration.THEMES_TURTLE_FILE_NAME), "TTL");
	}

	@Test
	public void testGetNotationConceptMappings() {

		Map<String, Resource> mappings = CodelistModelMaker.getNotationConceptMappings();
		for (String clNotation : mappings.keySet()) System.out.println(clNotation + " - " + mappings.get(clNotation));
	}

	@Test
	public void testExportAsMarkdown() {

		// Create a dataset of all code lists in one unique model
		String dummyGraphURI = "http://rdf.insee.fr/graphes/dummy";
		Dataset codes = CodelistModelMaker.readCodelistDataset(new File(Configuration.CL_XLSX_FILE_NAME), dummyGraphURI, dummyGraphURI);
		Model clModel = codes.getNamedModel(dummyGraphURI);
		
	}
}
