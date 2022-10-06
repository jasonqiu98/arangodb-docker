package com.jasonqiu.demo;

import com.arangodb.*;
import com.arangodb.entity.EdgeDefinition;
import com.arangodb.entity.StreamTransactionEntity;
import com.arangodb.mapping.ArangoJack;
import com.arangodb.model.DocumentCreateOptions;
import com.arangodb.model.StreamTransactionOptions;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    // host name and port
    private static String hostname = "coordinator1";
    private static int port = 8529;
    // the name of the database used in this example
    private static String dbName = "ldbc_graph";
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

    public static void main(String[] args) {
        cleanup();

        // Connection, db and collections
        ArangoDB arangoDB = new ArangoDB.Builder()
                .serializer(new ArangoJack())
                .host(hostname, port)
                .user("root")
                .password("")
                .build();
        ArangoDatabase db = Utils.createDatabase(arangoDB, dbName);

        // https://www.arangodb.com/2014/11/arangodb-java-driver-graphs/
        // Edge definitions of the graph
        List<EdgeDefinition> edgeDefinitions = new ArrayList<>();
        // one edge definition
        EdgeDefinition edgeDefKnowsPerson = new EdgeDefinition()
                .collection("knows")
                .from("person")
                .to("person");
        edgeDefinitions.add(edgeDefKnowsPerson);

        // collections will be auto created if they do not exist
        db.graph("socialNetwork").create(edgeDefinitions);

        // bulk import graph data with stream transactions
        // https://www.arangodb.com/docs/stable/drivers/java-examples-import-graph-data.html
        {
            // get persons as nodes, and rename "id" to "_key" for insertion
            List<Map<String, String>> persons = Utils.readCsvToJsonArray("resource/person.csv", '|');

            persons.stream().forEach(node -> node.put("_key", node.remove("id")));

            // get knowsPersons as edges, and rename "src.id" to "_from", "dst.id" to "_to"
            List<Map<String, String>> knowsPersons = Utils.readCsvToJsonArray("resource/person_knows_person.csv", '|');
            knowsPersons.stream().forEach(edge -> {
                // we need to create document handlers
                // "collectionName/_key", stored in "_id"
                edge.put("_from", "person/" + edge.remove("src.id"));
                edge.put("_to", "person/" + edge.remove("dst.id"));
            });

            StreamTransactionEntity tx = db.beginStreamTransaction(
                    new StreamTransactionOptions().writeCollections("person", "knows"));

            DocumentCreateOptions docCreateOptions = new DocumentCreateOptions().streamTransactionId(tx.getId());

            try {
                db.collection("person").insertDocuments(persons, docCreateOptions);
                db.collection("knows").insertDocuments(knowsPersons, docCreateOptions);
                db.commitStreamTransaction(tx.getId());
                logger.info("Collections \"person\" and \"knows\" imported from csv files.");
            } catch (Exception e) {
                db.abortStreamTransaction(tx.getId());
                logger.error("Failed to import collections from csv files: " + e.getMessage());
                // here we force exit the program if the collection cannot be imported
                System.exit(-1);
            }
        }

        arangoDB.shutdown();
    }
}
