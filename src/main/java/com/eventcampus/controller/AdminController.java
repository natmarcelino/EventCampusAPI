package com.eventcampus.controller;

import com.eventcampus.entity.*;
import com.eventcampus.exception.ApiException;
import com.eventcampus.repository.*;
import com.eventcampus.service.Body;
import com.eventcampus.service.HorasService;
import com.eventcampus.service.Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

/** Rotas da coordenação. Protegidas por perfil ADMIN no SecurityConfig (/api/admin/**). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UsuarioRepository usuarioRepo;
    private final EventoRepository eventoRepo;
    private final InscricaoRepository inscricaoRepo;
    private final CertificadoRepository certRepo;
    private final HorasService horasService;
    private final PasswordEncoder passwordEncoder;

    // GET /api/admin/dashboard — números reais do sistema
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        List<Usuario> alunos = usuarioRepo.findByPerfil(Usuario.Perfil.ALUNO);
        long horasRegistradas = 0;
        long concluidos = 0;
        for (Usuario a : alunos) {
            int h = horasService.horasTotais(a.getId());
            horasRegistradas += h;
            if (h >= a.getMetaHoras()) concluidos++;
        }

        // eventos por mês (últimos 6 meses, contando pela data do evento)
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = hoje.withDayOfMonth(1).minusMonths(5);
        LocalDate fim = hoje.withDayOfMonth(hoje.lengthOfMonth());
        List<Evento> periodo = eventoRepo.findByDataBetween(inicio, fim);
        List<Map<String, Object>> porMes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            LocalDate mes = inicio.plusMonths(i);
            long qtd = periodo.stream()
                .filter(e -> e.getData().getYear() == mes.getYear() && e.getData().getMonth() == mes.getMonth())
                .count();
            String nome = mes.getMonth().getDisplayName(TextStyle.SHORT, new Locale("pt", "BR")).replace(".", "");
            porMes.add(Map.of("mes", nome.substring(0, 1).toUpperCase() + nome.substring(1), "total", qtd));
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alunosAtivos", alunos.size());
        m.put("professores", usuarioRepo.countByPerfil(Usuario.Perfil.PROFESSOR));
        m.put("eventosAtivos", eventoRepo.countByStatus(Evento.Status.ATIVO));
        m.put("eventosConcluidos", eventoRepo.countByStatus(Evento.Status.CONCLUIDO));
        m.put("eventosTotal", eventoRepo.count());
        m.put("inscricoesTotal", inscricaoRepo.count());
        m.put("horasRegistradas", horasRegistradas);
        m.put("alunosConcluidos", concluidos);
        m.put("certsPendentes", certRepo.countByStatusAndEhFinalFalse(Certificado.Status.PENDENTE));
        m.put("certsAprovados", certRepo.countByStatusAndEhFinalFalse(Certificado.Status.APROVADO));
        m.put("certsReprovados", certRepo.countByStatusAndEhFinalFalse(Certificado.Status.REPROVADO));
        m.put("certsFinaisEmitidos", certRepo.countByEhFinalTrue());
        m.put("eventosPorMes", porMes);
        return m;
    }

    // GET /api/admin/eventos — todos os eventos (inclusive rascunhos)
    @GetMapping("/eventos")
    public List<Map<String, Object>> eventos() {
        return eventoRepo.findAllByOrderByDataDesc().stream().map(e -> {
            Map<String, Object> m = Mapper.evento(e);
            m.put("inscritos", eventoRepo.contarInscritos(e.getId()));
            m.put("presentes", eventoRepo.contarPresentes(e.getId()));
            return m;
        }).toList();
    }

    // GET /api/admin/usuarios?perfil=PROFESSOR
    @GetMapping("/usuarios")
    public List<Map<String, Object>> usuarios(@RequestParam(required = false) String perfil) {
        List<Usuario> lista = perfil == null || perfil.isBlank()
            ? usuarioRepo.findAll(org.springframework.data.domain.Sort.by("nome"))
            : usuarioRepo.findByPerfilOrderByNomeAsc(Usuario.Perfil.valueOf(perfil.toUpperCase()));
        return lista.stream().map(Mapper::usuario).toList();
    }

    // POST /api/admin/usuarios — coordenação cadastra professor, admin ou aluno
    @PostMapping("/usuarios")
    @Transactional
    public ResponseEntity<?> criarUsuario(@RequestBody Map<String, Object> body) {
        String nome  = Body.strObrig(body, "nome", "o nome");
        String email = Body.strObrig(body, "email", "o e-mail").toLowerCase();
        String senha = Body.strObrig(body, "senha", "a senha");
        if (senha.length() < 6) throw ApiException.badRequest("A senha deve ter pelo menos 6 caracteres");
        if (usuarioRepo.existsByEmailIgnoreCase(email)) throw ApiException.badRequest("E-mail já cadastrado");
        String perfil = Body.str(body, "perfil");
        Integer meta = Body.inteiro(body, "horasTotal");

        Usuario u = Usuario.builder()
            .nome(nome).email(email)
            .senha(passwordEncoder.encode(senha))
            .matricula(Body.str(body, "matricula") != null ? Body.str(body, "matricula") : "")
            .curso(Body.str(body, "curso"))
            .perfil(Usuario.Perfil.valueOf(perfil != null ? perfil.toUpperCase() : "PROFESSOR"))
            .horasTotal(meta != null && meta > 0 ? meta : Usuario.META_HORAS_PADRAO)
            .build();
        usuarioRepo.save(u);
        return ResponseEntity.status(HttpStatus.CREATED).body(Mapper.usuario(u));
    }
}
