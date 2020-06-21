package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import javax.naming.NamingException;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.OrganizationModelMaker;

/**
 * Test and launch methods for class <code>OrganizationModelMaker</code>.
 * 
 * @author Franck
 */
public class OrganizationModelMakerTest {

	/**
	 * Tests the method computing an organization URI from a label.
	 */
	@Test
	public void testGetOrganizationURIFromLabel() {

		assertEquals(OrganizationModelMaker.getOrganizationURIFromLabel("bdf"), "http://id.insee.fr/organisations/banque-de-france");
	}

	/**
	 * Creates the RDF model of Insee's units from the Excel workbook and writes it to a Turtle file.
	 * 
	 * @throws Exception In case of problem opening the workbook or writing the Turtle file.
	 */
	@Test
	public void testCreateInseeModel() throws Exception {
		Workbook orgWorkbook = WorkbookFactory.create(new File(Configuration.ORGANIZATIONS_XLSX_FILE_NAME));
		Model inseeModel = OrganizationModelMaker.createInseeModel(orgWorkbook);
		inseeModel.write(new FileWriter("src/main/resources/data/insee.ttl"), "TTL");
		inseeModel.close();
	}

	/**
	 * Creates the RDF model of SSM units from the Excel workbook and writes it to a Turtle file.
	 * 
	 * @throws Exception In case of problem opening the workbook or writing the Turtle file.
	 */
	@Test
	public void testCreateSSMModel() throws Exception {
		Workbook orgWorkbook = WorkbookFactory.create(new File(Configuration.ORGANIZATIONS_XLSX_FILE_NAME));
		Model ssmModel = OrganizationModelMaker.createSSMModel(orgWorkbook);
		ssmModel.write(new FileWriter("src/main/resources/data/ssm.ttl"), "TTL");
		ssmModel.close();
	}

	/**
	 * Creates the RDF model of Insee's units from the LDAP directory and writes it to a Turtle file.
	 * 
	 * @throws IOException In case of problem writing the Turtle.
	 * @throws NamingException In case of problem accessing the LDAP directory.
	 */
	@Test
	public void testCreateInseeModelFromLDAP() throws IOException, NamingException {

		Model inseeLDAPModel = OrganizationModelMaker.createInseeModelFromLDAP();
		inseeLDAPModel.write(new FileWriter("src/main/resources/data/insee-ldap.ttl"), "TTL");
		inseeLDAPModel.close();
	}

	/**
	 * Creates a RDF dataset containing all the information on organizations and writes it to a TriG file.
	 * 
	 * @param useLDAP Indicates if Insee units must be read in the LDAP directory (otherwise in the Excel workbook).
	 * @throws Exception In case of problem creating the dataset.
	 */
	@Test
	public void createOrganizationDataset(boolean useLDAP) throws IOException {

		String inseeGraphURI = Configuration.INSEE_BASE_GRAPH_URI + "organisations/insee";
		String ssmGraphURI = Configuration.INSEE_BASE_GRAPH_URI + "organisations"; 

		Workbook orgWorkbook = WorkbookFactory.create(new File(Configuration.ORGANIZATIONS_XLSX_FILE_NAME));

		Dataset dataset = DatasetFactory.create();
		Model inseeModel = useLDAP ? OrganizationModelMaker.createInseeModelFromLDAP() : OrganizationModelMaker.createInseeModel(orgWorkbook);
		Model ssmModel = OrganizationModelMaker.createSSMModel(orgWorkbook);
		if (inseeGraphURI.equals(ssmGraphURI)) dataset.addNamedModel(inseeGraphURI, inseeModel.add(ssmModel));
		else dataset.addNamedModel(inseeGraphURI, inseeModel).addNamedModel(ssmGraphURI, ssmModel);

		RDFDataMgr.write(new FileOutputStream("src/main/resources/data/organizations.trig"), dataset, Lang.TRIG);
		inseeModel.close();
		ssmModel.close();
		dataset.close();
	}
}
