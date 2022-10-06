# Dockerized ArangoDB Cluster with AQL Code Examples

An environment to build a simple ArangoDB cluster with one agency node, two coordinator nodes and three DBServer nodes. Borrowed and adapted based on <https://github.com/Korov/Docker/blob/master/arangodb/cluster/docker-compose.yaml>.

## Intro - What's in this project?

I present a couple of sub-projects under the `/project` folder.

- `v6` (using ArangoDB Java Driver version 6.16.1)
  - [`aql-tutorial`](./project/v6/aql-tutorial/src/main/java/com/jasonqiu/demo/Main.java)
    - a tutorial of AQL in combination with Java Driver
  - [`graph-data-import`](./project/v6/graph-data-import/src/main/java/com/jasonqiu/demo/Main.java)
    - an example to show how to import graph data from CSV files to ArangoDB with Java Driver
    - an [example](./project/v6/graph-data-import/logs/example-query.md) of a cycle detection query and its profiling results
  - [`java-driver-tutorial`](./project/v6/java-driver-tutorial/src/main/java/com/jasonqiu/demo/Main.java)
    - directly from the tutorial of Java Driver from ArangoDB docs [Docs](https://www.arangodb.com/docs/stable/drivers/java-tutorial.html)[GitHub](`https://github.com/arangodb/arangodb-java-driver-quickstart`)
- `v7` (using ArangoDB Java Driver version 7.0.0-SNAPSHOT)
  - [`java-driver-tutorial`](./project/v7/java-driver-tutorial/src/main/java/com/jasonqiu/demo/Main.java) (same as above)

### AQL Tutorial

As a highlight, the content of the AQL tutorial is listed below.

1. `INSERT`, "**C**reate a doc"

- **[Query 1]** insert a vertex
- **[Query 2]** return the inserted document, `NEW`
- **[Query 3]** insert an edge

2. `DOCUMENT`, "**R**ead a doc"

- **[Query 4]** read from "the result array of docs" (normally)
- **[Query 5]** read from an array of "the result array of docs" (occasionally)
  - `FIRST()`
  - a self-defined method with Java Driver
- *more* about "Read a doc"
  - **[Query 6]** AQL vs Java Driver: AQL is slightly faster when inserting a batch of documents
  - **[Query 7]** `ArangoCursor` is an iterator: print a nice result in a list by converting the iterator to an ArrayList
  - **[Query 8]** `SORT` and `LIMIT`

3. `UPDATE`, "**U**pdate a doc"

- `UPDATE` vs `REPLACE`
- **[Query 9]** update a single doc, `OLD` and `NEW`
- **[Query 10]** "READ after WRITE" is not allowed in a single AQL query
- **[Query 11]** batch update in a loop
- **[Query 12]** "WRITE after READ" is allowed in a single AQL query

4. `REMOVE`, "**D**elete a doc"

- **[Query 13]** remove in a loop

5. Array and `MERGE`

- **[Query 14]** array expansion: within "the result array of docs", if we have an attribute which is an inner array, array expansion will take out an attribute of this inner array and form a new array having only the values of this attribute. For example, we have an array of `users` which has an attribute `friends` listing out some friends. If we want to get only the names of the friends, we use array expansion to take out and form a new array with only the names of the friends, and put the array on the `friends` attribute.
- **[Query 15]** `MERGE` directly merges two documents
- **[Query 16]** inline expressions, `*` vs `**`, `CURRENT`

6. Graph traversals and cycle detection

- **[Query 17]** graph traversal
- **[Query 18]** detect cycles starting from a certain vertex
- **[Query 19]** detect all cycles
- **[Query 20]** detect the cycles within only a subset of vertices

## I. Quickstart

### 1. Access the default database through a coordinator in an ArangoDB Cluster

1. Make sure the Docker daemon is running on your machine.
2. Start the orchestration by `docker compose -f docker-compose.yml up` (or in the backend: `docker compose up -f docker-compose.yml -d`), until you see some messages like the following.

```
arango-coordinator1  | 1664615273.522205 [1] INFO [99d80] {general} Waiting for initial replication of system collections...
arango-coordinator1  | 1664615273.525363 [1] INFO [99d80] {general} bootstrapped coordinator CRDN-cb5a2f36-e6c6-4740-b594-aaf743050f09
arango-coordinator1  | 1664615273.767095 [1] INFO [cf3f4] {general} ArangoDB (version 3.9.2 [linux]) is ready for business. Have fun!
arango-coordinator2  | 1664615274.306926 [1] INFO [99d80] {general} bootstrapped coordinator CRDN-1ac0a833-eebd-47c2-8354-f83a2331d5fc
arango-coordinator2  | 1664615274.538391 [1] INFO [cf3f4] {general} ArangoDB (version 3.9.2 [linux]) is ready for business. Have fun!
```

3. Open a new terminal and enter the `arango-test` container by `docker exec -it arango-test sh`.

### 2. Run a (sub-)project, e.g., "~/project/v6/aql-tutorial", within the `arango-test` Docker container

After following the above instructions and setting up the environment, you can run any sub-project provided in the `/project` folder. (The path `/project` has been linked to the path `~/project` (i.e., `/root/project`) in the Docker container `arango-test`.) Java 17 and Maven have been pre-installed within the Docker container.

```shell
cd ~/project/v6/aql-tutorial
mvn clean compile
mvn exec:java -Dexec.mainClass="com.jasonqiu.demo.Main"
```

### 3. Stop and remove the cluster

Open a new terminal on you local machine, and run the following commands.

- Stop
  - `docker compose stop` to stop all the services
- Remove
  - `docker compose down -v` or `docker compose down --volumes` to remove all the containers, networks and volumes generated by the `docker-compose.yml` file
  - `docker rmi arangodb:3.9.2` and `docker rmi arangodb-docker-test:latest` to remove the relevant Docker images.
- Clean and remove the persisting volumes
  - Run `sudo rm -rf nodes logs` to delete relevant records/logs (if you don't need them for analysis any more)

### 4. Q&A

1. How do I connect to the Web interface of ArangoDB (on local machine)?

- Access `http://localhost:8000` (for `coordinator1`) and `http://localhost:8001` (for `coordinator2`) in the browser. Also remember to choose the correct database you are trying to access.

2. How do I connect to the database using `arangosh` (in `arango-test` container)?

- Connect to the default database `_system` via a coordinator, say `coordinator1` by `arangosh --server.endpoint tcp://coordinator1:8529 --server.username root --server.password ""`, and then submit any JavaScript commands.

3. How do I access the logs (on local machine)?

- Run `sudo chmod 777 logs/*/*.log` on the root of the project folder before accessing the log files on your local machine.

4. How do I read the logs?

- The logs are quite verbose. This is because (1) the logs have been set to TRACE level, meaning the log of all levels will be written to the log files, and (2) both user queries and system executions will be recorded in the logs. If you want to see a shorter log file, you can adjust the `--log.level` options in the `docker-compose.yml` (and probably also `docker-compose-restart.yml`) file to a level that is satisfactory to you.
- Read the logs of each coordinator and understand the execution of each query of your interest. Try searching code `[11160]` (meaning query begins) and code `[f5cee]` (meaning query ends) in `queries.log`.

### [Optional] Import an extract of LDBC by `arangoimport`

Here I also present a way to use `arangoimport` in combination with the previous steps. However, a recommended way is using ArangoDB Java Driver to import the dataset instead.

1. Make sure the Docker daemon is running on your machine.
2. Up the docker compose `docker compose -f docker-compose.yml up` (or in the backend: `docker compose -f docker-compose.yml up -d`), until you see some messages like the following. If you find the coordinators keep reconnecting with certain heartbeats, retry step 2.

```
arango-coordinator1  | 1664615273.522205 [1] INFO [99d80] {general} Waiting for initial replication of system collections...
arango-coordinator1  | 1664615273.525363 [1] INFO [99d80] {general} bootstrapped coordinator CRDN-cb5a2f36-e6c6-4740-b594-aaf743050f09
arango-coordinator1  | 1664615273.767095 [1] INFO [cf3f4] {general} ArangoDB (version 3.9.2 [linux]) is ready for business. Have fun!
arango-coordinator2  | 1664615274.306926 [1] INFO [99d80] {general} bootstrapped coordinator CRDN-1ac0a833-eebd-47c2-8354-f83a2331d5fc
arango-coordinator2  | 1664615274.538391 [1] INFO [cf3f4] {general} ArangoDB (version 3.9.2 [linux]) is ready for business. Have fun!
```

3. Open a new terminal and enter the `test` container by `docker exec -it arango-test sh`
4. Run the script `sh /resources/ldbc_extract/arangoimport.sh` to import an extract of LDBC
5. Connect to another database, say LDBC database `ldbc_snb_sf0001` via a coordinator, say `coordinator1` by
   - `arangosh --server.endpoint tcp://coordinator1:8529 --server.username root --server.password "" --server.database "ldbc_snb_sf0001"`

## II. Quickstart by `Makefile` [Optional]

If you have `gcc` installed on your machine, you can use the following commands to run this project.

- `make start`: start the orchestration
- `make stop`: stop the orchestration
- `make restart`: restart the orchestration (with options `--cluster.require-persisted-id true`)
- `make remove`: remove the orchestration and Docker images
- `make clean`: remove the local persisting volumes of the database

It is still recommended to run the raw commands by yourself, but `Makefile` can be a help.

## III. Additional Highlights of Java Syntax (useful when importing graph data from external files)

1. JSON to Jackson.

```java
ObjectMapper om = new ObjectMapper();
// https://stackoverflow.com/questions/39391095/how-to-convert-hashmap-to-jsonnode-with-jackson
JsonNode jsonNode = om.valueToTree(json);
System.out.println(jsonNode);
```

2. Rename a key from `"id"` to `"_key"` of a HashMap `map`.

```java
map.put("_key", map.remove("id"))
```

3. Use collections streams to generate ArangoDocument instances. Here `persons` and `knowsPersonsDocs` represent Java `Map` collections that contain the values of *nodes* and *edges*, respectively.

```java
List<BaseDocument> personDocs = persons.stream().map(node -> {
    BaseDocument doc = new BaseDocument(node.get("id"));
    node.remove("id");
    doc.setProperties(new HashMap<>(node));
    return doc;
}).collect(Collectors.toList());

List<BaseEdgeDocument> knowsPersonsDocs = knowsPersons.stream().map(edge -> {
    // BaseEdgeDocument edge = new BaseEdgeDocument("myVertexCollection/myFromKey",
    // "myVertexCollection/myToKey");
    BaseEdgeDocument edgeDoc = new BaseEdgeDocument(
            "person/" + edge.remove("src.id"),
            "person/" + edge.remove("dst.id"));
    edgeDoc.setProperties(new HashMap<>(edge));
    return edgeDoc;
}).collect(Collectors.toList());
```
