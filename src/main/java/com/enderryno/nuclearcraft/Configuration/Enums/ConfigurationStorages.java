package com.enderryno.nuclearcraft.Configuration.Enums;

import com.enderryno.nuclearcraft.CustomItems.ItemInterfaces.GenericItem;

public enum ConfigurationStorages {

    items("items"),
    blocks("blocks"),
    ;


    private final String storageFileName;


    ConfigurationStorages(String storageFileName){
        this.storageFileName = storageFileName;
    }


    public String getStorageFileName() {
        return storageFileName;
    }
}
