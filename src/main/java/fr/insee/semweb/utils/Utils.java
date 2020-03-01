package fr.insee.semweb.utils;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

/**
 * Utility methods for the conversion and checks methods.
 * 
 * @author Franck
 */
public class Utils {

	/** Log4J2 logger */
	public static Logger logger = LogManager.getLogger(Utils.class);

	/**
	 * Transforms a group of words into a camel case token.
	 * 
	 * @param original The group of words to process.
	 * @param lower If true, the result will be lowerCamelCase (otherwise UpperCamelCase).
	 * @param plural If true, the result will be in plural form (first token only, and 's' is the only form supported)
	 * @return The camel case token.
	 */
	public static String camelCase(String original, boolean lower, boolean plural) {
	
		final List<String> VOID_TOKENS = Arrays.asList("de", "l");
	
		if (original == null) return null;
	
		String[] tokens = removeDiacritics(original).trim().replace("'",  " ").split("\\s"); // Replace quote with space and separate the tokens
	
		StringBuilder builder = new StringBuilder();
		boolean nextSingular = false;
		for (String token : tokens) {
			if (token.length() > 0) {
				if (VOID_TOKENS.contains(token.toLowerCase())) {
					// The word following a void token stays singular
					nextSingular = true;
					continue;
				}
				if (plural) {
					token += (nextSingular ? "" : "s");
					nextSingular = false;
				}
				StringUtils.capitalize(token);
				builder.append(StringUtils.capitalize(token));
			}
		}
		String result = builder.toString();
		if (lower) result = StringUtils.uncapitalize(result);
	
		return result;
	}

	/**
	 * Produces a short textual representation of a RDF node.
	 * 
	 * @param node The RDF node to describe.
	 * @return The textual representation of the node.
	 */
	public static String nodeToAbbreviatedString(RDFNode node) {
	
		if (node.isURIResource()) return node.asResource().getURI();
		if (node.isAnon()) return "<blank node>";
		// At this point, we have a literal node
		return StringUtils.abbreviateMiddle(node.asLiteral().getLexicalForm(), " (...) ", 100);
	}

	/**
	 * Computes the differences between the values of two RDF nodes and writes them to a file.
	 * 
	 * @param node1 The first RDF node to compare.
	 * @param node2 The second RDF node to compare.
	 * @param diffFileName Name of the file to which the diff report will be written.
	 */
	public static void claculateDiffs(RDFNode node1, RDFNode node2, String diffFileName) {
	
		if (!(node1.isLiteral() && node2.isLiteral())) return;
		PrintWriter diffWriter = null;
		try {
			diffWriter = new PrintWriter(diffFileName);
		} catch (FileNotFoundException e) {
			logger.error("Error creating the diff file", e);
			return;
		}
		String baseString = node1.asLiteral().getLexicalForm();
		String comparedString = node2.asLiteral().getLexicalForm();
		diffWriter.println("Base string\n" + baseString);
		diffWriter.println("\nCompared string\n" + comparedString);
	
		diffWriter.println("\nDifferences\n");
	    Patch<String> patch;
		try {
			patch = DiffUtils.diff(Arrays.asList(baseString.split("\n")), Arrays.asList(comparedString.split("\n")));
	        for (AbstractDelta<String> delta: patch.getDeltas()) {
	        	diffWriter.println(delta);
	        }
		} catch (DiffException e) {
			logger.error("Error while calculating the differences", e);
		}
	
	    diffWriter.close();
	}

	/**
	 * String "sanitization": removes diacritics, trims and replaces spaces by dashes.
	 * 
	 * @param string The original string.
	 * @return The resulting string.
	 */
	public static String slug(String string) {
		
	    return Normalizer.normalize(string.toLowerCase(), Form.NFD)
	            .replaceAll("\\p{InCombiningDiacriticalMarks}|[^\\w\\s]", "") // But see http://stackoverflow.com/questions/5697171/regex-what-is-incombiningdiacriticalmarks
	            .replaceAll("[\\s-]+", " ")
	            .trim()
	            .replaceAll("\\s", "-");
	}

	/** 
	 * Removes diacritic marks in a string.
	 * 
	 * @param original The original string.
	 * @return The resulting string.
	 */
	public static String removeDiacritics(String original) {
	
		return Normalizer.normalize(original, Form.NFD).replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
	}
}
