package com.enderryno.nuclearcraft.database;

import com.enderryno.nuclearcraft.NuclearCraft;
import com.enderryno.nuclearcraft.enums.DatabaseFile;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;

/**
 * This class is responsible for handling all database related tasks.
 * Useful for storing data such as player data, block data, etc.
 */
public class Database {

    static HashMap<DatabaseFile, Database> openedDatabases = new HashMap<>();

    private Connection connection = null;
    private DatabaseFile databaseFile;

    public void connect(DatabaseFile databaseFile) {
        this.databaseFile = databaseFile;

        try {
            if (this.connection != null && this.connection.isClosed())
                return;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Get data folder path of plugin
        String dataFolder = NuclearCraft.instance.getDataFolder().getAbsolutePath();
        // Create the db directory if it doesn't exist
        String dbDirectory = dataFolder + "/db";
        File dbDir = new File(dbDirectory);
        if (!dbDir.exists()) {
            if (!dbDir.mkdir()) {
                NuclearCraft.instance.getLogger().warning("Failed to create database directory!");
            }
        }

        String url = "jdbc:sqlite:" + dbDirectory + "/" + databaseFile.getFileName();

        try {
            this.connection = DriverManager.getConnection(url);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void disconnect() {
        try {
            if (this.connection != null) {
                this.connection.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        return this.connection;
    }

    public static void disconnectAll() {
        for(Database database : openedDatabases.values()) {
            try {
                if (database != null) {
                    database.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

