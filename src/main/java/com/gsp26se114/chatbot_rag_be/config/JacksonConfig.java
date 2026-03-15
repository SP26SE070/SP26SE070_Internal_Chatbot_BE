package com.gsp26se114.chatbot_rag_be.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register JavaTimeModule for LocalDate/LocalDateTime support
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        mapper.registerModule(javaTimeModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Strict Integer deserializer — reject decimals like 4.2
        SimpleModule strictIntegerModule = new SimpleModule();
        strictIntegerModule.addDeserializer(Integer.class,
            new StdDeserializer<Integer>(Integer.class) {
                @Override
                public Integer deserialize(JsonParser p,
                        DeserializationContext ctx) throws IOException {
                    String raw = p.getText();
                    if (raw.contains(".")) {
                        throw new com.fasterxml.jackson.databind.exc.InvalidFormatException(
                            p,
                            "Giá trị '" + raw + "' không hợp lệ. " +
                            "Chỉ chấp nhận số nguyên, không chấp nhận số thập phân.",
                            raw,
                            Integer.class
                        );
                    }
                    try {
                        return Integer.parseInt(raw);
                    } catch (NumberFormatException e) {
                        throw new com.fasterxml.jackson.databind.exc.InvalidFormatException(
                            p,
                            "Giá trị '" + raw + "' không phải số nguyên hợp lệ.",
                            raw,
                            Integer.class
                        );
                    }
                }
            });
        mapper.registerModule(strictIntegerModule);
        return mapper;
    }
}
