package com.kvstore;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Main {

    private static int NODE_PORT;
    private static List<Integer> CLUSTER_PORTS;

    // In-memory key–value store (shared, thread-safe)
    private static final KVStore kvStore = new KVStore();

    // Stores cluster configs and return cluster index for corresponding key
    private static ClusterManager clusterManager;

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: <port> <clusterPorts>");
            System.exit(1);
        }

        int myPort = Integer.parseInt(args[0]);
        String[] portStrings = args[1].split(",");

        List<Integer> clusterPorts = new ArrayList<>();
        for (String portString : portStrings) {
            clusterPorts.add(Integer.parseInt(portString));
        }

        NODE_PORT = myPort;
        CLUSTER_PORTS = clusterPorts;

        clusterManager = new ClusterManager(clusterPorts);


        HttpServer server = HttpServer.create(
                new InetSocketAddress(NODE_PORT), 0
        );

        server.createContext("/put", new PutHandler());
        server.createContext("/get", new GetHandler());

        ExecutorService threadPool = Executors.newFixedThreadPool(10);
        server.setExecutor(threadPool); // default thread pool
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");

            server.stop(0); // stop accepting new requests

            threadPool.shutdown(); // stop accepting new tasks

            System.out.println("Shutdown complete.");
        }));

        System.out.println("Node started on port " + NODE_PORT);
        System.out.println("Cluster nodes: " + CLUSTER_PORTS);
    }

    // Handler for PUT operation
    static class PutHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            // Method validation
            if (!exchange.getRequestMethod().equalsIgnoreCase("PUT")) {
                String response = "Method Not Allowed";
                exchange.sendResponseHeaders(405, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            // Parse query parameters
            URI requestUri = exchange.getRequestURI();
            String query = requestUri.getQuery();

            String key = null;
            String value = null;
            boolean isReplicaWrite = false;

            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length != 2) continue;

                    switch (pair[0]) {
                        case "key":
                            key = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                            break;
                        case "value":
                            value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                            break;
                        case "replica":
                            isReplicaWrite = pair[1].equalsIgnoreCase("true");
                            break;
                    }
                }
            }

            // Validate input
            if (key == null || value == null) {
                String response = "Missing key or value";
                exchange.sendResponseHeaders(400, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            // REPLICA WRITES: STORE AND STOP (primary BUG fixed)
            if (isReplicaWrite) {
                kvStore.put(key, value);

                System.out.println(
                        "[REPLICA] Stored key=" + key + " on port " + NODE_PORT
                );

                String response = "Replica stored key=" + key;
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            // Ownership check (ONLY for client writes)
            int ownerPort = clusterManager.getOwnerPort(key);

            if (ownerPort != NODE_PORT) {
                try {
                    String response = RequestForwarder.forwardPut(
                            ownerPort, key, value, false
                    );
                    exchange.sendResponseHeaders(200, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                } catch (Exception e) {
                    String response = "Forwarding failed: " + e.getMessage();
                    exchange.sendResponseHeaders(500, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }
            }

            // PRIMARY WRITE
            kvStore.put(key, value);

            System.out.println(
                    "[PRIMARY] Stored key=" + key + " on port " + NODE_PORT + " (client write)"
            );

            // ASYNC REPLICATION (PRIMARY ONLY)
            int replicaPort = clusterManager.getReplicaPort(key);
            if (replicaPort != NODE_PORT) {
                final String finalKey = key;
                final String finalValue = value;
                final int replicaPortFinal = replicaPort;

                new Thread(() -> {
                    try {
                        System.out.println(
                                "[REPLICA] Replicating key=" + finalKey +
                                        " from port " + NODE_PORT +
                                        " to replica port " + replicaPortFinal
                        );

                        RequestForwarder.forwardPut(
                                replicaPortFinal,
                                finalKey,
                                finalValue,
                                true
                        );

                        System.out.println(
                                "[REPLICA] Replication success for key=" + finalKey +
                                        " on replica port " + replicaPortFinal
                        );

                    } catch (Exception e) {
                        System.err.println(
                                "Replication failed for key " + finalKey + " : " + e.getMessage()
                        );
                    }
                }).start();
            }

            // Respond to client
            String response = "Stored key = " + key;
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }


    // Handler for GET operation
    static class GetHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            // Enforce HTTP method contract
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                String response = "Method Not Allowed";
                exchange.sendResponseHeaders(405, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            // Parse query parameters
            URI requestUri = exchange.getRequestURI();
            String query = requestUri.getQuery();

            String key = null;

            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && pair[0].equals("key")) {
                        key = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                    }
                }
            }

            // Validate input
            if (key == null) {
                String response = "Missing key";
                exchange.sendResponseHeaders(400, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            int primaryPort = clusterManager.getOwnerPort(key);
            int replicaPort = clusterManager.getReplicaPort(key);

            // Case 1: This node is primary
            if (primaryPort == NODE_PORT) {
                String value = kvStore.get(key);
                if (value == null) {
                    exchange.sendResponseHeaders(404, 0);
                } else {
                    exchange.sendResponseHeaders(200, value.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(value.getBytes());
                    }
                }
                return;
            }

            // Case 2: Try primary first
            try {
                String response = RequestForwarder.forwardGet(primaryPort, key);
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            } catch (Exception e) {
                System.err.println(
                        "[GET] Primary unavailable for key=" + key
                );
            }

            // Case 3: If this node is the replica, read locally
            if (replicaPort == NODE_PORT) {
                String value = kvStore.get(key);
                if (value == null) {
                    exchange.sendResponseHeaders(404, 0);
                } else {
                    exchange.sendResponseHeaders(200, value.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(value.getBytes());
                    }
                }
                return;
            }

            // Case 4: Forward to replica
            try {
                String response = RequestForwarder.forwardGet(replicaPort, key);
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } catch (Exception e) {
                exchange.sendResponseHeaders(503, 0);
            }

        }
    }
}
