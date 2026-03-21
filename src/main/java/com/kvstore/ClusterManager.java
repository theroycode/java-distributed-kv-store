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
        int index = (key.hashCode() & Integer.MAX_VALUE) % clusterPorts.size();
        return clusterPorts.get(index);
    }

    public int getReplicaPort(String key) {
        int hash = key.hashCode();
        int ownerPortIndex = (key.hashCode() & Integer.MAX_VALUE) % clusterPorts.size();
        int replicaPortIndex = (ownerPortIndex + 1) % clusterPorts.size();
        return clusterPorts.get(replicaPortIndex);
    }
}
