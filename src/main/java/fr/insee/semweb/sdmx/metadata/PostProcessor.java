package fr.insee.semweb.sdmx.metadata;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.Consumer;

/**
 * Applies specific treatments after M0 -> target conversion.
 */

 public class PostProcessor {

	/** Log4J2 logger */
	public static Logger logger = LogManager.getLogger();

	/**
	 * Personalizes the labels of SIMS documentations using the labels of the documented resources.
	 *
	 * @param simsModel The Jena model containing the SIMS documentations.
	 * @param resourceModel The Jena model containing the documented resources.
	 */
	public static void enrichGSIMLabels(Model simsModel, Model resourceModel) {

		// Loop through the instances of metadata report
		Selector selector = new SimpleSelector(null, RDF.type, Configuration.SIMS_METADATA_REPORT);
		simsModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				logger.debug(statement);
				// Get the subject report and the associated target resource
				Resource report = statement.getSubject();
				StmtIterator attachmentStatements = report.listProperties(Configuration.SIMS_TARGET);
				if (!attachmentStatements.hasNext()) logger.warn("Report " + report.getURI() + " is not attached to any resource");
				Resource targetResource = attachmentStatements.nextStatement().getObject().asResource();
				logger.info("Report " + report.getURI() + " is attached to resource " + targetResource.getURI() + ", looking up for resource in resource model");
				// If we find the report attached to more than one resource, that's an error
				attachmentStatements.forEachRemaining(ignoredStatement -> { logger.error("Statement ignored: " + ignoredStatement.toString()); });
				attachmentStatements.close();
				// Try to find the documented resource and its labels (SKOS prefLabel) in the resource model
				Resource documentedResource = resourceModel.createResource(targetResource.getURI());
				Statement labelStatement = documentedResource.getProperty(SKOS.prefLabel, "fr");
				if (labelStatement != null) {
					String frenchResourceLabel = labelStatement.getObject().asLiteral().getLexicalForm();
					// Remove current report's French label
					Statement statementToReplace = report.getProperty(RDFS.label, "fr");
					simsModel.remove(statementToReplace);
					// Add new value for the report's French label
					String newReportFrenchLabel = "Rapport qualité : " + frenchResourceLabel;
					report.addProperty(RDFS.label, simsModel.createLiteral(newReportFrenchLabel, "fr"));
					logger.debug("French label modified for resource " + documentedResource.getURI() + " in the resource model");
				} else {
					logger.error("No French label found for resource " + documentedResource.getURI() + " in the resource model");
				}
				labelStatement = documentedResource.getProperty(SKOS.prefLabel, "en");
				if (labelStatement != null) {
					String englishResourceLabel = labelStatement.getObject().asLiteral().getLexicalForm();
					// Remove current report's English label
					Statement statementToReplace = report.getProperty(RDFS.label, "en");
					simsModel.remove(statementToReplace);
					// Add new value for the report's English label
					String newReportFrenchLabel = "Quality report: " + englishResourceLabel;
					report.addProperty(RDFS.label, simsModel.createLiteral(newReportFrenchLabel, "en"));
					logger.debug("English label modified for resource " + documentedResource.getURI() + " in the resource model");
				} else {
					logger.error("No English label found for resource " + documentedResource.getURI() + " in the resource model");
				}
			}
		});
	}

	/**
	 * Modifies a Jena model by adding statements on resources of a given class.
	 * All statements of the list will be added to each instance of the given class which is subject in the model.
	 * Subjects of the provided statements are ignored.
	 * No checks are made to verify if statements are already in the model.
	 *
	 * @param model The Jena model to modify.
	 * @param resourceType The type (OWL/RDFS class) of subject resources to qualify.
	 * @param statements List of statements containing properties and values to add (subjects are ignored).
	 */
	public static void addStatements(Model model, Resource resourceType, List<Statement> statements) {

		// Loop through the instances of the given class in the model
		logger.info(statements.size() + " statements to be added to all instances of " + resourceType.getURI());
		final int[] modifiedResources = {0}; // Variables used in inner Consumer should be final
		Selector selector = new SimpleSelector(null, RDF.type, resourceType);
		model.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				Resource instance = statement.getSubject();
				for (Statement statementToAdd : statements) {
					instance.addProperty(statementToAdd.getPredicate(), statementToAdd.getObject());
				}
				logger.debug("Statements added to resource " + instance.getURI());
				modifiedResources[0]++;
			}
		});
		logger.info(modifiedResources[0] + " resources modified");
	}
 }
