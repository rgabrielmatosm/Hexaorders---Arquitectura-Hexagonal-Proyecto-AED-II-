package com.hexaorders.adapter.out.health;

import com.hexaorders.application.port.out.SystemHealthPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/** Active checks of PostgreSQL (SELECT 1) and of the risk engine readiness endpoint. */
public final class DependencyHealthAdapter implements SystemHealthPort {
    private final JdbcTemplate jdbc;
    private final RestClient riskClient;

    public DependencyHealthAdapter(JdbcTemplate jdbc, RestClient riskClient) {
        this.jdbc = jdbc;
        this.riskClient = riskClient;
    }

    @Override
    public boolean databaseUp() {
        try {
            return Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class));
        } catch (RuntimeException error) {
            return false;
        }
    }

    @Override
    public boolean riskEngineUp() {
        try {
            riskClient.get().uri("/health/ready").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }
}
