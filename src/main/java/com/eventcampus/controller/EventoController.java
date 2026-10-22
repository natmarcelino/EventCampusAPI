package com.eventcampus.controller;

import com.eventcampus.entity.*;
import com.eventcampus.exception.ApiException;
import com.eventcampus.repository.*;
import com.eventcampus.service.Body;
import com.eventcampus.service.Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class EventoController {

    private final EventoRepository eventoRepo;
    private final InscricaoRepository inscricaoRepo;

    // ════════════════ ALUNO / TODOS ════════════════

    // GET /api/eventos — eventos visíveis para o aluno (tudo, menos RASCUNHO)
    // ?status=ATIVO filtra por um status específico
    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) String status) {
        List<Evento> eventos;
        if (status != null && !status.isBlank()) {
            Evento.Status s = Evento.Status.valueOf(status.toUpperCase());
            if (s == Evento.Status.RASCUNHO) return List.of();
            eventos = eventoRepo.findByStatusInOrderByDataAsc(List.of(s));
        } else {
            eventos = eventoRepo.findByStatusInOrderByDataAsc(
                List.of(Evento.Status.ATIVO, Evento.Status.CONCLUIDO, Evento.Status.ENCERRADO));
        }
        return eventos.stream().map(this::comContagem).toList();
    }

    // GET /api/eventos/{id}
    @GetMapping("/{id}")
    public Map<String, Object> buscar(@PathVariable Long id) {
        return comContagem(buscarEvento(id));
    }

    // ════════════════ PROFESSOR / ADMIN ════════════════

    // GET /api/eventos/meus — eventos do professor logado (admin vê todos)
    @GetMapping("/meus")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public List<Map<String, Object>> meusEventos(@AuthenticationPrincipal Usuario u) {
        List<Evento> eventos = u.getPerfil() == Usuario.Perfil.ADMIN
            ? eventoRepo.findAllByOrderByDataDesc()
            : eventoRepo.findByProfessorIdOrderByDataDesc(u.getId());
        return eventos.stream().map(this::comContagem).toList();
    }

    // POST /api/eventos — criar evento (nasce como RASCUNHO, a menos que venha "status")
    @PostMapping
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    @Transactional
    public ResponseEntity<?> criar(@RequestBody Map<String, Object> body,
                                   @AuthenticationPrincipal Usuario u) {
        Evento e = Evento.builder()
            .status(Evento.Status.RASCUNHO)
            .professor(u)
            .qrCodigo(UUID.randomUUID().toString())
            .qrAtivo(false)
            .build();
        aplicarCampos(e, body, true);
        eventoRepo.save(e);
        return ResponseEntity.status(HttpStatus.CREATED).body(comContagem(e));
    }

    // PUT /api/eventos/{id} — editar evento
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    @Transactional
    public Map<String, Object> editar(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                      @AuthenticationPrincipal Usuario u) {
        Evento e = buscarEventoDoUsuario(id, u);
        aplicarCampos(e, body, false);
        eventoRepo.save(e);
        return comContagem(e);
    }

    // PATCH /api/eventos/{id}/status  { "status": "ATIVO" | "CONCLUIDO" | "ENCERRADO" | "RASCUNHO" }
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    @Transactional
    public Map<String, Object> mudarStatus(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                           @AuthenticationPrincipal Usuario u) {
        Evento e = buscarEventoDoUsuario(id, u);
        String s = Body.strObrig(body, "status", "o novo status");
        Evento.Status novo = Evento.Status.valueOf(s.toUpperCase());
        e.setStatus(novo);
        if (novo != Evento.Status.ATIVO) e.setQrAtivo(false); // fora de ATIVO não aceita presença
        eventoRepo.save(e);
        return comContagem(e);
    }

    // DELETE /api/eventos/{id} — só se ninguém se inscreveu
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    @Transactional
    public Map<String, Object> excluir(@PathVariable Long id, @AuthenticationPrincipal Usuario u) {
        Evento e = buscarEventoDoUsuario(id, u);
        if (inscricaoRepo.existsByEventoId(id)) {
            throw ApiException.badRequest("Evento já tem inscritos. Use \"Encerrar\" em vez de excluir.");
        }
        eventoRepo.delete(e);
        return Map.of("mensagem", "Evento excluído");
    }

    // GET /api/eventos/{id}/qr — código do QR (só professor dono / admin)
    @GetMapping("/{id}/qr")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    @Transactional
    public Map<String, Object> qr(@PathVariable Long id, @AuthenticationPrincipal Usuario u) {
        Evento e = buscarEventoDoUsuario(id, u);
        expirarSeNecessario(e);
        return qrInfo(e);
    }

    // PATCH /api/eventos/{id}/ativar-qr
    // body (tudo opcional): { latitude, longitude, raioMetros, minutos }
    //  - latitude/longitude: local do evento (o painel web manda a localização do navegador do professor)
    //  - raioMetros: distância máxima aceita no check-in (padrão 100 m)
    //  - minutos: por quanto tempo o QR aceita presença (vazio = até encerrar)
    @PatchMapping("/{id}/ativar-qr")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    @Transactional
    public Map<String, Object> ativarQR(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        @AuthenticationPrincipal Usuario u) {
        Evento e = buscarEventoDoUsuario(id, u);
        if (e.getStatus() == Evento.Status.ENCERRADO || e.getStatus() == Evento.Status.CONCLUIDO) {
            throw ApiException.badRequest("Evento " + e.getStatus().name().toLowerCase() + " não aceita presença");
        }
        Map<String, Object> b = body != null ? body : Map.of();
        Double lat = Body.decimal(b, "latitude");
        Double lon = Body.decimal(b, "longitude");
        if (lat != null && lon != null) { e.setLatitude(lat); e.setLongitude(lon); }
        Integer raio = Body.inteiro(b, "raioMetros");
        if (raio != null && raio > 0) e.setRaioMetros(raio);
        Integer minutos = Body.inteiro(b, "minutos");
        e.setQrExpiraEm(minutos != null && minutos > 0 ? LocalDateTime.now().plusMinutes(minutos) : null);

        if (e.getQrCodigo() == null) e.setQrCodigo(UUID.randomUUID().toString());
        e.setQrAtivo(true);
        e.setStatus(Evento.Status.ATIVO);
        eventoRepo.save(e);

        Map<String, Object> m = qrInfo(e);
        m.put("mensagem", "QR Code ativado com sucesso");
        return m;
    }

    // PATCH /api/eventos/{id}/encerrar-qr
    @PatchMapping("/{id}/encerrar-qr")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    @Transactional
    public Map<String, Object> encerrarQR(@PathVariable Long id, @AuthenticationPrincipal Usuario u) {
        Evento e = buscarEventoDoUsuario(id, u);
        e.setQrAtivo(false);
        eventoRepo.save(e);
        Map<String, Object> m = qrInfo(e);
        m.put("mensagem", "Presença encerrada");
        return m;
    }

    // PATCH /api/eventos/{id}/regenerar-qr — novo código (o antigo para de funcionar: anti-print/foto)
    @PatchMapping("/{id}/regenerar-qr")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    @Transactional
    public Map<String, Object> regenerarQR(@PathVariable Long id, @AuthenticationPrincipal Usuario u) {
        Evento e = buscarEventoDoUsuario(id, u);
        e.setQrCodigo(UUID.randomUUID().toString());
        eventoRepo.save(e);
        Map<String, Object> m = qrInfo(e);
        m.put("mensagem", "Novo QR Code gerado");
        return m;
    }

    // ════════════════ helpers ════════════════

    private Evento buscarEvento(Long id) {
        return eventoRepo.findById(id).orElseThrow(() -> ApiException.notFound("Evento não encontrado"));
    }

    /** Professor só mexe nos próprios eventos; admin mexe em todos. */
    private Evento buscarEventoDoUsuario(Long id, Usuario u) {
        Evento e = buscarEvento(id);
        boolean admin = u.getPerfil() == Usuario.Perfil.ADMIN;
        boolean dono = e.getProfessor() != null && Objects.equals(e.getProfessor().getId(), u.getId());
        if (!admin && !dono) throw ApiException.forbidden("Este evento pertence a outro professor");
        return e;
    }

    private void expirarSeNecessario(Evento e) {
        if (Boolean.TRUE.equals(e.getQrAtivo()) && e.getQrExpiraEm() != null
                && LocalDateTime.now().isAfter(e.getQrExpiraEm())) {
            e.setQrAtivo(false);
            eventoRepo.save(e);
        }
    }

    private Map<String, Object> qrInfo(Evento e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventoId", e.getId());
        m.put("qrCodigo", e.getQrCodigo());
        m.put("qrAtivo", Boolean.TRUE.equals(e.getQrAtivo()));
        m.put("qrExpiraEm", e.getQrExpiraEm());
        // segundos até fechar a presença (null = sem limite) — evita diferença de fuso/relógio no navegador
        m.put("segundosRestantes", e.getQrExpiraEm() == null ? null
            : Math.max(0, java.time.Duration.between(LocalDateTime.now(), e.getQrExpiraEm()).getSeconds()));
        m.put("latitude", e.getLatitude());
        m.put("longitude", e.getLongitude());
        m.put("raioMetros", e.getRaioEfetivo());
        m.put("presentes", eventoRepo.contarPresentes(e.getId()));
        m.put("inscritos", eventoRepo.contarInscritos(e.getId()));
        return m;
    }

    private Map<String, Object> comContagem(Evento e) {
        Map<String, Object> m = Mapper.evento(e);
        long inscritos = e.getId() != null ? eventoRepo.contarInscritos(e.getId()) : 0;
        long presentes = e.getId() != null ? eventoRepo.contarPresentes(e.getId()) : 0;
        int vagas = e.getVagas() != null ? e.getVagas() : 0;
        m.put("inscritos", inscritos);
        m.put("presentes", presentes);
        m.put("vagasRestantes", Math.max(0, vagas - inscritos));
        return m;
    }

    private void aplicarCampos(Evento e, Map<String, Object> b, boolean criando) {
        String titulo = Body.str(b, "titulo");
        if (criando && titulo == null) throw ApiException.badRequest("Informe o título do evento");
        if (titulo != null) e.setTitulo(titulo);

        if (b.containsKey("descricao")) e.setDescricao(Body.str(b, "descricao"));

        String tipo = Body.str(b, "tipo");
        if (tipo != null) e.setTipo(Evento.Tipo.valueOf(normalizarTipo(tipo)));
        else if (criando) e.setTipo(Evento.Tipo.PALESTRA);

        String data = Body.str(b, "data");
        if (criando && data == null) throw ApiException.badRequest("Informe a data do evento");
        if (data != null) e.setData(LocalDate.parse(data));

        if (b.containsKey("horarioInicio")) e.setHorarioInicio(hora(Body.str(b, "horarioInicio")));
        if (b.containsKey("horarioFim"))    e.setHorarioFim(hora(Body.str(b, "horarioFim")));
        if (e.getHorarioInicio() != null && e.getHorarioFim() != null
                && e.getHorarioFim().isBefore(e.getHorarioInicio())) {
            throw ApiException.badRequest("O horário de término deve ser depois do início");
        }

        if (b.containsKey("local")) e.setLocal(Body.str(b, "local"));

        Integer carga = Body.inteiro(b, "cargaHoraria");
        if (carga != null) {
            if (carga <= 0) throw ApiException.badRequest("A carga horária deve ser maior que zero");
            e.setCargaHoraria(carga);
        } else if (criando) e.setCargaHoraria(1);

        Integer vagas = Body.inteiro(b, "vagas");
        if (vagas != null) {
            if (vagas <= 0) throw ApiException.badRequest("O número de vagas deve ser maior que zero");
            if (!criando && e.getId() != null && vagas < eventoRepo.contarInscritos(e.getId())) {
                throw ApiException.badRequest("Já existem mais inscritos do que " + vagas + " vagas");
            }
            e.setVagas(vagas);
        } else if (criando) e.setVagas(50);

        Integer raio = Body.inteiro(b, "raioMetros");
        if (raio != null && raio > 0) e.setRaioMetros(raio);
        Double lat = Body.decimal(b, "latitude");
        Double lon = Body.decimal(b, "longitude");
        if (lat != null && lon != null) { e.setLatitude(lat); e.setLongitude(lon); }

        String status = Body.str(b, "status");
        if (status != null) e.setStatus(Evento.Status.valueOf(status.toUpperCase()));
    }

    private static String normalizarTipo(String t) {
        String s = t.trim().toUpperCase()
            .replace("Á", "A").replace("Ã", "A").replace("É", "E").replace("Í", "I");
        return s;
    }

    private static LocalTime hora(String s) {
        if (s == null) return null;
        return LocalTime.parse(s.length() == 5 ? s : s.substring(0, Math.min(8, s.length())));
    }
}
