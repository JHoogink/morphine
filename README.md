# MORPHINE

The [MORPHINE project](http://www.strategischprogrammarivm.nl/Wiskundige_modellering_van_ziekten#cat-8), 
funded by the [Strategic Programme of the RIVM](http://www.strategischprogrammarivm.nl), 
contains a simulation model that is implemented with the 
[EPIDEMES framework](https://github.com/krevelen/epidemes/) supported by [ZonMW](https://www.zonmw.nl/nl/onderzoek-resultaten/gezondheidsbescherming/programmas/project-detail/infectieziektebestrijding-2014-2017/from-individual-perception-of-vaccination-risks-and-benefits-to-compliance-costs-and-effects-of-vac/)
for micro-simulation of synthetic populations, which uses open-source 
tooling such as the [COALA binder](https://github.com/krevelen/coala-binder/releases) 
and the [DSOL simulator](http://www.simulation.tudelft.nl/). 

The MORPHINE synthetic population exhibits *vaccination hesitancy* behaviour 
and is used to study the vaccination dynamics found in empirical data gathered 
among the Dutch population (e.g. in the 
[PIENTER 2-project](http://www.rivm.nl/dsresource?objectid=66322c3e-9d28-466b-9c46-a0ba6adb6edf) 
and others) in order to inform policy advisors with respect to the Dutch 
[National Immunisation Programme](http://www.rivm.nl/en/Topics/N/National_Immunisation_Programme).

## Scenario

MORPHINE scenarios typically contain a number of **households**, each 
consisting of 1 parent (m/f) + 1 child (m/f), distributed evenly over the 
available **attractors** which represent the aggregate attitude of some subgroup
including those of e.g. social authorities (medical, religious, familial, etc.) 
and media influences. 

Household parents are embedded in a social network, 
sharing and updating their attitude towards vaccination each at their 
own frequency, connection degrees and peer activation. Furthermore, the extent 
of their flexibility depends on their 'stubbornness' as well as the appreciation
or weight attributed to the attitude of their designated *attractor*. 

In parallel, the parents must decide at **vaccination occassions** occuring at 
regular intervals whether their child should be vaccinated already, or to 
remain hesitant. This decision is based on parameters inspired by the 
**4C model**, a clustering of vaccination hesitancy behavior drivers into four 
categories by [Betsch et al. (2015)](http://dx.doi.org/10.1177/2372732215600716):

  - **CONFIDENCE**: perceived utility of vaccination
  - **COMPLACENCY**: perceived utility of the disease, due to e.g. 
  conformance to protestantist (Luther), homeopathic (Hahneman), or 
  anthroposophic (Steiner) beliefs, or "the idea that it is selfish-rational to 
  omit vaccination as long as enough other individuals are vaccinated to keep 
  the infection risk low" thus leading complacent people to "passively omit" 
  (Betsch et al., 2015)
  - **CONVENIENCE**: a level that depends on the vaccionation occasion at hand, 
  regarding factors like *utility* ("physical availability, affordability and 
  willingness-to-pay"), *proximity* ("geographical accessibility"), *clarity* 
  ("ability to understand, language and health literacy") and *affinity* 
  ("appeal of immunization service") (MacDonald et al., 2015); and
  - **CALCULATION**: the extent to which information is actively searched to 
  replace any pre-existing attitude. With a "strong pre-existing attitude, 
  extensive search for pros and cons of vaccination." Furthermore, "any 
  additional information about costs or (social) benefits will influence the 
  decision because it is included in and updates the utility calculation" 
  (Betsch et al., 2015). Vice versa, low calculation entails a strong 
  pre-existing attitude, low active information search (Fischer et al., 2011)

# Getting started

## 1. Install

### A. Ready-made
Download and unzip the [latest release](https://github.com/JHoogink/morphine/releases/latest).

### B. Build from source
Install clients for the prerequisite tools:

* Git versioning system (e.g. [git-scm](https://git-scm.com/downloads/))
* Java development kit (e.g. [Oracle's SE JDK](http://www.oracle.com/technetwork/java/javase/downloads/))
* [Apache Maven](https://maven.apache.org/) build manager

Although `git` and `mvn` are usually embedded within modern 
[IDEs](https://www.wikiwand.com/en/Integrated_development_environment) 
like [Eclipse](http://www.eclipse.org/), [Netbeans](https://netbeans.org/), or 
[IntelliJ IDEA](https://www.jetbrains.com/idea/), you may wish to install 
them into your command shell's `PATH` environment variable.

Clone the repository (switched to 'master' branch) and compile the uber-jar
```
> git clone git@github.com:JHoogink/morphine.git
> mvn install -f morphine/episim-morphine
```

Create the configuration files for both logging and simulation:
```
> cd ./morphine/episim-morphine/dist
> copy log4j2.dist.yaml   log4j2.yaml
> copy morphine.dist.yaml morphine.yaml
```

## 2. Configure
The scenario configuration is processed by [`nl.rivm.cib.morphine.household.HHConfig`](https://github.com/JHoogink/morphine/blob/v1.0/episim-morphine/src/main/java/nl/rivm/cib/morphine/household/HHConfig.java). 
You can refer to the code to see default values, or to the distributed sample 
configuration [`morphine.dist.yaml`](https://github.com/JHoogink/morphine/blob/v1.0/episim-morphine/dist/morphine.dist.yaml)
for some simple parameter settings.

To configure the logger, please refer to the 
[Log4j 2.0 YAML docs](https://logging.apache.org/log4j/2.0/manual/configuration.html#Configuration_with_YAML)
or to [`log4j2.dist.yaml`](https://github.com/JHoogink/morphine/blob/v1.0/episim-morphine/dist/log4j2.dist.yaml) 
example for some default settings.

## 3. Run

### A. Single replication
Call the executable jar directly, optionally overriding any setup parameter 
values (as defined in [`nl.rivm.cib.morphine.household.HHConfig`](https://github.com/JHoogink/morphine/blob/v1.0/episim-morphine/src/main/java/nl/rivm/cib/morphine/household/HHConfig.java)):
```
> java -jar ./morphine-full-1.0.jar [key1=val1 [key2=val2 [...]]]
```

### B. Batch mode
The basic shell script [`morphine.bat`](https://github.com/JHoogink/morphine/blob/v1.0/episim-morphine/dist/morphine.bat) 
repeats a call to run the morphine 
scenario *n* times, and also provides an example on how to override some 
configuration settings between replications so as to run entire experiments.

To run multiple iterations of the setup configuration in `./morphine.yaml`:
```
> morphine [number-of-replications]
```

Unless `morphine.replication.random-seed` is configured with some integer 
(for instance to reproduce an earlier run), each iteration takes a 
new seed value for the pseudorandom number generator, which is exported in 
the `RUNS.SEED` column for later replication if required.

Feel free to create variations of the `morphine.bat` script, and iterate 
over your own choice of (independent) setup parameter settings. *NOTE*: `float` 
or `string`-type parameter values may require a slightly 
[different notation](https://stackoverflow.com/a/3439978).

# Results Data Structure
Statistics are exported using [JPA](https://www.wikiwand.com/en/Java_Persistence_API) 
into two [SQL](https://www.wikiwand.com/en/SQL) tables: `RUNS` and `HOUSEHOLDS` 
using their respective [Data Access Objects](https://www.wikiwand.com/en/Data_access_object) (DAOs).

## Setup configurations
Populated using [`nl.rivm.cib.morphine.dao.HHConfigDao`](https://github.com/JHoogink/morphine/blob/master/episim-morphine/src/main/java/nl/rivm/cib/morphine/dao/HHConfigDao.java), 
the `RUNS` table has the following columns:

  - `PK`: the primary key used for reference across the database
  - `CREATED_TS`: the database timestamp of this record's creation
  - `CONTEXT`: the [UUID](https://www.wikiwand.com/en/Universally_unique_identifier) of the simulation run
  - `SETUP`: the name of this setup, configured (in `morphine.replication.setup-name`)
  - `SEED`: the pseudorandom number generator seed, generated or configured (in `morphine.replication.random-seed`)
  - `HASH`: the MD5 hash of the effective configuration (ignoring comments)
  - `JSON`: the effective setup configuration as [JSON](http://json.org/) tree
  - `YAML`: the effective setup configuration as [YAML](http://yaml.org/) tree

## Individual and household time lines
Populated using [`nl.rivm.cib.morphine.dao.HHStatisticsDao`](https://github.com/JHoogink/morphine/blob/v1.0/episim-morphine/src/main/java/nl/rivm/cib/morphine/dao/HHStatisticsDao.java), 
the `HOUSEHOLDS` table has the following columns:

  - `PK`: the primary key of this record
  - `CONFIG_PK`: foreign key of the respective `RUNS` record
  - `SEQ`: the statistics export sequence iteration
  - `INDEX`: the household (contact network row) index (0..A+N/2)
  - `HH`: household identifier (may be replaced to to death/birth or migration)
  - `HH_DT_DAYS`: the number of days between social impressions on this household
  - `ATTRACTOR_REF`: household designated *attractor* (social network row) index (0..A)
  - `REFERENT_AGE`:  household referent (parent) current age (years)
  - `REFERENT_STATUS`: household referent (parent) status (see [`nl.rivm.cib.morphine.household.HHMemberStatus`](https://github.com/JHoogink/morphine/blob/v1.0/episim-morphine/src/main/java/nl/rivm/cib/morphine/household/HHMemberStatus.java))
  - `REFERENT_MALE`: household referent (parent) masculine gender (true/false)
  - `CHILD1_AGE`: household child current age (years)
  - `CHILD1_STATUS`: household child status (see [`nl.rivm.cib.morphine.household.HHMemberStatus`](https://github.com/JHoogink/morphine/blob/v1.0/episim-morphine/src/main/java/nl/rivm/cib/morphine/household/HHMemberStatus.java))
  - `CHILD1_MALE`: household child masculine gender (true/false)
  - `CONFIDENCE`: household (referent) confidence level (vaccine utility in 0..1)
  - `COMPLACENCY`: household (referent) complacency level (disease utility in 0..1)
  - `CALCULATION`: household (referent) calculation level (information search in 0..1)
  - `ATTITUDE`: household (referent) current positive attitude (true/false)
  - `SOCIAL_ASSORTATIVITY`: fraction of peers of the same social/attractor group (0..1)
  - `SOCIAL_NETWORK_SIZE`: number of social peers that may apply peer pressure (0..N/2)
  - `IMPRESS_F_POSITIVE`: fraction of social peer attitudes currently positive (0..1)
  - `IMPRESS_DT_DAYS`: number of days between peer pressure contact
  - `IMPRESS_N_ROUNDS`: number of times the household updated its attitude
  - `IMPRESS_N_PEERS`: number of times any peer applied pressure so far
  - `IMPRESS_N_BY_PEER`: number of times each peer applied pressure so far, as JSON-format mapping `{"peer-index":N, ...}` (useful for reconstructing the social network activation dynamics using e.g. the [jsonlite](https://cran.r-project.org/web/packages/jsonlite/index.html) R-package) 
  - `IMPRESS_W_ASSORT`: weight of in-group peer attitudes (of same attractor)
  - `IMPRESS_W_DISSORT`: weight of out-group peer attitudes (of other attractors)
  - `IMPRESS_W_SELF`: maximum weight of own current opinion (stubbornness)
  - `IMPRESS_W_ATTRACTOR`: maximum weight of attractor's attitude (conformance)

# Analysis
The default setup will export the current statistics at virtual time instants matching the 
[CRON expression](http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html) 
configured in `morphine.replication.statistics.recurrence`. For instance: 

  - `0 0 0 1,15 * ? *` matches midnight (`0s 0m 0h`) on every 1st and 15th day of 
  any month (`1,15 *`), of any year (`*`)
  - `0 30 9,14 ? * MON-FRI *` matches 9:30am and 2:30pm (`0s 30m 9,14h`) on any date (`? *`) 
  every weekday (`MON-FRI`) of any year (`*`)
  - `1 0 0 L-2  * ? *` matches the first second after midnight (`1s 0m 0h`) at the 
  second-to-last day of any month (`L-2 *`), whatever the weekday (`?`), of any 
  year (`*`).

## Inspection via web-based H2 Console
One straight-forward approach to inspecting the results is to use SQL directly, for instance in the [H2 database web console](http://www.h2database.com/html/tutorial.html#console_settings).
```
> h2_console
```

In the web form that now becomes available (typically at 
[`http://localhost:8082/`](http://localhost:8082/)) provide the 
[JDBC](https://www.wikiwand.com/en/Java_Database_Connectivity) connection details:

- JDBC-URL: `jdbc:h2:./morphine;AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=FALSE`
- Username: `sa`
- Password: `sa`

Now the console is ready to perform your SQL queries. For example:

- left-click the `HOUSEHOLDS` table name (in the left-side explorer panel) to generate a default browse query (`SELECT * FROM HOUSEHOLDS`); and
- left-click the `RUN` tab/button (in the right-hand side query editor) to fetch the first 1000 records or rows.

For instance, the query `SHOW COLUMNS FROM HOUSEHOLDS` should yield something like:

| FIELD | TYPE | NULL | KEY | DEFAULT |  
| ----- | ---- | ---- | --- | ------- |
| PK | INTEGER(10) | NO | PRI | NULL | 
| ATTITUDE | BOOLEAN(1) | YES |  | NULL | 
| ATTRACTOR_REF | VARCHAR(255) | NO |  | NULL | 
| CALCULATION | DECIMAL(15) | NO |  | NULL | 
| CHILD1_AGE | DECIMAL(10) | YES |  | NULL | 
| CHILD1_MALE | BOOLEAN(1) | YES |  | NULL | 
| CHILD1_STATUS | VARCHAR(255) | YES |  | NULL | 
| COMPLACENCY | DECIMAL(15) | NO |  | NULL | 
| CONFIDENCE | DECIMAL(15) | NO |  | NULL | 
| HH | BIGINT(19) | NO |  | NULL | 
| IMPRESS_F_POSITIVE | DECIMAL(15) | YES |  | NULL | 
| IMPRESS_N_BY_PEER | CLOB(2147483647) | NO |  | NULL | 
| IMPRESS_N_PEERS | INTEGER(10) | NO |  | NULL | 
| IMPRESS_N_ROUNDS | INTEGER(10) | NO |  | NULL | 
| IMPRESS_DT_DAYS | DECIMAL(15) | NO |  | NULL | 
| IMPRESS_W_ASSORT | DECIMAL(15) | NO |  | NULL | 
| IMPRESS_W_ATTRACTOR | DECIMAL(15) | NO |  | NULL | 
| IMPRESS_W_DISSORT | DECIMAL(15) | NO |  | NULL | 
| IMPRESS_W_SELF | DECIMAL(15) | NO |  | NULL | 
| HH_DT_DAYS | DECIMAL(15) | NO |  | NULL | 
| INDEX	| BIGINT(19) | NO |  | NULL | 
| REFERENT_AGE | DECIMAL(10 | NO |  | NULL | 
| REFERENT_MALE | BOOLEAN(1) | NO |  | NULL | 
| REFERENT_STATUS | VARCHAR(255) | NO |  | NULL | 
| SEQ | INTEGER(10) | NO |  | NULL | 
| SOCIAL_ASSORTATIVITY | DECIMAL(15) | NO |  | NULL | 
| SOCIAL_NETWORK_SIZE	| INTEGER(10)	| NO |  | NULL | 
| CONFIG_PK	| INTEGER(10)	| NO | | NULL | 

## R import and statistics
The simulation results can be easily imported into your R session.

First, install and load the required packages, e.g. 
[`RJDBC`](https://cran.r-project.org/web/packages/RJDBC/) and 
[`data.table`](https://cran.r-project.org/web/packages/data.table/)
(see also [this cheat sheet](https://www.datacamp.com/community/tutorials/data-table-cheat-sheet)).
```r
install.packages( c( 'RJDBC','data.table' ), dep=TRUE )
require( RJDBC )
require( data.table )
```

Next, after editing the below URLs to match your environment, connect to the 
(H2 or other) SQL-compatible database via JDBC (a JDBC driver for H2 is 
embedded in the uber-jar):
```r
h2dbFile <- 'path/to/morphine/dist'
drv <- RJDBC::JDBC( driverClass='org.h2.Driver', identifier.quote="`"
  , classPath='path/to/morphine/dist/morphine-full-1.0.jar' )
conn <- RJDBC::dbConnect( drv
  , paste0('jdbc:h2:', h2dbFile, ';AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=FALSE')
  , 'sa', 'sa' ) 
```

Finally, check the connection, import the data, disconnect, and check contents:
```r
> RJDBC::dbListTables( conn ) 
> DT <- data.table( RJDBC::dbGetQuery( conn, "select * from HOUSEHOLDS" ) )
> RJDBC::dbDisconnect( conn )
> str( DT )
```
Data table `DT` now contains all records for each households `INDEX` (0..A+N/2) in each statistics export iteration `SEQ` (0...) of each simulation run `CONFIG_PK` (0..). Thus you should get something like:
```
Classes ‘data.table’ and 'data.frame':	6048 obs. of  28 variables:
 $ PK                  : num  2 3 4 5 6 7 8 9 10 11 ...
 $ ATTITUDE            : chr  "FALSE" "FALSE" "FALSE" "TRUE" ...
 $ ATTRACTOR_REF       : chr  "rel-alt" "rel-reg" "sec-alt" "sec-reg" ...
 $ CALCULATION         : num  0 0 0 0 0.5 0.5 0.5 0.5 0.5 0.5 ...
 $ CHILD1_AGE          : num  24.68 24.68 24.68 24.68 4.68 ...
 $ CHILD1_MALE         : chr  "TRUE" "TRUE" "TRUE" "TRUE" ...
 $ CHILD1_STATUS       : chr  "susceptible" "susceptible" "susceptible" "susceptible" ...
 $ COMPLACENCY         : num  0.5 1 1 0 0.578 0.467 0.319 0.375 0.895 0.28 ...
 $ CONFIDENCE          : num  0.5 0.5 0 1 0.451 0.563 0.613 0.806 0.367 0.743 ...
 $ HH                  : num  0 1 2 3 4 5 6 7 8 9 ...
 $ IMPRESS_F_POSITIVE  : num  0 0 0 0 0.667 ...
 $ IMPRESS_N_BY_PEER   : chr  "{}" "{}" "{}" "{}" ...
 $ IMPRESS_N_PEERS     : num  0 0 0 0 0 0 0 0 0 0 ...
 $ IMPRESS_N_ROUNDS    : num  0 0 0 0 1 1 1 1 1 1 ...
 $ IMPRESS_DT_DAYS     : num  0 0 0 0 0.154 ...
 $ IMPRESS_W_ASSORT    : num  1 1 1 1 23 23 22 23 13 12 ...
 $ IMPRESS_W_ATTRACTOR : num  1 1 1 1 27 26 27 27 16 17 ...
 $ IMPRESS_W_DISSORT   : num  1 1 1 1 4 3 5 4 3 5 ...
 $ IMPRESS_W_SELF      : num  100 100 100 100 2700 2600 2700 2700 1600 1700 ...
 $ HH_DT_DAYS          : num  27 27 27 27 27 ...
 $ INDEX               : num  0 1 2 3 4 5 6 7 8 9 ...
 $ REFERENT_AGE        : num  24.7 24.7 24.7 24.7 24.7 ...
 $ REFERENT_MALE       : chr  "TRUE" "TRUE" "TRUE" "TRUE" ...
 $ REFERENT_STATUS     : chr  "susceptible" "susceptible" "susceptible" "susceptible" ...
 $ SEQ                 : num  0 0 0 0 0 0 0 0 0 0 ...
 $ SOCIAL_ASSORTATIVITY: num  0 0 0 0 0.852 ...
 $ SOCIAL_NETWORK_SIZE : num  126 128 128 126 27 26 27 27 16 17 ...
 $ CONFIG_PK           : num  1 1 1 1 1 1 1 1 1 1 ...
```

Note that all observations containing the lowest values in `INDEX` (and conversely `HH`) are for the attractors as per the default configuration [`morphine.dist.yaml`](https://github.com/JHoogink/morphine/blob/v1.0/episim-morphine/dist/morphine.dist.yaml#L62-#L63). In this case, the first 4 indices (0..3) are constant throughout all of the run's statistics iterations (uniquely identifiable with `CONFIG_PK` and `SEQ` respectively) for the 4 attractors named `rel-alt`, `rel-reg`, `sec-alt` and `sec-reg`.

Unfortunately, `RJDBC` translates the `BOOLEAN` columns (`ATTITUDE`, `CHILD1_MALE`, `REFERENT_MALE`) to `character` values (`TRUE` and `FALSE`) rather than integers (`1` and `0` respectively).
