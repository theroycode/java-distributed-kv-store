package com.kvstore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class RequestForwarder {
    public static String forwardPut(int port, String key, String value) throws Exception {
        String urlStr = "http://localhost:" + port + "/put?key=" + key + "&value=" + value;
        URL url = new URL(urlStr);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");

        return readResponse(conn);
    }
    public static String forwardGet(int port, String key) throws Exception {
        String urlStr = "http://localhost:" + port + "/get?key=" + key;
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        return readResponse(conn);
    }
    public static String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream())
        );
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        return response.toString();
    }
}
