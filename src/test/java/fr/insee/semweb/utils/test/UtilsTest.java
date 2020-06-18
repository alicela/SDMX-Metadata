package fr.insee.semweb.utils.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.jupiter.api.Test;

import fr.insee.semweb.utils.Utils;

/**
 * Test and launch methods for class <code>Utils</code>.
 * 
 * @author Franck
 */
class UtilsTest {

	@Test
	public void testCamelCase1() {
		assertEquals(Utils.camelCase("How about that", true, false), "howAboutThat");
		assertEquals(Utils.camelCase("How about that", true, true), "howsAboutThat");
		assertEquals(Utils.camelCase("A  B C dF edd", true, false), "aBCDfEdd");
		assertNull(Utils.camelCase(null, true, true));
	}

	@Test
	public void testCamelCase2() {
		assertEquals(Utils.camelCase("Type de source", true, true), "typesSource");
		assertEquals(Utils.camelCase("Type de source", true, false), "typeSource");
		assertEquals(Utils.camelCase("Type de source", false, true), "TypesSource");
		assertEquals(Utils.camelCase("Type de source", false, false), "TypeSource");
		assertEquals(Utils.camelCase("Unité enquêtée", true, true), "unitesEnquetees");		
		assertEquals(Utils.camelCase("Unité enquêtée", true, false), "uniteEnquetee");		
	}

	@Test
	public void testCamelCase3() {
		assertEquals(Utils.camelCase("Frequence", true, true), "frequences");
		assertEquals(Utils.camelCase("Statut de l'enquête", true, false), "statutEnquete");
		assertEquals(Utils.camelCase("Statut de l'enquête", false, true), "StatutsEnquete");
		assertEquals(Utils.camelCase("Catégorie de source", false, true), "CategoriesSource");
	}
}
