package com.example.fittrack.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;

@Service
public class ThirdPartyService {
    private final RestTemplate rest;

    public ThirdPartyService() throws Exception {
        this.rest = createUnsafeRestTemplate();
    }

    public String fetchMotivationalQuote() {
        try {
            Map<?, ?> resp = rest.getForObject("https://api.quotable.io/random", Map.class);
            if (resp == null) return "Цитата недоступна зараз";
            Object content = resp.get("content");
            Object author = resp.get("author");
            StringBuilder sb = new StringBuilder();
            if (content != null) sb.append(content.toString());
            if (author != null) sb.append(" — ").append(author.toString());
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Цитата недоступна зараз";
        }
    }

    private RestTemplate createUnsafeRestTemplate() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

        HostnameVerifier allHostsValid = (hostname, session) -> true;
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

        return new RestTemplate();
    }
}
