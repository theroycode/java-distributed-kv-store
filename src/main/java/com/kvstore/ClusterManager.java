package com.kvstore;

import java.util.List;

public class ClusterManager {

    private final List<Integer> clusterPorts;

    public ClusterManager(List<Integer> clusterPorts) {
        this.clusterPorts = clusterPorts;
    }

    // Decide which node owns a given key
    public int getOwnerPort(String key) {
        int hash = key.hashCode();
        int index = Math.abs(hash) % clusterPorts.size();
        return clusterPorts.get(index);
    }
}
