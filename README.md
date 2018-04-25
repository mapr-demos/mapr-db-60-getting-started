# MapR-DB JSON: Getting Started

## Introduction

This getting started will show you how to work with MapR-DB JSON using:

* MapR-DB Shell to interact with JSON tables from the command line
* Apache Drill to run some analytics with SQL
* Java and the OJAI library to build operational application.
* Spark-DB connector to natively load / store RDDs to table.

You will learn how to optimize query using MapR-DB Secondary indexes.

**Prerequisites**

* MapR Converged Data Platform 6.0 with Apache Drill or [MapR Container for Developers](https://maprdocs.mapr.com/home/MapRContainerDevelopers/MapRContainerDevelopersOverview.html).
* JDK 8
* Maven 3.x

## Setting up MapR Container For Developers

MapR Container For Developers is a docker image that enables you to quickly deploy a MapR environment on your developer machine.

Installation, Setup and further information can be found [**here**](https://maprdocs.mapr.com/home/MapRContainerDevelopers/MapRContainerDevelopersOverview.html).

## Importing Data into MapR JSON Tables

The application use the Yelp Academy Dataset that contains a list of Business and User Reviews.

#### 1. Download the Yelp JSON Dataset from [here](https://www.yelp.com/dataset_challenge)

#### 2. Unarchive the dataset

```
$ tar xvf yelp_dataset_challenge_round9.tar
```

#### 3. Copy the dataset to MapR Cluster

##### 3a. Copy data to the node 
To do this, we first copy the file (say `business.json`) to the cluster node.
```
$ cd dataset
$ scp business.json review.json user.json root@<hostname>:/tmp/
```

If you are working with Developer Sandbox container, it's faster to transfer files using `docker cp` instead of `scp`, so copy the Yelp JSON files to the container like this:
```
$ docker cp business.json <container-id>:/tmp/
$ docker cp review.json <container-id>:/tmp/
$ docker cp user.json <container-id>:/tmp/
```
where "container-id" can be obtained from the `docker ps` command.

##### 3b. Copy data in to MapR-XD

There are two ways to put data into the MapR filesystem, called MapR-XD.

- Simple copy, if [**MapR NFS**](https://maprdocs.mapr.com/home/AdministratorGuide/AccessDataWithNFS.html) is installed and the cluster is mounted at `/mapr`. 
```
$ cp business.json review.json user.json /mapr/<cluster-name>/tmp/
```

- Run `hadoop fs` commands to put the data. The MapR Container For Developers does not include MapR NFS, so you will need to use this command to save the JSON files on the MapR filesystem.
```
hadoop fs -put business.json review.json user.json /tmp/
```

#### 4. Import the JSON documents into MapR-DB JSON tables

We will import the Yelp JSON documents into MapR-DB JSON tables using the [mapr importJSON](https://maprdocs.mapr.com/home/ReferenceGuide/mapr_importjson.html?hl=importjson) command. Note, the source file path specified in `mapr importJSON` must be a valid path in the MapR filesystem. 

```
$ mapr importJSON -idField business_id -src /tmp/business.json -dst /apps/business -mapreduce false
$ mapr importJSON -idField review_id -src /tmp/review.json -dst /apps/review -mapreduce false
$ mapr importJSON -idField user_id -src /tmp/user.json -dst /apps/user -mapreduce false
```

> Refer to [**Loading Documents in to JSON Tables**](https://maprdocs.mapr.com/home/MapR-DB/JSON_DB/loading_documents_into_json_tables.html) for more details.

You have now 3 JSON tables, let's query these tables using SQL with Apache Drill.

#### 5. Give user permissions / public permissions to allow read / write.
MapR-DB JSON tables allow us to set permissions in order restrict access to data. You can find more information [**here**](https://maprdocs.mapr.com/home/SecurityGuide/EnablingTableAuthorizations.html).

To make it simple for now we'll just set `readperm` and `writeperm` to `public` access. This means that anyone can read from table and write to the table. We'll apply this change to the `default` column family because all of the data in our case resides in default cf.

```
$ maprcli table cf edit -path /apps/business -cfname default -readperm p -writeperm p
$ maprcli table cf edit -path /apps/review -cfname default -readperm p -writeperm p
$ maprcli table cf edit -path /apps/user -cfname default -readperm p -writeperm p
```

> Refer to [**table cf edit command**](https://maprdocs.mapr.com/home/ReferenceGuide/table-cf-edit.html) for more details about the CLI command.


## MapR DB Shell

In this section you will learn how to use DB shell to query JSON table but also how to update a document with a new field.

As mapr user in a terminal enter the following commands to get familiar with the shell.

If you are running MapR as container, you can still use DB shell from the host machine.
```
$ mapr dbshell

maprdb mapr:> jsonoptions --pretty true --withtags false

maprdb mapr:> find /apps/user --limit 2

```

To learn more about the various commands, run `help`' or  `help <command>` , for example `help insert`.


**1. Retrieve one document using its id**

```
maprdb mapr:> find /apps/user --id l52TR2e4p9K4Z9zezyGKfg
```

**2. Add a projection to limit the number of fields**
```
maprdb mapr:> find /apps/user --id l52TR2e4p9K4Z9zezyGKfg --f name,average_stars,useful
```

**3. Insert a new Document**
```
maprdb mapr:> insert /apps/user --value '{"_id":"pdavis000222333", "name":"PDavis", "type" : "user", "yelping_since" : "2016-09-22", "fans" : 0, "support":"gold"}'
```

**4. Query document with Condition**
```
maprdb mapr:> find /apps/user --where '{ "$eq" : {"support":"gold"} }' --f _id,name,support
```

You will see later how to improve query performance with indices.


**5. Update a document**

Let's add the support status "gold" to an existing user, and find all users with support equals to gold

```
maprdb mapr:> update /apps/user --id l52TR2e4p9K4Z9zezyGKfg --m '{ "$set" : [ {"support" : "gold" } ] }'


maprdb mapr:> find /apps/user --where '{ "$eq" : {"support":"gold"} }' --f _id,name,support 

```

**6. Adding index to improve queries**

Let's now add indices to the user table.

In a terminal window:

```
$ maprcli table index add -path /apps/user -index idx_support -indexedfields 'support:1'
```

After waiting a minute for MapR-DB to add the index, open MapR-DB Shell and repeat the query to find all users with support equal to gold. This query should run much faster now.

```
maprdb mapr:> find /apps/user --where '{ "$eq" : {"support":"gold"} }' --f _id,name,support

```

**7. Using MapR DB Shell Query Syntax**

MapR-DB Shell has a complete JSON syntax to express the query including projection, condition, orderby, limit, skip. For example 

```
maprdb mapr:> find /apps/user --q ' { "$select" : ["_id","name","support"]  ,"$where" : {"$eq" : {"support":"gold"}}, "$limit" : 2 }'
```

Now that you are familiar with MapR-DB Shell, and you have seen the impact on index on query performance, let's now use Apache Drill to run some queries.

## Querying the data using Drill

We will use the `sqlline` utility to connect to Drill. You will find that utility in the Drill installation folder, such as /opt/mapr/drill/drill-1.11.0/bin/.  Here is how to connect to the Drill service running on a cluster node called "mapr60" where Zookeeper is running:

```
$ sqlline

sqlline> !connect jdbc:drill:zk=mapr60:5181
```

To connect to the Drill service running in the Developer Sandbox container, run `sqlline` like this:

```
$ sqlline -u jdbc:drill:zk=localhost:5181 -n mapr
```

You can execute the same query that you have used in the previous example using a simple SQL statement:

```sql
sqlline> select _id, name, support from dfs.`/apps/user` where support = 'gold';
```

Let's now use another table and query to learn more about MapR-DB JSON Secondary Index capabilities.

```sql
sqlline> select name, stars from dfs.`/apps/business` where stars = 5 order by name limit 10;
```

Execute the query multiple times and looks at the execution time.

**Simple Index**

You can now improve the performance of this query using an index. In a new terminal window run the following command to create an index on the `stars` field sorted in descending order (`-1`).

```
$ maprcli table index add -path /apps/business -index idx_stars -indexedfields 'stars:-1'
```

If you execute the query, multiple times, now it should be faster, since the index is used.

In this case Apache Drill is using the index to find all the stars equal to 5, then retrieve the name of the business from the document itseld (in the `/apps/user` table).

You can use the `EXPLAIN PLAN` command to see how drill is executing the query, is sqlline:

```
sqlline> explain plan for select name, stars from dfs.`/apps/business` where stars = 5;
```

The result of the explain plan will be a JSON document explaining the query plan, you can look in the `scanSpec` attribute that use the index if present. You can look at the following attributes in the `scanSpec` attribute:

* `secondaryIndex` equals true when a secondary index is created
* `startRow` and `stopRow` that show the index key used by the query
* `indexName` set to `idx_stars` that is the index used by the query


If you want to delete the index to compare the execution plan run the followind command as `mapr` in a terminal:

```
$ maprcli table index remove -path /apps/business -index idx_stars  
```

**Covering Queries**

In the index you have created in the previous step, we have specific the `-indexedfields` parameter to set the index key with the `stars` values. It is also possible to add some non indexed field to the index using the `-includedfields`. 

In this case the index includes some other fields that could be used by applications to allow the query to only access the index without having to retrieve any value from the JSON table itself.

Let's recreate the index on `stars` field and add the `name` to the list of included fields.

```
# if you have not deleted the index yet
$ maprcli table index remove -path /apps/business -index idx_stars  

$ maprcli table index add -path /apps/business -index idx_stars -indexedfields 'stars:-1' -includedfields 'name'
```

If you execute the query, multiple times, now it should be even faster than previous queries since Drill is doing a covered queries, only accessing the index.

In sqlline, run the explain plan with the new index:

```
sqlline> explain plan for select name, stars from dfs.`/apps/business` where stars = 5;
```


The main change in the explain plan compare to previous queries is:

* `includedFields` attribute that show the fields used by the queries.



## Working with OJAI

OJAI the Java API used to access MapR-DB JSON, leverages the same query engine than MapR-DB Shell and Apache Drill. The various examples use the `/apps/user` user table.

* `OJAI_001_YelpSimpleQuery` : Retrieve all the users with the `support` field equals to "gold". Note that the `support` field is a new field you are adding in the demonstration
* `OJAI_002_YelpInsertAndQuery` : Insert a new user in the table, with support set to gold and do the query. The query will not necessary returns the value from the index since it is indexed ascynchronously (see next example)
* `OJAI_003_YelpInsertAndQueryRYOW` : Insert a new user in the table, and do the query. This example use the "Tracking Writes" to be sure the query waits for the index to be updated
* `OJAI_004_YelpQueryAndDelete` : Delete the documents created in example 002 and 003.
* `OJAI_005_YelpComplexQuery` : Retrieve all restaurants that have take-outs and a rating greater than 3. Also calculates the percentage of restaurants that offer take out, out of all restaurants in Las Vegas that have take-out information available.

Here's an example run command:

```
java -cp ojai-examples/target/ojai-examples-1.0-SNAPSHOT.jar:./ojai-examples/target/* com.mapr.db.examples.OJAI_005_YelpComplexQuery
```

## Working with Drill-JDBC

JDBC is the standard interface for connecting Java applications to a database. The following example shows how to use JDBC to query a MapR-DB table from a Java application:

* `DRILL_001_YelpSimpleQuery` : Retrieve all businesses that have at least 100 reviews and a rating greater than 3.

Here's how to run it:
```
java -cp drill-examples/target/drill-examples-1.0-SNAPSHOT.jar:./drill-examples/target/* com.mapr.db.examples.DRILL_001_YelpSimpleQuery
```

## Working with Spark-DB Connector

The MapR-DB connector for Spark enables you to load and save Spark RDDs, DataFrames, and DataSets (which are all just tables of data in Spark) to MapR-DB. This is really a great connector, because it allows a distributed execution engine (Spark) to load and save data form a distributed database (MapR-DB).  The following examples shows how to use it in Scala:

* `SPARK_001_YelpQueryRDD` : Retrieve `city` with most restaurants rated higher than 3 stars.

The MapR docker container doesn't include Spark, but if you have a MapR cluster that does include Spark then you can run this example with spark-submit, like this:

```
/opt/mapr/spark/spark-*/bin/spark-submit --class com.mapr.db.examples.SPARK_001_YelpQueryRDD spark-examples/target/spark-examples-1.0-SNAPSHOT.jar
```

#### Run the sample applications

**Build and the project**

```
$ mvn clean install
```

This creates the following 3 jars.
- `drill-examples/target/drill-examples-1.0-SNAPSHOT.jar`
- `ojai-examples/target/ojai-examples-1.0-SNAPSHOT.jar`
- `spark-examples/target/spark-examples-1.0-SNAPSHOT.jar`

**Run the application**

Run one of the provided applications. Here are a few example run commands:

```
$ java -cp drill-examples/target/drill-examples-1.0-SNAPSHOT.jar:./drill-examples/target/* com.mapr.db.examples.DRILL_001_YelpSimpleQuery

$ java -cp ojai-examples/target/ojai-examples-1.0-SNAPSHOT.jar:./ojai-examples/target/* com.mapr.db.examples.OJAI_005_YelpComplexQuery
```

Change the name of the application to test the various examples.


## Conclusion

In this example you have learned how to:

* Use MapR-DB Shell to insert, update and query MapR-DB JSON Tables
* Add an index to improve queries performances
* Use Drill to query JSON Tables and see the impact of indexes
* Develop Java application with OJAI to query documents, and the impact of indexes on performance
* track you writes to be sure the index is updated when you query the table and a document has been inserted/updated
* Develop Drill-JDBC application in Java
* Develop Spark-DB application in Scala


You can also look at the following examples:

* [Ojai 2.0 Examples](https://github.com/mapr-demos/ojai-2-examples) to learn more about OJAI 2.0 features
* [MapR-DB Change Data Capture](https://github.com/mapr-demos/mapr-db-cdc-sample) to capture database events such as insert, update, delete and react to this events.





