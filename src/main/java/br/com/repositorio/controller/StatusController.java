package br.com.repositorio.controller;

import br.com.repositorio.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/status")
public class StatusController {

    private final DataSource dataSource;
    private final StorageService storageService;

    public StatusController(DataSource dataSource, StorageService storageService) {
        this.dataSource = dataSource;
        this.storageService = storageService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> verificar() {
        boolean bancoOk = testarBanco();
        boolean storageOk = storageService.testarConexao();
        boolean ok = bancoOk && storageOk;

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("status", ok ? "UP" : "DEGRADED");
        corpo.put("banco", bancoOk ? "UP" : "DOWN");
        corpo.put("storage", storageOk ? "UP" : "DOWN");

        return ResponseEntity
                .status(ok ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(corpo);
    }

    private boolean testarBanco() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }
}
