package com.eventcampus.controller;

import com.eventcampus.entity.*;
import com.eventcampus.exception.ApiException;
import com.eventcampus.repository.*;
import com.eventcampus.service.Body;
import com.eventcampus.service.PresencaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/inscricoes")
@RequiredArgsConstructor
public class InscricaoController {

    private final InscricaoRepository inscricaoRepo;
    private final EventoRepository eventoRepo;
    private final PresencaService presencaService;

    // POST /api/inscricoes/{eventoId} — aluno se inscreve
    @PostMapping("/{eventoId}")
    @PreAuthorize("hasRole('ALUNO')")
    @Transactional
    public ResponseEntity<?> inscrever(@PathVariable Long eventoId,
                                       @AuthenticationPrincipal Usuario aluno) {
        Evento evento = eventoRepo.findById(eventoId)
            .orElseThrow(() -> ApiException.notFound("Evento não encontrado"));
        if (evento.getStatus() != Evento.Status.ATIVO) {
            throw ApiException.badRequest("Este evento não está aceitando inscrições");
        }
        if (inscricaoRepo.existsByAlunoIdAndEventoId(aluno.getId(), eventoId)) {
            throw ApiException.badRequest("Você já está inscrito neste evento");
        }
        long total = eventoRepo.contarInscritos(eventoId);
        if (evento.getVagas() != null && total >= evento.getVagas()) {
            throw ApiException.badRequest("Evento sem vagas disponíveis");
        }
        Inscricao i = Inscricao.builder().aluno(aluno).evento(evento).build();
        inscricaoRepo.save(i);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("mensagem", "Inscrição realizada com sucesso", "id", i.getId()));
    }

    // DELETE /api/inscricoes/{eventoId} — aluno cancela a inscrição (antes da presença)
    @DeleteMapping("/{eventoId}")
    @PreAuthorize("hasRole('ALUNO')")
    @Transactional
    public Map<String, Object> cancelar(@PathVariable Long eventoId,
                                        @AuthenticationPrincipal Usuario aluno) {
        Inscricao i = inscricaoRepo.findByAlunoIdAndEventoId(aluno.getId(), eventoId)
            .orElseThrow(() -> ApiException.notFound("Inscrição não encontrada"));
        if (Boolean.TRUE.equals(i.getPresencaConfirmada())) {
            throw ApiException.badRequest("Não é possível cancelar: presença já confirmada");
        }
        inscricaoRepo.delete(i);
        return Map.of("mensagem", "Inscrição cancelada");
    }

    // GET /api/inscricoes/minhas — inscrições do aluno logado
    @GetMapping("/minhas")
    public List<Map<String, Object>> minhasInscricoes(@AuthenticationPrincipal Usuario aluno) {
        return inscricaoRepo.findByAlunoId(aluno.getId()).stream().map(i -> {
            Evento e = i.getEvento();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("eventoId", e.getId());
            m.put("titulo", e.getTitulo());
            m.put("tipo", e.getTipo());
            m.put("data", e.getData());
            m.put("horarioInicio", e.getHorarioInicio() != null ? e.getHorarioInicio() : "");
            m.put("horarioFim", e.getHorarioFim() != null ? e.getHorarioFim() : "");
            m.put("local", e.getLocal() != null ? e.getLocal() : "");
            m.put("cargaHoraria", e.getCargaHoraria() != null ? e.getCargaHoraria() : 0);
            m.put("status", e.getStatus());
            m.put("qrAtivo", Boolean.TRUE.equals(e.getQrAtivo()));
            m.put("presencaConfirmada", Boolean.TRUE.equals(i.getPresencaConfirmada()));
            m.put("confirmadaEm", i.getConfirmadaEm());
            m.put("inscritoEm", i.getInscritoEm());
            return m;
        }).sorted(Comparator.comparing(m -> m.get("data").toString())).toList();
    }

    // POST /api/inscricoes/confirmar-presenca — mantido por compatibilidade (mesma regra de /api/presenca/confirmar)
    @PostMapping("/confirmar-presenca")
    public Map<String, Object> confirmarPresenca(@RequestBody Map<String, Object> body,
                                                 @AuthenticationPrincipal Usuario aluno) {
        return presencaService.confirmarPorQR(aluno, Body.str(body, "qrCodigo"),
            Body.decimal(body, "latitude"), Body.decimal(body, "longitude"));
    }

    // GET /api/inscricoes/evento/{eventoId} — lista de inscritos (professor dono / admin)
    @GetMapping("/evento/{eventoId}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public List<Map<String, Object>> inscritosPorEvento(@PathVariable Long eventoId,
                                                        @AuthenticationPrincipal Usuario u) {
        Evento e = eventoRepo.findById(eventoId).orElseThrow(() -> ApiException.notFound("Evento não encontrado"));
        verificarDono(e, u);
        return inscricaoRepo.findByEventoId(eventoId).stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("alunoId", i.getAluno().getId());
            m.put("nome", i.getAluno().getNome());
            m.put("email", i.getAluno().getEmail());
            m.put("matricula", i.getAluno().getMatricula());
            m.put("curso", i.getAluno().getCurso() != null ? i.getAluno().getCurso() : "");
            m.put("presencaConfirmada", Boolean.TRUE.equals(i.getPresencaConfirmada()));
            m.put("confirmadaEm", i.getConfirmadaEm());
            m.put("formaConfirmacao", i.getFormaConfirmacao());
            m.put("distanciaMetros", i.getDistanciaMetros());
            m.put("inscritoEm", i.getInscritoEm());
            return m;
        }).sorted(Comparator.comparing(m -> m.get("nome").toString())).toList();
    }

    // PATCH /api/inscricoes/{id}/presenca  { "confirmar": true|false } — professor ajusta manualmente
    @PatchMapping("/{id}/presenca")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    @Transactional
    public Map<String, Object> confirmarManual(@PathVariable Long id,
                                               @RequestBody(required = false) Map<String, Object> body,
                                               @AuthenticationPrincipal Usuario u) {
        Inscricao i = inscricaoRepo.findById(id).orElseThrow(() -> ApiException.notFound("Inscrição não encontrada"));
        verificarDono(i.getEvento(), u);
        Object v = body != null ? body.get("confirmar") : null;
        boolean confirmar = v == null || Boolean.parseBoolean(v.toString());
        i.setPresencaConfirmada(confirmar);
        i.setConfirmadaEm(confirmar ? LocalDateTime.now() : null);
        i.setFormaConfirmacao(confirmar ? "MANUAL" : null);
        i.setDistanciaMetros(null);
        inscricaoRepo.save(i);
        return Map.of("mensagem", confirmar ? "Presença confirmada" : "Presença removida");
    }

    private void verificarDono(Evento e, Usuario u) {
        if (u.getPerfil() == Usuario.Perfil.ADMIN) return;
        if (e.getProfessor() == null || !Objects.equals(e.getProfessor().getId(), u.getId())) {
            throw ApiException.forbidden("Este evento pertence a outro professor");
        }
    }
}
