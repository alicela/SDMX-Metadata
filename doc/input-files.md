# Input files

Eight input files are used for the conversion of M0 information to the target model.

## Metadata

The Metadata Structure Definition relies on the [SDMX-MM](https://github.com/linked-statistics/SDMX-Metadata) vocabulary. The name of the [Turtle file](https://github.com/linked-statistics/SDMX-Metadata/blob/master/sdmx-metadata.ttl) containing the vocabulary is referred to as the `SDMX_MM_TURTLE_FILE_NAME` configuration parameter.

The main file used for the generation of the SIMS/SIMFr constructs is the Excel workbook containing the specification of the metadata structure. The name of this file is given by the `SIMS_XLSX_FILE_NAME` configuration parameter.

The code lists defining the possible values of certain SIMS/SIMFr attributes are contained in an Excel workbook names `CL_XLSX_FILE_NAME`. This workbook also contains the 'Thèmes' concept scheme used to classify the statistical operations.

## Organizations

Organizations lists and hierarchies are described in an Excel file named by the value of the `ORGANIZATIONS_XLSX_FILE_NAME` configuration parameter.

## Data

The main data input file is the M0 model in TriG format, whose name is given by the `M0_FILE_NAME` configuration parameter.

Additionally, the following files are used:

  * `FAMILY_THEMES_XLSX_FILE_NAME` specifies the correspondence between families and statistical themes;
  * `DDS_ID_TO_WEB4G_ID_FILE_NAME` and `M0_ID_TO_WEB4G_ID_FILE_NAME` are used for the computation of target operations and series URIs (see details [here](uri-mappings.md)).
