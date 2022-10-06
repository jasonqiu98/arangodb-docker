package com.jasonqiu.demo;

import com.arangodb.*;

import java.io.*;
import java.util.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.csv.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {

    private static Logger logger = LoggerFactory.getLogger(Utils.class);

    /**
     * convert csv to List<Map<String, String>> with the help of Jackson
     * 
     * @param filename        ".csv" file name
     * @param columnSeparator the column separator used in the csv file, '|' here
     * @return a JSON array
     */
    public static List<Map<String, String>> readCsvToJsonArray(String filename, char columnSeparator) {
        List<Map<String, String>> jsonArray = new ArrayList<>();
        try {
            File input = new File(filename);
            CsvSchema csv = CsvSchema.emptySchema().withHeader().withColumnSeparator(columnSeparator);
            CsvMapper csvMapper = new CsvMapper();
            MappingIterator<Map<String, String>> mappingIterator = csvMapper.reader().forType(Map.class).with(csv)
                    .readValues(input);
            jsonArray = mappingIterator.readAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonArray;
    }

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
        ArangoCollection collection = db.collection(name);
        try {
            logger.info("Creating collection...");
            collection.create();
            logger.info("Collection " + name + " created.");
        } catch (ArangoDBException e) {
            logger.error("Failed to create collection: " + name + "; " +
                    e.getMessage());
            // here we force exit the program if the collection cannot be created
            System.exit(-1);
        }
        return collection;
    }
}
