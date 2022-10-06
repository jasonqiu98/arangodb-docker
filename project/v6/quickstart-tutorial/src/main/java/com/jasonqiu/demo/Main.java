package com.jasonqiu.demo;

import com.arangodb.*;
import com.arangodb.entity.BaseDocument;
import com.arangodb.mapping.ArangoJack;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    // host name and port
    private static String hostname = "coordinator1";
    private static int port = 8529;

    // the name of the database used in this example
    private static String dbName = "quickstart";
    private static String collectionName = "firstCollection";
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

        // Connection
        ArangoDB arangoDB = new ArangoDB.Builder()
                .serializer(new ArangoJack())
                .host(hostname, port)
                .user("root")
                .password("")
                .build();
        // Creating a database
        ArangoDatabase db = arangoDB.db(DbName.of(dbName));
        try {
            logger.info("Creating database...");
            db.create();
            logger.info("Database " + dbName + " created.");
        } catch (ArangoDBException e) {
            logger.error("Failed to create database: " + dbName + "; " + e.getMessage());
        }

        // Creating a collection
        ArangoCollection collection = db.collection(collectionName);
        try {
            logger.info("Creating collection...");
            collection.create();
            logger.info("Collection " + collectionName + " created.");
        } catch (ArangoDBException e) {
            logger.error("Failed to create collection: " + collectionName + "; " + e.getMessage());
        }

        // Creating a document
        String key = "myKey";
        BaseDocument doc = new BaseDocument(key);
        doc.addAttribute("a", "Foo");
        doc.addAttribute("b", 42);
        logger.info("Inserting document...");
        collection.insertDocument(doc);

        // Read a document
        {
            logger.info("Reading document...");
            BaseDocument readDocument = collection.getDocument(key, BaseDocument.class);
            logger.info("Key: " + readDocument.getKey());
            logger.info("Attribute a: " + readDocument.getAttribute("a"));
            logger.info("Attribute b: " + readDocument.getAttribute("b"));
        }

        // Creating a document from Jackson JsonNode
        String keyJackson = "myJacksonKey";
        {
            logger.info("Creating a document from Jackson JsonNode...");
            JsonNode jsonNode = JsonNodeFactory.instance.objectNode()
                    .put("_key", keyJackson)
                    .put("a", "Bar")
                    .put("b", 53);
            logger.info("Inserting document from Jackson JsonNode...");
            collection.insertDocument(jsonNode);
        }

        // Read a document as Jackson JsonNode
        {
            logger.info("Reading document as Jackson JsonNode...");
            JsonNode jsonNode = collection.getDocument(key, ObjectNode.class);
            logger.info("Key: " + jsonNode.get("_key").textValue());
            logger.info("Attribute a: " + jsonNode.get("a").textValue());
            logger.info("Attribute b: " + jsonNode.get("b").intValue());
        }

        // Update a document
        {
            doc.addAttribute("c", "Bar");
            logger.info("Updating document ...");
            collection.updateDocument(key, doc);
        }

        // Read the document again
        {
            logger.info("Reading updated document ...");
            BaseDocument updatedDocument = collection.getDocument(key, BaseDocument.class);
            logger.info("Key: " + updatedDocument.getKey());
            logger.info("Attribute a: " + updatedDocument.getAttribute("a"));
            logger.info("Attribute b: " + updatedDocument.getAttribute("b"));
            logger.info("Attribute c: " + updatedDocument.getAttribute("c"));
        }

        // Delete a document
        {
            logger.info("Deleting document ...");
            collection.deleteDocument(key);
        }

        // Execute AQL queries
        {
            for (int i = 0; i < 10; i++) {
                BaseDocument value = new BaseDocument(String.valueOf(i));
                value.addAttribute("name", "Homer");
                collection.insertDocument(value);
            }

            String query = "FOR t IN firstCollection FILTER t.name == @name RETURN t";
            Map<String, Object> bindVars = Collections.singletonMap("name", "Homer");
            logger.info("Executing read query ...");
            ArangoCursor<BaseDocument> cursor = db.query(query, bindVars, null, BaseDocument.class);
            cursor.forEach(aDocument -> logger.info("Key: " + aDocument.getKey()));
        }

        // Delete a document with AQL
        {
            String query = "FOR t IN firstCollection FILTER t.name == @name "
                    + "REMOVE t IN firstCollection LET removed = OLD RETURN removed";
            Map<String, Object> bindVars = Collections.singletonMap("name", "Homer");
            logger.info("Executing delete query ...");
            ArangoCursor<BaseDocument> cursor = db.query(query, bindVars, null, BaseDocument.class);
            cursor.forEach(aDocument -> logger.info("Removed document " + aDocument.getKey()));
        }

        arangoDB.shutdown();
    }
}
