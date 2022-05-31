package com.enderryno.nuclearcraft.Configuration.Enums;

import com.enderryno.nuclearcraft.CustomItems.ItemInterfaces.GenericItem;

public enum ConfigurationStorages {

    items("items"),
    blocks("blocks"),
    ;


    private final String storageFileName;
    private final String configPath;


    ConfigurationStorages(String storageFileName, String configPath){
        this.storageFileName = storageFileName;
        this.configPath = configPath;
    }

    ConfigurationStorages(String storageFileName){
        this(storageFileName, "/");
    }


    public String getStorageFileName() {
        return storageFileName;
    }

    public String getStoragePath() {
        return configPath;
    }

}
