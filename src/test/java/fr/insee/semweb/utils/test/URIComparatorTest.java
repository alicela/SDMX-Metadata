package fr.insee.semweb.utils.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import fr.insee.semweb.utils.URIComparator;

class URIComparatorTest {

	@Test
	void testCompare() {

		URIComparator comparator = new URIComparator();
		assertTrue(comparator.compare("http://baseUri/familles/famille/1", "http://baseUri/familles/famille/10") < 0);
		assertTrue(comparator.compare("http://baseUri/familles/famille/2", "http://baseUri/familles/famille/10") < 0);
		assertTrue(comparator.compare("http://baseUri/familles/famille/9", "http://baseUri/familles/famille/10") < 0);
		assertTrue(comparator.compare("http://baseUri/familles/famille/11", "http://baseUri/familles/famille/10") > 0);
		assertTrue(comparator.compare("http://id.insee.fr/operations/famille/s30", "http://id.insee.fr/operations/famille/s30") == 0);
		assertTrue(comparator.compare("http://id.insee.fr/operations/famille/s3", "http://id.insee.fr/operations/famille/s30") < 0);
		assertTrue(comparator.compare("", "http://id.insee.fr/operations/famille/s30") < 0);
		assertTrue(comparator.compare("http://id.insee.fr/operations/famille/s30", "") > 0);
		assertTrue(comparator.compare("", "") == 0);
	}
}
