package com.eventcampus.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * GET /api/status — rota pública para:
 *  - conferir se a API está no ar;
 *  - "acordar" o servidor grátis do Render (o painel e o app chamam ao abrir);
 *  - o UptimeRobot pingar a cada 5 min e não deixar a API dormir.
 */
@RestController
public class StatusController {

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        return Map.of("status", "ok", "horario", LocalDateTime.now().toString());
    }
}