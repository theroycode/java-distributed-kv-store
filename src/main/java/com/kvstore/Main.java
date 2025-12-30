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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



public class Main {

    // In-memory key–value store (shared, thread-safe)
    private static final KVStore kvStore = new KVStore();

    public static void main(String[] args) throws IOException {

        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080), 0
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

        System.out.println("KV Store running on port 8080");
    }

    // Handler for PUT operation
    static class PutHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String method = exchange.getRequestMethod();

            if (!method.equalsIgnoreCase("PUT")) {
                String response = "Method Not Allowed";
                exchange.sendResponseHeaders(405, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            URI requestUri = exchange.getRequestURI();
            String query = requestUri.getQuery();

            String key = null;
            String value = null;

            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] pair = param.split("=");
                    if (pair.length == 2) {
                        if (pair[0].equals("key")) {
                            key = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                        } else if (pair[0].equals("value")) {
                            value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                        }
                    }
                }
            }

            String response;

            if (key == null || value == null) {
                response = "Missing key or value";
                exchange.sendResponseHeaders(400, response.length());
            } else {
                kvStore.put(key, value);
                response = "Stored key=" + key;
                exchange.sendResponseHeaders(200, response.length());
            }

            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    // Handler for GET operation
    static class GetHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String method = exchange.getRequestMethod();

            if (!method.equalsIgnoreCase("GET")) {
                String response = "Method Not Allowed";
                exchange.sendResponseHeaders(405, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            URI requestUri = exchange.getRequestURI();
            String query = requestUri.getQuery();

            String key = null;

            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && pair[0].equals("key")) {
                        key = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                    }
                }
            }

            String response;

            if (key == null) {
                response = "Missing key";
                exchange.sendResponseHeaders(400, response.length());
            } else {
                String value = kvStore.get(key);
                if (value == null) {
                    response = "Key not found";
                    exchange.sendResponseHeaders(404, response.length());
                } else {
                    response = value;
                    exchange.sendResponseHeaders(200, response.length());
                }
            }

            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
