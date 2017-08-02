# MORPHINE

The MORPHINE simulation scenario involves a synthetic population, an 
individual-based or micro-simulation model common in social simulation studies, 
that exhibits *vaccination hesitancy* behaviour. This model is used to calibrate 
the vaccination dynamics to empirical data gathered among the Dutch population 
in studies like the [PIENTER 2-project](http://www.rivm.nl/dsresource?objectid=66322c3e-9d28-466b-9c46-a0ba6adb6edf) 
and others in order to inform advisors about national immunization policies.

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

  - *CONFIDENCE*: perceived utility of vaccination
  - *COMPLACENCY*: perceived utility of the disease, due to e.g. 
  conformance to protestantist (Luther), homeopathic (Hahneman), or 
  anthroposophic (Steiner) beliefs, or "the idea that it is selfish-rational to 
  omit vaccination as long as enough other individuals are vaccinated to keep 
  the infection risk low" thus leading complacent people to "passively omit" 
  (Betsch et al., 2015)
  - *CONVENIENCE*: a level that depends on the vaccionation occasion at hand, 
  regarding factors like *utility* ("physical availability, affordability and 
  willingness-to-pay"), *proximity* ("geographical accessibility"), *clarity* 
  ("ability to understand, language and health literacy") and *affinity* 
  ("appeal of immunization service") (MacDonald et al., 2015); and
  - *CALCULATION*: the extent to which information is actively searched to 
  replace any pre-existing attitude. With a "strong pre-existing attitude, 
  extensive search for pros and cons of vaccination." Furthermore, "any 
  additional information about costs or (social) benefits will influence the 
  decision because it is included in and updates the utility calculation" 
  (Betsch et al., 2015). Vice versa, low calculation entails a strong 
  pre-existing attitude, low active information search (Fischer et al., 2011)

## Acknowledgement

The [MORPHINE project](http://www.strategischprogrammarivm.nl/Wiskundige_modellering_van_ziekten#cat-8), 
funded by the [Strategic Programme of the RIVM](http://www.strategischprogrammarivm.nl).
The simulation model is implemented on the 
[EPIDEMES framework](https://github.com/krevelen/epidemes/), 
which applies open-source tooling including the 
[COALA binder](https://github.com/krevelen/coala-binder) 
and the [DSOL simulator](http://www.simulation.tudelft.nl/). 

# Getting started

Pre-requisites:

* Git client installed
* Maven 3+ installed

## Build

```
# clone the repository and switch to 'master' branch
git clone git@github.com:JHoogink/morphine.git
# build the uber-jar
cd episim-morphine\
mvn install
# create custom configuration 
cd dist\
copy .\log4j.dist.yaml .\log4j.yaml
copy .\morphine.dist.yaml .\morphine.yaml
```

## Run

```
# run the custom setup (default is 1x only)
.\morphine
# run 3 iterations of the custom setup, each with a new morphine.replication.random-seed
.\morphine 3
```

# Analysis

The default setup will export the current statistics at instants matching the [CRON expression](http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html) configured in `morphine.replication.statistics.recurrence`. For instance `1 0 0 L-2 * ? *` means: the first second after midnight (`1s 0m 0h`) at the second-to-last day of any month (`L-2 *`), whatever the weekday (`?`), of any year (`*`).

The exported statistics includes two tables populated using their respective Data Access Objects:

  1. `RUNS` populated using `nl.rivm.cib.morphine.dao.HHConfigDao` with columns:
    - `PK`: the primary key used for reference across the database
    - `CREATED_TS`: the timestamp of this record's creation
    - `CONTEXT`: the [UUID](https://www.wikiwand.com/en/Universally_unique_identifier) of the setup
    - `SETUP`: the name of this setup, configured in `morphine.replication.setup-name`
    - `SEED`: the seed of the pseudorandom number generator, possibly configured in `morphine.replication.random-seed`
    - `HASH`: the MD5 hash of the effective configuration (ignoring comments)
    - `JSON`: the effective setup configuration as [JSON](http://json.org/) tree
    - `YAML`: the effective setup configuration as [YAML](http://yaml.org/) tree
  2. `HOUSEHOLDS` populated using `nl.rivm.cib.morphine.dao.HHStatisticsDao` with columns:
    - `PK`: the primary key of this record
    - 'CONFIG_PK': foreign key of the respective `RUNS` record
    - `SEQ`: the statistics export sequence iteration, depends on the pattern configured in `morphine.replication.statistics.recurrence`
    - `INDEX`: the household (contact network row) index
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
    - `SOCIAL_NETWORK_SIZE`: number of social peers that may apply peer pressure
    - `IMPRESS_F_POSITIVE`: fraction of social peer attitudes currently positive (0..1)
    - `IMPRESS_DT_DAYS`: number of days between peer pressure contact
    - `IMPRESS_N_ROUNDS`: number of times the household updated its attitude
    - `IMPRESS_N_PEERS`: number of times any peer applied pressure so far
    - `IMPRESS_N_BY_PEER`: number of times each peer applied pressured so far
    - `IMPRESS_W_ASSORT`: weight of in-group peer attitudes (of same attractor)
    - `IMPRESS_W_DISSORT`: weight of out-group peer attitudes (of other attractor)
    - `IMPRESS_W_SELF`: maximum weight of own current opinion (stubbornness)
    - `IMPRESS_W_ATTRACTOR`: maximum weight of attractor's attitude (conformance)

## Web-based inspection
The simplest option to inspect the results directtly is to use the [H2 database web console](http://www.h2database.com/html/tutorial.html#console_settings).

```
# inspect results via H2 web server console
.\h2_console
```

then enter:

- URL: `jdbc:h2:./morphine;AUTO_SERVER=TRUE`
- User: `sa`
- Password: `sa`

## R import and statistics
Another option is to import the results to your R session using e.g. [RJDBC](https://cran.r-project.org/web/packages/RJDBC/) and [data.table](https://cran.r-project.org/web/packages/data.table/).

```
# edit the base URL to match your environment
baseUrl <- 'path/to/morphine/episim-morphine/dist'
# install and load packages
install.packages(c('RJDBC','data.table'),dep=TRUE)
require(RJDBC)
require(data.table)
# connect to the SQL database
drv <- RJDBC::JDBC( driverClass='org.h2.Driver' 
  , classPath=paste0(baseUrl,'/morphine-full-1.0.jar')
  , identifier.quote="`"
  )
conn <- RJDBC::dbConnect(drv
  , paste0('jdbc:h2:', baseUrl, '/morphine;AUTO_SERVER=TRUE')
  , 'sa', 'sa' ) 
# check the connection, import the data, and disconnect
RJDBC::dbListTables(conn) 
MORPHINE <- data.table(dbGetQuery(conn, "select * from HOUSEHOLDS"))
dbDisconnect(conn)
```