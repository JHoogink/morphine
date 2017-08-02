# MORPHINE

The [MORPHINE project](http://www.strategischprogrammarivm.nl/Wiskundige_modellering_van_ziekten#cat-8), 
funded by the [Strategic Programme of the RIVM](http://www.strategischprogrammarivm.nl), 
contains a simulation model that is implemented on the 
[EPIDEMES framework](https://github.com/krevelen/epidemes/), 
for micro-simulation of synthetic populations. EPIDEMESE applies open-source 
tooling including the [COALA binder](https://github.com/krevelen/coala-binder) 
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

Prerequisites, typically already embedded with IDE's like [Eclipse](), [Netbeans](), or [IntelliJ]():

* `git` (e.g. [git-scm](https://git-scm.com/downloads))
* `mvn` (e.g. [Apache Maven](https://maven.apache.org/))

## Build from source

Clone the repository and switch to 'master' branch
```
git clone git@github.com:JHoogink/morphine.git
```

Build the uber-jar
```
cd episim-morphine\
mvn install
```

Create custom configuration 
```
cd dist\
copy .\log4j.dist.yaml .\log4j.yaml
copy .\morphine.dist.yaml .\morphine.yaml
```

## Run the uber-jar
The basic shell script `morphine.bat` repeats a call to run the morphine 
scenario *n* times, and also provides an example on how to override some 
configuration settings between replications so as to run entire experiments.

### Usage example 
Run the custom setup 1x only using the shell script:
```
>epidemes-morphine\dist\morphine
```
or call the executable jar directly:
```
>java -jar epidemes-morphine\dist\morphine-full-1.0.jar
```

### Batch mode
Run 3 iterations of the custom setup, each with a new `RUNS.SEED` value
(unless `morphine.replication.random-seed` is configured with some integer)
```
>epidemes-morphine\dist\morphine 3
```

# Analysis
The default setup will export the current statistics at instants matching the 
[CRON expression](http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html) 
configured in `morphine.replication.statistics.recurrence`. For instance: 
`1 0 0 L-2 * ? *` means: the first second after midnight (`1s 0m 0h`) at the 
second-to-last day of any month (`L-2 *`), whatever the weekday (`?`), of any 
year (`*`).

## Exportable statistics
Statistics are exported using [JPA](https://www.wikiwand.com/en/Java_Persistence_API) 
into two tables: `RUNS` and `HOUSEHOLDS` using their respective 
[Data Access Objects](https://www.wikiwand.com/en/Data_access_object).

### RUNS Table: Setup configurations
Populated using `nl.rivm.cib.morphine.dao.HHConfigDao`, the `RUNS` table has 
the following columns:

  - `PK`: the primary key used for reference across the database
  - `CREATED_TS`: the timestamp of this record's creation
  - `CONTEXT`: the [UUID](https://www.wikiwand.com/en/Universally_unique_identifier) of the setup
  - `SETUP`: the name of this setup, configured in `morphine.replication.setup-name`
  - `SEED`: the seed of the pseudorandom number generator, possibly configured in `morphine.replication.random-seed`
  - `HASH`: the MD5 hash of the effective configuration (ignoring comments)
  - `JSON`: the effective setup configuration as [JSON](http://json.org/) tree
  - `YAML`: the effective setup configuration as [YAML](http://yaml.org/) tree

## HOUSEHOLDS Table: Individual time lines
Populated using `nl.rivm.cib.morphine.dao.HHStatisticsDao` the `HOUSEHOLDS` 
table has the following columns:

  - `PK`: the primary key of this record
  - 'CONFIG_PK': foreign key of the respective `RUNS` record
  - `SEQ`: the statistics export sequence iteration, depends on the pattern configured in `morphine.replication.statistics.recurrence`
  - `INDEX`: the household (contact network row) index (0..N/2)
  - `HH`: household identifier (may be replaced to to death/birth or migration)
  - `HH_DT_DAYS`: the number of days between social impressions on this household
  - `ATTRACTOR_REF`: household designated *attractor* (social network row) index 
  - `REFERENT_AGE`:  household referent (parent) current age (years)
  - `REFERENT_STATUS`: household referent (parent) status (vaccinated, susceptible, ...)
  - `REFERENT_MALE`: household referent (parent) masculine gender (true/false)
  - `CHILD1_AGE`: household child current age (years)
  - `CHILD1_STATUS`: household child status (vaccinated, susceptible, ...)
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
  - `IMPRESS_N_BY_PEER`: number of times each peer applied pressured so far
  - `IMPRESS_W_ASSORT`: weight of in-group peer attitudes (of same attractor)
  - `IMPRESS_W_DISSORT`: weight of out-group peer attitudes (of other attractor)
  - `IMPRESS_W_SELF`: maximum weight of own current opinion (stubbornness)
  - `IMPRESS_W_ATTRACTOR`: maximum weight of attractor's attitude (conformance)

## SQL inspection via Web-based H2 Console
The simplest option to inspect the results directtly is to use the [H2 database web console](http://www.h2database.com/html/tutorial.html#console_settings).

```
>epidemes-morphine\dist\h2_console
```

In the web form that now becomes available typically at 
`http://192.168.56.1:8082/` provide the JDBC connection details:

- URL: `jdbc:h2:./morphine;AUTO_SERVER=TRUE`
- User: `sa`
- Password: `sa`

## R import and statistics
Another option is to import the results to your R session.

First, edit the base URL to match your environment, e.g.:
```
baseUrl <- 'C:/path/to/morphine/episim-morphine/dist/'
dbName <- 'morphine'
```

Then install and load the required packages, e.g. 
[`RJDBC`](https://cran.r-project.org/web/packages/RJDBC/) and 
[`data.table`](https://cran.r-project.org/web/packages/data.table/).
```
install.packages(c('RJDBC','data.table'),dep=TRUE)
require(RJDBC)
require(data.table)
```

Connect to the SQL database `.../morphine` via JDBC:
```
drv <- RJDBC::JDBC( driverClass='org.h2.Driver' 
  , classPath=paste0(baseUrl,'morphine-full-1.0.jar')
  , identifier.quote="`"
  )
conn <- RJDBC::dbConnect(drv
  , paste0('jdbc:h2:', baseUrl, dbName, ';AUTO_SERVER=TRUE')
  , 'sa', 'sa' ) 
```

Finally, check the connection, import the data, and disconnect
```
RJDBC::dbListTables(conn) 
MORPHINE <- data.table(dbGetQuery(conn, "select * from HOUSEHOLDS"))
dbDisconnect(conn)
```