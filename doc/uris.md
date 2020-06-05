# URIs


## Metadata structure definition

### Metadata structure definition

The URI for the SIMSFr Metadata Structure Definition is http://id.insee.fr/qualite/simsv2fr/msd

### Report structure

The URI for the SIMSFr Report Structure is http://id.insee.fr/qualite/simsv2fr/reportStructure

### MetadataAttributeSpecification

SIMS: http://ec.europa.eu/eurostat/simsv2/attributeSpecification/{code}, where code is the SIMS attribute code (e.g. S.13.3.2)

SIMSFr: http://id.insee.fr/qualite/simsv2fr/attribut/{code}, where code is the SIMSFr attribute code (e.g. I.6.3)

### MetadataAttributeProperty

SIMS: http://ec.europa.eu/eurostat/simsv2/attribute/{code}, where code is the SIMS attribute code

SIMSFr: http://id.insee.fr/qualite/simsv2fr/attribut/{code}, where code is the SIMSFr attribute code

## Metadata set

MetadataReport: http://id.insee.fr/qualite/rapport/{number}, where number is the M0 documentation number (e.g. 1503)
ReportedAttribute: http://id.insee.fr/qualite/attribut/{number}/{code}, where number is the M0 documentation number (e.g. 1503) and code is the SIMS attribute code (e.g. S.13.3.2)

DCTypes:Text: http://id.insee.fr/qualite/attribut/{number}/{code}/texte