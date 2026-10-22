package com.eventcampus.controller;

import com.eventcampus.entity.*;
import com.eventcampus.exception.ApiException;
import com.eventcampus.repository.*;
import com.eventcampus.service.Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

/** RF-ADM-003 — relatório de presença de um evento (o painel exporta em PDF/Excel). */
@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private final EventoRepository eventoRepo;
    private final InscricaoRepository inscricaoRepo;

    // GET /api/relatorios/evento/{id}
    @GetMapping("/evento/{id}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public Map<String, Object> evento(@PathVariable Long id, @AuthenticationPrincipal Usuario u) {
        Evento e = eventoRepo.findById(id).orElseThrow(() -> ApiException.notFound("Evento não encontrado"));
        if (u.getPerfil() != Usuario.Perfil.ADMIN
                && (e.getProfessor() == null || !Objects.equals(e.getProfessor().getId(), u.getId()))) {
            throw ApiException.forbidden("Este evento pertence a outro professor");
        }
        List<Inscricao> inscricoes = inscricaoRepo.findByEventoId(id);
        long presentes = inscricoes.stream().filter(i -> Boolean.TRUE.equals(i.getPresencaConfirmada())).count();
        long viaQR = inscricoes.stream().filter(i -> "QR_CODE".equals(i.getFormaConfirmacao())).count();

        // presença por curso
        Map<String, List<Inscricao>> porCurso = inscricoes.stream().collect(Collectors.groupingBy(
            i -> i.getAluno().getCurso() == null || i.getAluno().getCurso().isBlank() ? "Não informado" : i.getAluno().getCurso(),
            TreeMap::new, Collectors.toList()));
        List<Map<String, Object>> cursos = new ArrayList<>();
        porCurso.forEach((curso, lista) -> {
            long p = lista.stream().filter(i -> Boolean.TRUE.equals(i.getPresencaConfirmada())).count();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("curso", curso);
            c.put("inscritos", lista.size());
            c.put("presentes", p);
            c.put("percentual", inscricoes.isEmpty() ? 0 : Math.round(lista.size() * 100.0 / inscricoes.size()));
            cursos.add(c);
        });

        List<Map<String, Object>> alunos = inscricoes.stream()
            .sorted(Comparator.comparing(i -> i.getAluno().getNome()))
            .map(i -> {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("nome", i.getAluno().getNome());
                a.put("matricula", i.getAluno().getMatricula());
                a.put("curso", i.getAluno().getCurso());
                a.put("presente", Boolean.TRUE.equals(i.getPresencaConfirmada()));
                a.put("confirmadaEm", i.getConfirmadaEm());
                a.put("forma", i.getFormaConfirmacao());
                a.put("distanciaMetros", i.getDistanciaMetros());
                return a;
            }).toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evento", Mapper.evento(e));
        m.put("inscritos", inscricoes.size());
        m.put("presentes", presentes);
        m.put("ausentes", inscricoes.size() - presentes);
        m.put("taxaPresenca", inscricoes.isEmpty() ? 0 : Math.round(presentes * 100.0 / inscricoes.size()));
        m.put("confirmadosViaQR", viaQR);
        m.put("confirmadosManual", presentes - viaQR);
        m.put("horasGeradas", presentes * (e.getCargaHoraria() != null ? e.getCargaHoraria() : 0));
        m.put("porCurso", cursos);
        m.put("alunos", alunos);
        return m;
    }
}
