package fr.insee.semweb.utils;

import java.util.Comparator;

/**
 * Custom comparator for sorting URIs ending with numbers according to the numerical order on those numbers.
 * Will raise an exception if one of the URIs is <code>null</code>, so should be used with <code>nullsFirst</code> or <code>nullsLast</code>.
 * 
 * @author Franck
 */
public class URIComparator implements Comparator<String> {

	/**
	 * Performs the comparison.
	 * 
	 * @param uri1 The first URI to compare.
	 * @param uri2 The second URI to compare.
	 * @return The usual result of a comparator (-1, 0 or 1).
	 */
	@Override
	public int compare(String uri1, String uri2) {

		// Extract last digits from each string
		final String[] parts1 = separateEndDigits(uri1);
		final String[] parts2 = separateEndDigits(uri2);

		int comparison = parts1[0].compareTo(parts2[0]);
		if (comparison == 0) {
			try {
				comparison = Integer.compare(Integer.parseInt(parts1[1]), Integer.parseInt(parts2[1]));
			} catch(Exception e) {
				// Thrown if at least one of the 'numeric' string empty
				 comparison = parts1[1].compareTo(parts2[1]);
			}
		}

		return comparison;
	}

	/**
	 * Splits a (URI) string: the ending digits are separated as a new string.
	 * 
	 * @param uri The (URI) string to process.
	 * @return An array of two string with the numeric part as second element and the rest as first.
	 */
	private String[] separateEndDigits(String uri) {

		if (uri == null) return null;

		int splitIndex = uri.length();
		while ((--splitIndex >= 0) && Character.isDigit(uri.charAt(splitIndex)));

		final String[] parts = {uri.substring(0, splitIndex + 1), uri.substring(splitIndex + 1)};
		return parts;
	}
}
