# Conversion des données M0 - Détermination des URI cibles

## Familles, séries, opérations et indicateurs

Le mapping des URI M0 vers les URI cibles pour les familles, séries, opérations et indicateurs est réalisé dans la classe `M0Converter` par les méthodes `createURIMappings` et `getIdURIFixedMappings`. Le processus est difficilement lisible du fait des multiples exceptions et cas particuliers qui ont été spécifiés au fur et à mesure des développements.

La structure générales des URI est :

`http://id.insee.fr/operations/{type}/s{entier}`

pour les familles, séries et opérations (type vaut respectivement `famille`, `serie` et `operation`), et :

`http://id.insee.fr/produits/indicateur/p{entier}`

pour les indicateurs.

`entier` est un numéro de séquence dont la méthode de détermination est décrite plus bas.

### Familles

Pour les familles, `entier` est le numéro d'ordre créé dans le modèle M0, qui démarre à 1. L'URI cible correspondante sera donc :

`http://id.insee.fr/operations/famille/s1`

### Series

Pour les séries, les URI cibles sont contraintes par la nécessité de ne pas modifier les identifiants de sources déjà publiées. Il faut donc tenir compte de mappings fixes définis a priori entre des ressources M0 et des URIs cibles. Ces mappings sont spécifiés dans un fichier `idSources.csv`, qui définit en fait la correspondence entre l'"identifiant DDS" de la série et le numéro de séquence cible, par exemple :

`FR-ENQ-BDF,1194`

Il faut donc dans un premier temps établir quelle resource M0 est associée à l'identifiant DDS afin de coupler son URI M0 avec l'URI cible. Pour reprendre l'exemple précédent :

`http://baseUri/series/serie/26   <->   http://id.insee.fr/operations/serie/s1194`

379 mappings sont ainsi définis dans le fichier `idSources.csv`, qui couvre les séries et les opérations mais n'est utilisé que pour les séries. Toutefois, seuls les mappings concernant les identifiants DDS commençant par FR- sont effectivement lus (190 lignes). De plus, le fichier est modifié avant d'être utilisé (voir membre `ddsToWeb4GIdMappings` de la classe `Configuration`). Trois mappings sont supprimés, concernant les iddentifiants DDS suivants :

ENQUETE-PATRIMOINE : la série 125 (identifiant cible 1282) est en fait mappée sur l'opération de 2014 (opération 158)
ENQ-SDF : la série 85 (identifiant cible 1267) est en fait mappée sur l'opération de 2001 (opération 189)
ENQ-TRAJECTOIRES-2008-TEO : la série 118 (identifiant ciblee 1276) est en fait mappée sur l'opération de 2008 (opération 199)


mais seulement 126 identifiants DDS sont valorisés dans le modèle M0 sur les séries (qui contient au total 165 séries).


En effet, le fichier peut également couvrir des mappings d'opérations. Parmi les 126 séries M0