package com.jasonqiu.demo;

import com.arangodb.*;
import com.arangodb.entity.BaseDocument;
import com.arangodb.entity.CollectionType;
import com.arangodb.entity.EdgeDefinition;
import com.arangodb.entity.GraphEntity;
import com.arangodb.entity.VertexEntity;
import com.arangodb.mapping.ArangoJack;
import com.arangodb.model.CollectionCreateOptions;
import com.arangodb.model.VertexCreateOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    // host name and port
    private static String hostname = "coordinator1";
    private static int port = 8529;

    // the name of the database used in this example
    private static String dbName = "aql_tutorial";
    private static Logger logger = LoggerFactory.getLogger(Main.class);

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
        logger.info("creating social graph...");

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
            Map.entry("_key", "alice")
        ));
        VertexEntity b = db.graph("social").vertexCollection("male").insertVertex(Map.ofEntries(
            Map.entry("name", "Bob"),
            Map.entry("_key", "bob")
        ));
        VertexEntity c = db.graph("social").vertexCollection("male").insertVertex(Map.ofEntries(
            Map.entry("name", "Charly"),
            Map.entry("_key", "charly")
        ));
        VertexEntity d = db.graph("social").vertexCollection("female").insertVertex(Map.ofEntries(
            Map.entry("name", "Diana"),
            Map.entry("_key", "diana")
        ));

        // four edges
        db.graph("social").edgeCollection("relation").insertEdge(Map.ofEntries(
            Map.entry("_from", a.getId()),
            Map.entry("_to", b.getId()),
            Map.entry("type", "married"),
            Map.entry("vertex", a.getKey())
        ));
        db.graph("social").edgeCollection("relation").insertEdge(Map.ofEntries(
            Map.entry("_from", a.getId()),
            Map.entry("_to", c.getId()),
            Map.entry("type", "friend"),
            Map.entry("vertex", a.getKey())
        ));
        db.graph("social").edgeCollection("relation").insertEdge(Map.ofEntries(
            Map.entry("_from", c.getId()),
            Map.entry("_to", d.getId()),
            Map.entry("type", "married"),
            Map.entry("vertex", c.getKey())
        ));
        db.graph("social").edgeCollection("relation").insertEdge(Map.ofEntries(
            Map.entry("_from", b.getId()),
            Map.entry("_to", d.getId()),
            Map.entry("type", "friend"),
            Map.entry("vertex", b.getKey())
        ));

        logger.info("social graph created.");

        return socialGraph;
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

        // AQL Query 1-3: INSERT
        {
            // insert a vertex
            // https://www.arangodb.com/docs/stable/aql/operations-insert.html#returning-the-inserted-documents
            String query1 = """
                INSERT { "name": "Eric" } 
                    INTO male
            """;
            logger.info("Executing AQL Query 1: insert a vertex...");
            try {
                db.query(query1, null);
                logger.info("Query 1 Success.");
            } catch (Exception e) {
                logger.error("Query 1 Failure: " + e.getMessage());
            }

            // return the inserted document
            // https://www.arangodb.com/docs/stable/aql/operations-insert.html#returning-the-inserted-documents
            String query2 = """
                INSERT { "name": "Fiona" }
                    INTO female
                    RETURN NEW        
            """;
            logger.info("Executing AQL Query 2: return the inserted document...");
            try {
                ArangoCursor<BaseDocument> cursor = db.query(query2, BaseDocument.class);
                cursor.forEach(aDocument -> logger.info("Key: " + aDocument.getAttribute("name")));
                logger.info("Query 2 Success.");
            } catch (Exception e) {
                logger.error("Query 2 Failure: " + e.getMessage());
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
            logger.info("Executing AQL Query 3: insert an edge...");
            
            try {
                ArangoCursor<String> cursor = db.query(query3, String.class);
                cursor.forEach(key -> logger.info("Key: " + key));
                logger.info("Query 3 Success.");
            } catch (Exception e) {
                logger.error("Query 3 Failure: " + e.getMessage());
            }
        }


        // // Execute AQL queries
        // {
        //     for (int i = 0; i < 10; i++) {
        //         BaseDocument value = new BaseDocument(String.valueOf(i));
        //         value.addAttribute("name", "Homer");
        //         collection.insertDocument(value);
        //     }

        //     String query = "FOR t IN firstCollection FILTER t.name == @name RETURN t";
        //     Map<String, Object> bindVars = Collections.singletonMap("name", "Homer");
        //     logger.info("Executing read query ...");
        //     ArangoCursor<BaseDocument> cursor = db.query(query, bindVars, null, BaseDocument.class);
        //     cursor.forEach(aDocument -> logger.info("Key: " + aDocument.getKey()));
        // }

        // // Delete a document with AQL
        // {
        //     String query = "FOR t IN firstCollection FILTER t.name == @name "
        //             + "REMOVE t IN firstCollection LET removed = OLD RETURN removed";
        //     Map<String, Object> bindVars = Collections.singletonMap("name", "Homer");
        //     logger.info("Executing delete query ...");
        //     ArangoCursor<BaseDocument> cursor = db.query(query, bindVars, null, BaseDocument.class);
        //     cursor.forEach(aDocument -> logger.info("Removed document " + aDocument.getKey()));
        // }

        arangoDB.shutdown();
    }
}
