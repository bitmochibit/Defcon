package com.enderryno.nuclearcraft.enums;

public enum ConfigurationStorages {

    items("items"),
    blocks("blocks"),
    config("config"),
    playerStructures("player_structures");


    private final String storageFileName;
    private final String configPath;


    ConfigurationStorages(String storageFileName, String configPath){
        this.storageFileName = storageFileName;
        this.configPath = configPath;
    }

    ConfigurationStorages(String storageFileName){
        this(storageFileName, "");
    }


    public String getStorageFileName() {
        return storageFileName;
    }

    public String getStoragePath() {
        return configPath;
    }

}
