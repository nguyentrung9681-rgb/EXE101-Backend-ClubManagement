package com.example.clubmanagement.Config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

import java.lang.reflect.Field;

@Configuration
public class PayOSConfig {

    @Value("${PAYOS_CLIENT_ID:${payos.client-id:}}")
    private String clientId;

    @Value("${PAYOS_API_KEY:${payos.api-key:}}")
    private String apiKey;

    @Value("${PAYOS_CHECKSUM_KEY:${payos.checksum-key:}}")
    private String checksumKey;

    @Bean
    public PayOS payOS() {
        PayOS payOS;
        if (clientId == null || clientId.isBlank() 
                || apiKey == null || apiKey.isBlank() 
                || checksumKey == null || checksumKey.isBlank()) {
            payOS = new PayOS("DUMMY_CLIENT_ID", "DUMMY_API_KEY", "DUMMY_CHECKSUM_KEY");
        } else {
            payOS = new PayOS(clientId, apiKey, checksumKey);
        }

        disableFailOnUnknownProperties(payOS);
        return payOS;
    }

    private void disableFailOnUnknownProperties(Object obj) {
        if (obj == null) return;
        try {
            Class<?> clazz = obj.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = null;
                    try {
                        value = field.get(obj);
                    } catch (Exception ignored) {}

                    System.out.println("PayOS Field: " + field.getName() + " -> " + (value != null ? value.getClass().getName() : "null"));

                    if (value instanceof ObjectMapper mapper) {
                        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                        System.out.println("SUCCESSFULLY CONFIGURED OBJECTMAPPER FOR FIELD: " + field.getName());
                    } else if (value != null && field.getType().getName().startsWith("vn.payos")) {
                        disableFailOnUnknownProperties(value);
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {
        }
    }
}
