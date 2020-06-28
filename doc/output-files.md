# Output files

This page describes the minimum set of files containing all the converted resources, and how to produce them. Many more smaller files can be produced by the launch methods. All output files are created in the `src/main/resources/data` directory.

## Metadata and schemes

The `SIMSModelMakerTest.exportAllAsTriG()` method creates the `sims-metadata.trig` TriG file containing all SIMSFr metadata (MSD, concepts, base RDF vocabulary).

The `CodelistModelMakerTest.exportAllAsTriG()` method creates the `sims-codes.trig` TriG file containing all SIMSFr metadata (MSD, concepts, base RDF vocabulary).

The `OrganizationModelMakerTest.createOrganizationDataset()` method creates the `organizations.trig` TriG file containing the organization schemes.


## Data

The `M0ConverterTest.testConvertAllOperationsAndIndicators()` method creates the `all-operations-and-indicators.trig` TriG file containing all families, series, operations and indicators, as well as relations between them.

The `M0ConverterTest.testConvertAllToSIMS()` method creates the `models/sims-all.trig` TriG file which contains all the SIMSFr documentations with their attachments to the documented resources.
