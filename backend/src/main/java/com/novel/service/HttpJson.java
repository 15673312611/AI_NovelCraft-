package com.novel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

class HttpJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    static Map<String, Object> post(String url, String apiKey, Map<String, Object> body) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(15000);
        f.setReadTimeout(120000);
        RestTemplate rt = new RestTemplate(f);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) {
            h.setBearerAuth(apiKey);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, h);
        return rt.postForObject(url, entity, Map.class);
    }

    @SuppressWarnings("unchecked")
    static <T> T read(String json, Class<T> clazz) throws Exception {
        return (T) MAPPER.readValue(json, clazz);
    }
}


