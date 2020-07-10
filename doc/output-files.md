# Output files

This page describes the minimum set of files containing all the converted resources, and how to produce them. Many more smaller files can be produced by the launch methods. All output files are created in the `src/main/resources/data` directory.

## Metadata and schemes

The `SIMSModelMakerTest.exportAllAsTriG()` method creates the `sims-metadata.trig` TriG file containing all SIMSFr metadata (MSD, concepts, base RDF vocabulary).

The `CodelistModelMakerTest.exportAllAsTriG()` method creates the `sims-codes.trig` TriG file containing all SIMSFr metadata (schemes, codes, concepts).

The `OrganizationModelMakerTest.createOrganizationDataset()` method creates the `organizations.trig` TriG file containing the organization schemes.

The `SIMSModelMakerTest.exportGeoAsTriG()` method creates the `sims-geo.trig` TriG file containing the specific territories used in SIMSFr documentations.

## Data

The `M0ConverterTest.testConvertAllOperationsAndIndicators()` method creates the `all-operations-and-indicators.trig` TriG file containing all families, series, operations and indicators, as well as relations between them.

The `M0ConverterTest.testConvertAllToSIMS()` method creates the `sims-all.trig` TriG file which contains all the SIMSFr documentations with their attachments to the documented resources. Data on referenced documents is not included (this can be changed with a boolean parameter).

The `M0ConverterTest.exportDocumentsAsTriG()` method creates the `documents.trig` TriG file which contains the information on all documents referenced in SIMSFr models (M0 'documents' and 'links').
