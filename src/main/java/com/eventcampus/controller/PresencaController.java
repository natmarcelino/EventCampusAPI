package com.eventcampus.controller;

import com.eventcampus.entity.Usuario;
import com.eventcampus.service.Body;
import com.eventcampus.service.PresencaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/presenca")
@RequiredArgsConstructor
public class PresencaController {

    private final PresencaService presencaService;

    // POST /api/presenca/confirmar  { "qrCodigo": "...", "latitude": -16.6, "longitude": -49.2 }
    @PostMapping("/confirmar")
    public Map<String, Object> confirmar(@RequestBody Map<String, Object> body,
                                         @AuthenticationPrincipal Usuario aluno) {
        return presencaService.confirmarPorQR(aluno,
            Body.str(body, "qrCodigo"),
            Body.decimal(body, "latitude"),
            Body.decimal(body, "longitude"));
    }
}
