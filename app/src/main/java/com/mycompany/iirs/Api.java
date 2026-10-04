package com.mycompany.iirs;

import org.json.JSONObject;
import java.io.*;
import java.net.*;

/** Minimal HTTP client for the local IIRS Flask server. Session cookie based. */
public class Api {
    public static String base = "http://127.0.0.1:5000";

    static { CookieHandler.setDefault(new CookieManager()); }

    public static String get(String path) throws Exception { return call("GET", path, null); }

    /** Only used for /api/login and /api/logout. The app has no other write calls. */
    public static String post(String path, String json) throws Exception { return call("POST", path, json); }

    private static String call(String method, String path, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(8000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("X-IIRS-Client", "mobile-readonly");
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                OutputStream os = c.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }
            int code = c.getResponseCode();
            InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
            }
            String s = sb.toString();
            if (code >= 400) {
                String msg = "Error " + code;
                try { msg = new JSONObject(s).optString("error", msg); } catch (Exception ignored) {}
                throw new Exception(msg);
            }
            return s;
        } finally {
            c.disconnect();
        }
    }
        }
