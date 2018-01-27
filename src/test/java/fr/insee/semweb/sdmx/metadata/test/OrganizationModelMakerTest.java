package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.*;

import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.OrganizationModelMaker;

public class OrganizationModelMakerTest {

	@Test
	public void testGetOrganizationURIFromLabel() {

		assertEquals(OrganizationModelMaker.getOrganizationURIFromLabel("bdf"), "http://id.insee.fr/organisations/banque-de-france");
	}

	@Test
	public void testGetOrganizationURI() {

		assertEquals(Configuration.organizationURI("banque-de-france"), "http://id.insee.fr/organisations/banque-de-france");
	}

}
