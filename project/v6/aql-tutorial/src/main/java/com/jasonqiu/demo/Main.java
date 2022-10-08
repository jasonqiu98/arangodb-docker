package com.jasonqiu.demo;

import com.arangodb.*;
import com.arangodb.entity.BaseDocument;
import com.arangodb.entity.CollectionType;
import com.arangodb.entity.EdgeDefinition;
import com.arangodb.entity.GraphEntity;
import com.arangodb.entity.StreamTransactionEntity;
import com.arangodb.entity.VertexEntity;
import com.arangodb.mapping.ArangoJack;
import com.arangodb.model.CollectionCreateOptions;
import com.arangodb.model.DocumentCreateOptions;
import com.arangodb.model.StreamTransactionOptions;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    // host name and port
    private static String hostname = "coordinator1";
    private static int port = 8529;

    // the name of the database used in this example
    private static String dbName = "aql_tutorial";
    private static Logger logger = LoggerFactory.getLogger("           ");
    private static Logger beginLogger = LoggerFactory.getLogger(">>>BEGIN>>>");
    private static Logger endLogger = LoggerFactory.getLogger("<<<<END<<<<");

    private static void cleanup() {
        ArangoDB arangoDB = new ArangoDB.Builder()
                .serializer(new ArangoJack())
                .host(hostname, port)
                .user("root")
                .password("")
                .build();
        ArangoDatabase db = arangoDB.db(DbName.of(dbName));
        if (db.exists()) {
            db.drop();
        }
        arangoDB.shutdown();
    }

    // https://www.arangodb.com/docs/stable/graphs.html#the-social-graph
    // https://github.com/arangodb/arangodb/blob/devel/js/common/modules/%40arangodb/graph-examples/example-graph.js#L76
    // an example - social graph
    private static GraphEntity createSocialGraph(ArangoDatabase db) {
        beginLogger.info("creating social graph...");

        Utils.createCollection(db, "female");
        Utils.createCollection(db, "male");
        // https://github.com/arangodb/arangodb-java-driver/issues/142
        Utils.createCollection(db, "relation",
                new CollectionCreateOptions().type(CollectionType.EDGES).shardKeys("vertex"));

        // https://www.arangodb.com/2014/11/arangodb-java-driver-graphs/
        // Edge definitions of the graph
        List<EdgeDefinition> edgeDefinitions = new ArrayList<>();
        // one edge definition
        // from either female or male, to either female or male
        EdgeDefinition edgeDefRelation = new EdgeDefinition()
                .collection("relation")
                .from("female", "male")
                .to("female", "male");
        edgeDefinitions.add(edgeDefRelation);

        // collections will be auto created if they do not exist
        GraphEntity socialGraph = db.graph("social").create(edgeDefinitions);

        // four vertices
        VertexEntity a = db.graph("social").vertexCollection("female").insertVertex(Map.ofEntries(
                Map.entry("name", "Alice"),
                Map.entry("_key", "alice")));
        VertexEntity b = db.graph("social").vertexCollection("male").insertVertex(Map.ofEntries(
                Map.entry("name", "Bob"),
                Map.entry("_key", "bob")));
        VertexEntity c = db.graph("social").vertexCollection("male").insertVertex(Map.ofEntries(
                Map.entry("name", "Charly"),
                Map.entry("_key", "charly")));
        VertexEntity d = db.graph("social").vertexCollection("female").insertVertex(Map.ofEntries(
                Map.entry("name", "Diana"),
                Map.entry("_key", "diana")));

        // four edges
        db.graph("social").edgeCollection("relation").insertEdge(Map.ofEntries(
                Map.entry("_from", a.getId()),
                Map.entry("_to", b.getId()),
                Map.entry("type", "married"),
                Map.entry("vertex", a.getKey())));
        db.graph("social").edgeCollection("relation").insertEdge(Map.ofEntries(
                Map.entry("_from", a.getId()),
                Map.entry("_to", c.getId()),
                Map.entry("type", "friend"),
                Map.entry("vertex", a.getKey())));
        db.graph("social").edgeCollection("relation").insertEdge(Map.ofEntries(
                Map.entry("_from", c.getId()),
                Map.entry("_to", d.getId()),
                Map.entry("type", "married"),
                Map.entry("vertex", c.getKey())));
        db.graph("social").edgeCollection("relation").insertEdge(Map.ofEntries(
                Map.entry("_from", d.getId()),
                Map.entry("_to", b.getId()),
                Map.entry("type", "friend"),
                Map.entry("vertex", d.getKey())));

        // add a new edge to form a cycle a -> c -> d -> a
        db.graph("social").edgeCollection("relation").insertEdge(Map.ofEntries(
                Map.entry("_from", d.getId()),
                Map.entry("_to", a.getId()),
                Map.entry("type", "friend"),
                Map.entry("vertex", d.getKey())));

        endLogger.info("social graph created.");

        return socialGraph;
    }

    private static List<BaseDocument> filterByName(ArangoDatabase db, String collectionName, String name) {
        String query = """
                    FOR doc IN @@collection
                        FILTER doc.name == @name
                        RETURN doc
                """;
        Map<String, Object> bindVars = Map.ofEntries(
                Map.entry("@collection", collectionName), // for collection
                Map.entry("name", name));
        ArangoCursor<BaseDocument> cursor = db.query(query, bindVars, BaseDocument.class);
        beginLogger.info("AQL Read: from collection \"{}\" of name \"{}\"",
                collectionName, name);
        List<BaseDocument> docList = cursor.asListRemaining();
        return docList;
    }

    public static void main(String[] args) {
        cleanup();

        // Connection
        ArangoDB arangoDB = new ArangoDB.Builder()
                .serializer(new ArangoJack())
                .host(hostname, port)
                .user("root")
                .password("")
                .build();
        // Creating a database
        ArangoDatabase db = Utils.createDatabase(arangoDB, dbName);
        // create a social graph with
        // two vertex collections - male, female (name)
        // and one edge collection - relation (type, vertex) [vertex is out vertex]
        createSocialGraph(db);

        // AQL Queries 1-3: INSERT
        // insert: vertex, return and edge
        {
            // insert a vertex
            // https://www.arangodb.com/docs/stable/aql/operations-insert.html#returning-the-inserted-documents
            String query1 = """
                        INSERT { "name": "Eric" }
                            INTO male
                    """;

            beginLogger.info("Executing AQL Query 1: insert a vertex...");
            try {
                db.query(query1, null);
                endLogger.info("Query 1 Success.");
            } catch (Exception e) {
                endLogger.error("Query 1 Failure: " + e.getMessage());
            }

            // return the inserted document
            // https://www.arangodb.com/docs/stable/aql/operations-insert.html#returning-the-inserted-documents
            String query2 = """
                        INSERT { "name": "Fiona" }
                            INTO female
                            RETURN NEW
                    """;

            beginLogger.info("Executing AQL Query 2: return the inserted document...");
            try {
                ArangoCursor<BaseDocument> cursor = db.query(query2, BaseDocument.class);
                cursor.forEach(aDocument -> logger.info("Key: " + aDocument.getAttribute("name")));
                endLogger.info("Query 2 Success.");
            } catch (Exception e) {
                endLogger.error("Query 2 Failure: " + e.getMessage());
            }

            // insert an edge
            // when inserting into an edge collection,
            // it is mandatory to specify the attributes _from and _to in document
            String query3 = """
                        LET alice = (
                            FOR f_alice IN female
                                FILTER f_alice.name == "Alice"
                                RETURN f_alice
                        )
                        LET fiona = (
                            FOR f_fiona IN female
                                FILTER f_fiona.name == "Fiona"
                                RETURN f_fiona
                        )
                        INSERT { _from: alice[0]._id, _to: fiona[0]._id, "type": "friend", "vertex": alice[0]._key }
                            INTO relation
                            LET r = NEW
                            RETURN r._key
                    """;

            beginLogger.info("Executing AQL Query 3: insert an edge...");
            try {
                ArangoCursor<String> cursor = db.query(query3, String.class);
                cursor.forEach(key -> logger.info("Key: " + key));
                endLogger.info("Query 3 Success.");
            } catch (Exception e) {
                endLogger.error("Query 3 Failure: " + e.getMessage());
            }

        }

        // AQL Queries 4-5: DOCUMENT
        // doc vs doc array
        {
            /**
             * [
             * {
             * "_key": "alice",
             * "_id": "female/alice",
             * "_rev": "_e4luqTS---",
             * "name": "Alice"
             * }
             * ]
             */
            String query4 = """
                        FOR f_alice IN female
                            FILTER f_alice.name == "Alice"
                            // we can also use `RETURN f_alice` directly
                            RETURN DOCUMENT(female, f_alice._key)
                    """;

            beginLogger.info("Executing AQL Query 4: read the document(s) of name \"Alice\"...");
            try {
                ArangoCursor<BaseDocument> cursor = db.query(query4, BaseDocument.class);
                cursor.forEach(aDocument -> logger.info("Key: " + aDocument.getKey()));
                endLogger.info("Query 4 Success.");
            } catch (Exception e) {
                endLogger.error("Query 4 Failure: " + e.getMessage());
            }

            /*
             * [
             * [
             * {
             * "_key": "alice",
             * "_id": "female/alice",
             * "_rev": "_e3ylytC---",
             * "name": "Alice"
             * }
             * ]
             * ]
             */
            // Subqueries always return a result array
            // even if there is only a single return value
            // https://www.arangodb.com/docs/3.9/aql/examples-combining-queries.html#subquery-results-and-unwinding
            // use FIRST() to unwind the nested array, i.e.,
            /**
             * String query5 = """
             * LET a = (
             * FOR f_alice IN female
             * FILTER f_alice.name == "Alice"
             * RETURN DOCUMENT(female, f_alice._key)
             * )
             * RETURN FIRST(a)
             * """;
             */
            // but here we still stick to this more complicated case
            // and see how we apply the Java Driver here
            String query5 = """
                        LET a = (
                            FOR f_alice IN female
                                FILTER f_alice.name == "Alice"
                                RETURN DOCUMENT(female, f_alice._key)
                        )
                        RETURN a
                    """;

            beginLogger.info("Executing AQL Query 5: read an array of " +
                    "document(s) of name \"Alice\"...");
            try {
                // use the implemented `queryDocumentArray` to read such query results
                List<BaseDocument> docArray = Utils.queryDocumentArray(db, query5);
                docArray.forEach(doc -> logger.info(doc.toString()));
                endLogger.info("Query 5 Success.");
            } catch (Exception e) {
                endLogger.error("Query 5 Failure: " + e.getMessage());
            }
        }

        // AQL Query 6
        // INSERT: AQL, Java Driver and Stream Transactions
        {
            // insert some documents - AQL
            String query6 = """
                        FOR i in 1..5
                            INSERT { "_key": CONCAT("someone", i), "name": "Someone" }
                                INTO male
                    """;

            beginLogger.info("Executing AQL Query 6: insert five docs with " +
                    "incrementing keys and a common name \"Someone\"");
            try {
                ArangoCursor<BaseDocument> cursor = db.query(query6, BaseDocument.class);
                double runtime = (long) (cursor.getStats().getExecutionTime() * 1e9) / 1e6;
                endLogger.info("Query 6 Success. Runtime: {} ms", runtime);
            } catch (Exception e) {
                endLogger.error("Query 6 Failure: " + e.getMessage());
            }

            // insert some documents - Batch Insert by Java Driver, in a Stream Transaction
            beginLogger.info("Executing Batch Insert by Java Driver: insert another five docs");
            List<BaseDocument> baseDocArray = new ArrayList<>();
            for (int i = 6; i < 11; i++) {
                BaseDocument baseDoc = new BaseDocument("someone" + i);
                baseDoc.addAttribute("name", "Someone");
                baseDocArray.add(baseDoc);
            }
            StreamTransactionEntity tx = db
                    .beginStreamTransaction(new StreamTransactionOptions().writeCollections("male"));
            DocumentCreateOptions options = new DocumentCreateOptions().streamTransactionId(tx.getId());
            try {
                long start = System.nanoTime();
                db.collection("male").insertDocuments(baseDocArray, options);
                long end = System.nanoTime();
                db.commitStreamTransaction(tx.getId());
                double runtime = (double) (end - start) / 1e6;
                // the timing doesn't count in the begin and end of the transaction
                endLogger.info("Batch Insert by Java Driver Success. Runtime: {} ms", runtime);
            } catch (Exception e) {
                db.abortStreamTransaction(tx.getId());
                endLogger.error("Batch Insert by Java Driver Failure: " + e.getMessage());
            }
        }

        // AQL Quert 7: Read
        // iterator vs list
        /**
         * note that in the FILTER sub statement
         * those docs without the specified attribute will also be returned
         */
        {
            // After Query 7, we introduce a method `filterByName`
            String query7 = """
                        FOR m IN male
                            FILTER m.name == "Someone"
                            RETURN m
                    """;

            beginLogger.info("Executing AQL Query 7: read the document(s) of name \"Someone\"...");
            try {
                logger.info("Output with ArangoCursor/ArangoIterator: Key, line by line");
                ArangoCursor<BaseDocument> cursor = db.query(query7, BaseDocument.class);
                // converts to ArangoIterator, which extends java.util.Iterator
                Iterator<BaseDocument> iter = cursor.iterator();
                cursor.forEach(aDocument -> logger.info("Key: " + aDocument.getKey()));
                // re-run, but no new logs => iterator has ended
                iter.forEachRemaining(doc -> logger.info("Keys: " + doc.getKey()));

                logger.info("Output with List: Keys, one line");
                // renew cursor
                cursor = db.query(query7, BaseDocument.class);
                // convert ArangoCursor to List
                List<BaseDocument> docList = cursor.asListRemaining();
                logger.info("Keys: {}",
                        docList.stream().map(doc -> doc.getKey()).collect(Collectors.toList()).toString());
                endLogger.info("Query 7 Success.");
            } catch (Exception e) {
                endLogger.error("Query 7 Failure: " + e.getMessage());
            }
        }

        // AQL Query 8: SORT and LIMIT
        {
            String query8 = """
                        FOR m_someone IN male
                            FILTER m_someone.name == "Someone"
                            SORT m_someone._key DESC
                            LIMIT 2, 3
                            RETURN m_someone
                    """;

            beginLogger.info("Executing AQL Query 8: sort and limit " +
                    "the document(s) of name \"Someone\"...");
            try {
                ArangoCursor<BaseDocument> cursor = db.query(query8, BaseDocument.class);
                List<BaseDocument> docList = cursor.asListRemaining();
                logger.info("Keys: {}",
                        docList.stream().map(doc -> doc.getKey()).collect(Collectors.toList()).toString());
                endLogger.info("Query 8 Success.");
            } catch (Exception e) {
                endLogger.error("Query 8 Failure: " + e.getMessage());
            }
        }

        // AQL Queries 9-12: UPDATE
        // Note that UPDATE only performs "partial update" on a document
        // If we need to replace the whole doc with a new doc (specified in the query),
        // use the keyword `REPLACE` instead
        {
            // update a single doc
            // OLD, NEW
            String query9 = """
                        UPDATE "someone1"
                            WITH { name: "NotSomeone" } IN male
                            RETURN { old: OLD, new: NEW }
                    """;

            beginLogger.info("Executing AQL Query 9: update the doc with key \"someone1\"...");
            try {
                @SuppressWarnings("unchecked")
                ArangoCursor<Map<String, Object>> cursor = db.query(query9,
                        (Class<Map<String, Object>>) ((Class<?>) Map.class));
                cursor.forEach(map -> logger.info("old: {}, new: {}",
                        map.get("old").toString(),
                        map.get("new").toString()));
                endLogger.info("Query 9 Success.");
            } catch (Exception e) {
                endLogger.error("Query 9 Failure: " + e.getMessage());
            }

            // "READ after WRITE" is not allowed, Error: 1579
            // https://www.arangodb.com/docs/stable/appendix-error-codes.html#1579
            String query10 = """
                        LET a = (
                            FOR m_someone IN male
                                FILTER m_someone.name == "Someone"
                                UPDATE m_someone WITH { name: "NotSomeone" } IN male
                        )
                        FOR m IN male
                            FILTER m.name == "NotSomeone"
                            RETURN m
                    """;

            beginLogger.info("Executing AQL Query 10: update remaining docs of name \"Someone\"...");
            try {
                ArangoCursor<BaseDocument> cursor = db.query(query10, BaseDocument.class);
                // convert ArangoCursor to List
                List<BaseDocument> docList = cursor.asListRemaining();
                logger.info("Keys: {}",
                        docList.stream().map(doc -> doc.getKey()).collect(Collectors.toList()).toString());
                endLogger.info("Query 10 Success.");
            } catch (Exception e) {
                endLogger.error("Query 10 Failure: " + e.getMessage());
            }

            // read "NotSomeone"
            // only one doc was updated
            try {
                List<BaseDocument> docList = filterByName(db, "male", "NotSomeone");
                logger.info("Keys: {}",
                        docList.stream().map(doc -> doc.getKey()).collect(Collectors.toList()).toString());
                endLogger.info("AQL Read Success.");
            } catch (Exception e) {
                endLogger.error("AQL Read Failure: " + e.getMessage());
            }

            // separate "READ after WRITE"
            // start from WRITE/UPDATE
            String query11 = """
                        FOR m_someone IN male
                            FILTER m_someone.name == "Someone"
                            UPDATE m_someone WITH { name: "NotSomeone" } IN male
                    """;

            beginLogger.info("Executing AQL Query 11: update remaining docs of name \"Someone\"...");
            try {
                db.query(query11, null);
                endLogger.info("Query 11 Success.");
            } catch (Exception e) {
                endLogger.error("Query 11 Failure: " + e.getMessage());
            }

            // then READ, ok
            try {
                List<BaseDocument> docList = filterByName(db, "male", "NotSomeone");
                logger.info("Keys: {}",
                        docList.stream().map(doc -> doc.getKey()).collect(Collectors.toList()).toString());
                endLogger.info("AQL Read Success.");
            } catch (Exception e) {
                endLogger.error("AQL Read Failure: " + e.getMessage());
            }

            // "WRITE after READ", ok
            String query12 = """
                        LET a = (
                            FOR m IN male
                                FILTER m.name == "NotSomeone"
                                RETURN m
                        )
                        FOR m_someone IN male
                            FILTER m_someone.name == "NotSomeone"
                            UPDATE m_someone WITH { name: "Someone" } IN male
                    """;

            beginLogger.info("Executing AQL Query 12: update docs of name \"NotSomeone\"...");
            try {
                db.query(query12, null);
                endLogger.info("Query 12 Success.");
            } catch (Exception e) {
                endLogger.error("Query 12 Failure: " + e.getMessage());
            }

            // read NotSomeone, no docs
            try {
                List<BaseDocument> docList = filterByName(db, "male", "NotSomeone");
                logger.info("Keys: {}",
                        docList.stream().map(doc -> doc.getKey()).collect(Collectors.toList()).toString());
                endLogger.info("AQL Read Success.");
            } catch (Exception e) {
                endLogger.error("AQL Read Failure: " + e.getMessage());
            }

            // read Someone, the docs updated
            try {
                List<BaseDocument> docList = filterByName(db, "male", "Someone");
                logger.info("Keys: {}",
                        docList.stream().map(doc -> doc.getKey()).collect(Collectors.toList()).toString());
                endLogger.info("AQL Read Success.");
            } catch (Exception e) {
                endLogger.error("AQL Read Failure: " + e.getMessage());
            }
        }

        // AQL Query 13: REMOVE
        {
            String query13 = """
                        FOR m IN male
                            FILTER m.name == "Someone"
                            REMOVE m IN male
                    """;

            beginLogger.info("Executing AQL Query 13: remove docs of name \"Someone\"...");
            try {
                db.query(query13, null);
                endLogger.info("Query 13 Success.");
            } catch (Exception e) {
                endLogger.error("Query 13 Failure: " + e.getMessage());
            }

            // read Someone, all docs of name "Someone" removed
            try {
                List<BaseDocument> docList = filterByName(db, "male", "Someone");
                logger.info("Keys: {}",
                        docList.stream().map(doc -> doc.getKey()).collect(Collectors.toList()).toString());
                endLogger.info("AQL Read Success.");
            } catch (Exception e) {
                endLogger.error("AQL Read Failure: " + e.getMessage());
            }
        }

        // AQL Queries 14-16
        // Array expansion, MERGE and inline expressions
        {
            String userArray = """
                        [
                            {
                              "name": "john",
                              "age": 35,
                              "friends": [
                                { "name": "tina", "age": 43 },
                                { "name": "helga", "age": 52 },
                                { "name": "alfred", "age": 34 }
                              ]
                            },
                            {
                              "name": "yves",
                              "age": 24,
                              "friends": [
                                { "name": "sergei", "age": 27 },
                                { "name": "tiffany", "age": 25 }
                              ]
                            },
                            {
                              "name": "sandra",
                              "age": 40,
                              "friends": [
                                { "name": "bob", "age": 32 },
                                { "name": "elena", "age": 48 }
                              ]
                            }
                        ]
                    """;

            // array expansion

            // friends=null
            // String query14 = "LET users =".concat(userArray).concat(
            // """
            // FOR u IN users
            // RETURN { name: u.name, friends: u.friends.name }
            // """
            // );

            // friends is the array of "u.friends.name"
            String query14 = "LET users =".concat(userArray).concat(
                    """
                                FOR u IN users
                                    RETURN { name: u.name, friends: u.friends[*].name }
                            """);

            beginLogger.info("Executing AQL Query 14: array expansion...");
            try {
                ArangoCursor<BaseDocument> cursor = db.query(query14, BaseDocument.class);
                List<BaseDocument> docList = cursor.asListRemaining();
                logger.info(docList.toString());
                endLogger.info("Query 14 Success.");
            } catch (Exception e) {
                endLogger.error("Query 14 Failure: " + e.getMessage());
            }

            // merge
            String query15 = "LET users =".concat(userArray).concat(
                    """
                                FOR u IN users
                                    RETURN MERGE({ name: u.name, friends: u.friends[*].name },
                                        { age: u.age })
                            """);

            beginLogger.info("Executing AQL Query 15: merge two sub-docs...");
            try {
                ArangoCursor<BaseDocument> cursor = db.query(query15, BaseDocument.class);
                List<BaseDocument> docList = cursor.asListRemaining();
                logger.info(docList.toString());
                endLogger.info("Query 15 Success.");
            } catch (Exception e) {
                endLogger.error("Query 15 Failure: " + e.getMessage());
            }

            // inline expression for arrays
            // the first star is the syntax
            // the second star flats the inner arrays to one array
            // CURRENT means the current value of iteration
            String query16 = """
                        LET arr = [ [ 1, 2 ], 3, [ 4, 5 ], 6 ]
                        RETURN arr[** FILTER CURRENT % 2 == 0]
                    """;
            beginLogger.info("Executing AQL Query 16: inline filter...");
            try {
                // Suppress the warning here
                // Type safety: Unchecked cast from Class to Class<List<Integer>>
                @SuppressWarnings("unchecked")
                ArangoCursor<List<Integer>> cursor = db.query(query16, (Class<List<Integer>>) ((Class<?>) List.class));
                List<List<Integer>> docList = cursor.asListRemaining();
                logger.info(docList.toString());
                endLogger.info("Query 16 Success.");
            } catch (Exception e) {
                endLogger.error("Query 16 Failure: " + e.getMessage());
            }
        }

        // AQL Queries: Graph traversals and cycle detection
        {
            // path is a map with all previous edges and vertices
            // a path is like { "edges": [ ... ], "vertices": [ ... ] }
            String query17 = """
                        WITH male, female
                        FOR vertex, edge, path
                            IN 2..5
                            OUTBOUND "female/alice"
                            GRAPH social
                            RETURN CONCAT_SEPARATOR("->", path.vertices[*].name)
                    """;
            beginLogger.info("Executing AQL Query 17: graph traversal...");
            try {
                ArangoCursor<String> cursor = db.query(query17, String.class);
                List<String> path = cursor.asListRemaining();
                logger.info(path.toString());
                endLogger.info("Query 17 Success.");
            } catch (Exception e) {
                endLogger.error("Query 17 Failure: " + e.getMessage());
            }

            // detect cycles starting from Alice
            String query18 = """
                        WITH male, female
                        FOR vertex, edge, path
                            IN 2..5
                            OUTBOUND @start
                            GRAPH social
                            FILTER vertex._id == @start
                            RETURN CONCAT_SEPARATOR("->", path.vertices[*].name)
                    """;
            beginLogger.info("Executing AQL Query 18: cycle detection, starting from vertex Alice...");
            try {
                // start from Alice
                Map<String, Object> bindVars = Collections.singletonMap("start", "female/alice");
                ArangoCursor<String> cursor = db.query(query18, bindVars, String.class);
                List<String> path = cursor.asListRemaining();
                logger.info(path.toString());
                endLogger.info("Query 18 Success.");
            } catch (Exception e) {
                endLogger.error("Query 18 Failure: " + e.getMessage());
            }

            // detect all cycles
            String query19 = """
                        FOR start IN relation
                            FOR vertex, edge, path
                                IN 2..5
                                OUTBOUND start._from
                                GRAPH social
                                FILTER edge._to == start._from
                                RETURN CONCAT_SEPARATOR("->", path.vertices[*].name)
                    """;
            beginLogger.info("Executing AQL Query 19: cycle detection, starting from all edges...");
            try {
                ArangoCursor<String> cursor = db.query(query19, String.class);
                List<String> path = cursor.asListRemaining();
                logger.info(path.toString());
                endLogger.info("Query 19 Success.");
            } catch (Exception e) {
                endLogger.error("Query 19 Failure: " + e.getMessage());
            }

            // detect the cycles within only a subset of vertices (say, vertices in the male
            // collection)
            // IS_SAME_COLLECTION():
            // https://www.arangodb.com/docs/stable/aql/functions-document.html#is_same_collection
            // `PRUNE NOT IS_SAME_COLLECTION("male", vertex)` gets rid of
            // all non-male vertices
            // In this case where we only have two vertex collections, we can also query by
            // `PRUNE IS_SAME_COLLECTION("female", vertex)`
            String query20 = """
                        FOR start IN relation
                            FOR vertex, edge, path
                                IN 2..5
                                OUTBOUND start._from
                                GRAPH social
                                PRUNE NOT IS_SAME_COLLECTION("male", vertex)
                                FILTER edge._to == start._from
                                RETURN CONCAT_SEPARATOR("->", path.vertices[*].name)
                    """;
            beginLogger.info("Executing AQL Query 20: cycle detection, starting from all male vertices...");
            try {
                ArangoCursor<String> cursor = db.query(query20, String.class);
                List<String> path = cursor.asListRemaining();
                logger.info(path.toString());
                endLogger.info("Query 20 Success.");
            } catch (Exception e) {
                endLogger.error("Query 20 Failure: " + e.getMessage());
            }
        }

        arangoDB.shutdown();
    }
}
