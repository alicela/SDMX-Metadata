package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertEquals;

import javax.naming.NamingException;

import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.OrganizationModelMaker;

/**
 * Test and launch methods for class <code>OrganizationModelMaker</code>.
 * 
 * @author Franck
 */
public class OrganizationModelMakerTest {

	@Test
	public void testGetOrganizationURIFromLabel() {

		assertEquals(OrganizationModelMaker.getOrganizationURIFromLabel("bdf"), "http://id.insee.fr/organisations/banque-de-france");
	}

	@Test
	public void testCreateInseeModelFromLDAP() throws NamingException {

		OrganizationModelMaker.createInseeModelFromLDAP();
	}
}
