package com.jasonqiu.demo;

import com.arangodb.*;
import com.arangodb.entity.BaseDocument;
import com.arangodb.model.CollectionCreateOptions;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {

    private static Logger logger = LoggerFactory.getLogger(Utils.class);

    /**
     * create a new database
     * 
     * @param conn   ArangoDB
     * @param dbName String, name of the database
     * @return
     */
    public static ArangoDatabase createDatabase(ArangoDB conn, String name) {
        ArangoDatabase db = conn.db(DbName.of(name));
        try {
            logger.info("Creating database...");
            db.create();
            logger.info("Database " + name + " created.");
        } catch (ArangoDBException e) {
            logger.error("Failed to create database: " + name + "; " + e.getMessage());
            // here we force exit the program if the database cannot be created
            System.exit(-1);
        }
        return db;
    }

    /**
     * create a new collection
     * 
     * @param db   ArangoDatabase
     * @param name name of the collection
     * @return
     */
    public static ArangoCollection createCollection(ArangoDatabase db, String name) {
        return createCollection(db, name, null);
    }

    public static ArangoCollection createCollection(ArangoDatabase db, String name, CollectionCreateOptions options) {
        ArangoCollection collection = db.collection(name);
        try {
            logger.info("Creating collection...");
            if (options != null) {
                collection.create(options);
            } else {
                collection.create();
            }
            logger.info("Collection " + name + " created.");
        } catch (ArangoDBException e) {
            logger.error("Failed to create collection: " + name + "; " +
                    e.getMessage());
            // here we force exit the program if the collection cannot be created
            System.exit(-1);
        }
        return collection;
    }

    /**
     * Convert the query result of a document array
     * to a List<BaseDocuement> in java
     * @param db
     * @param query
     * @return
     */
    public static List<BaseDocument> queryDocumentArray(ArangoDatabase db, String query) {
        
        // https://stackoverflow.com/questions/2012306/how-to-create-a-class-literal-of-a-known-type-classliststring
        // Suppress the warning here 
        // Type safety: Unchecked cast from Class to Class<List<Map<String, Object>>>
        @SuppressWarnings("unchecked")
        ArangoCursor<List<Map<String, Object>>> cursor = db.query(query,
            (Class<List<Map<String, Object>>>) ((Class<?>) List.class));
        // ArangoCursor<List> cursor = db.query(query5, List.class);
        List<BaseDocument> docList = new ArrayList<>();
        for (List<Map<String, Object>> mapList : cursor) {
            for (Map<String, Object> map : mapList) {
                BaseDocument doc = new BaseDocument();
                doc.setId(map.remove("_id").toString());
                doc.setKey(map.remove("_key").toString());
                doc.setRevision(map.remove("_rev").toString());
                doc.setProperties(map);
                docList.add(doc);
            }
        }
        return docList;
    }
}
