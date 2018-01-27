package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.SourceConverter;

public class SourceConverterTest {

	@Test
	public void testSplitModel() throws IOException {

		List<String> testOperations = Arrays.asList("IND-COUT-TRAVAIL-ICHT-TS", "IND-COMMANDES-INDUSTRIE");
		SourceConverter.splitModel(testOperations);
	}

	@Test
	public void testGetOperationName() {

		assertEquals(SourceConverter.getOperationId("http://baseUri/FR-IND-COMMANDES-INDUSTRIE/REF_AREA"), "IND-COMMANDES-INDUSTRIE");
	}

	@Test
	public void testGetPropertyName() {

		assertEquals(SourceConverter.getPropertyCode("http://baseUri/FR-IND-COMMANDES-INDUSTRIE/REF_AREA"), "REF_AREA");
		assertNull(SourceConverter.getPropertyCode("http://baseUri/FR-IND-COMMANDES-INDUSTRIE"));
	}
}
