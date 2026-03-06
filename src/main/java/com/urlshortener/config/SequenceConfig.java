package com.urlshortener.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SequenceConfig {
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.instance-id}")
    private int instanceId;

    @Value("${app.total-instances}")
    private int totalInstances;

    public SequenceConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void configureSequence() {
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS url_sequence START WITH "
                + instanceId + " INCREMENT BY " + totalInstances);
        jdbcTemplate.execute("ALTER SEQUENCE url_sequence RESTART WITH " +
                getNextValidValue() + " INCREMENT BY " + totalInstances);
    }

    private long getNextValidValue() {
        Long currentMax = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM urls", Long.class);
        if (currentMax == null || currentMax == 0) {
            return instanceId;
        }
        long next = instanceId;
        while (next <= currentMax) {
            next += totalInstances;
        }
        return next;
    }
}
