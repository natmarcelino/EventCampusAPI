package com.eventcampus.controller;

import com.eventcampus.entity.*;
import com.eventcampus.exception.ApiException;
import com.eventcampus.repository.*;
import com.eventcampus.service.Body;
import com.eventcampus.service.HorasService;
import com.eventcampus.service.Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/certificados")
@RequiredArgsConstructor
public class CertificadoController {

    /** Limite do anexo em Base64 (~5 MB de arquivo). */
    private static final int MAX_BASE64 = 7_000_000;

    private final CertificadoRepository certRepo;
    private final UsuarioRepository usuarioRepo;
    private final InscricaoRepository inscricaoRepo;
    private final HorasService horasService;

    // ════════════════ ALUNO ════════════════

    // POST /api/certificados — aluno envia certificado externo
    // { tituloCurso, instituicao, cargaHoraria, arquivoNome?, arquivoTipo?, arquivoBase64? }
    @PostMapping
    @PreAuthorize("hasRole('ALUNO')")
    @Transactional
    public ResponseEntity<?> enviar(@RequestBody Map<String, Object> body,
                                    @AuthenticationPrincipal Usuario aluno) {
        String titulo = Body.strObrig(body, "tituloCurso", "o nome do curso/evento");
        Integer carga = Body.inteiro(body, "cargaHoraria");
        if (carga == null || carga <= 0) throw ApiException.badRequest("Informe uma carga horária válida");
        if (carga > 200) throw ApiException.badRequest("Carga horária acima do permitido para um único certificado (200h)");

        String base64 = Body.str(body, "arquivoBase64");
        if (base64 != null) {
            int virgula = base64.indexOf(',');           // aceita "data:application/pdf;base64,...."
            if (base64.startsWith("data:") && virgula > 0) base64 = base64.substring(virgula + 1);
            if (base64.length() > MAX_BASE64) throw ApiException.badRequest("Arquivo muito grande (máx. 5 MB)");
        }

        Certificado c = Certificado.builder()
            .aluno(aluno)
            .tituloCurso(titulo)
            .instituicao(Body.str(body, "instituicao"))
            .cargaHoraria(carga)
            .status(Certificado.Status.PENDENTE)
            .arquivoNome(Body.str(body, "arquivoNome"))
            .arquivoTipo(Body.str(body, "arquivoTipo"))
            .arquivoBase64(base64)
            .build();
        certRepo.save(c);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("mensagem", "Certificado enviado para análise", "id", c.getId()));
    }

    // GET /api/certificados/meus — certificados do aluno logado (externos + final)
    @GetMapping("/meus")
    public List<Map<String, Object>> meusCertificados(@AuthenticationPrincipal Usuario aluno) {
        return certRepo.findByAlunoIdOrderByEnviadoEmDesc(aluno.getId()).stream().map(Mapper::certificado).toList();
    }

    // GET /api/certificados/{id}/arquivo — anexo (dono do certificado ou admin)
    @GetMapping("/{id}/arquivo")
    public Map<String, Object> arquivo(@PathVariable Long id, @AuthenticationPrincipal Usuario u) {
        Certificado c = buscar(id);
        boolean admin = u.getPerfil() == Usuario.Perfil.ADMIN;
        if (!admin && !Objects.equals(c.getAluno().getId(), u.getId())) {
            throw ApiException.forbidden("Sem permissão para ver este arquivo");
        }
        if (c.getArquivoBase64() == null) throw ApiException.notFound("Este certificado não tem anexo");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", c.getArquivoNome() != null ? c.getArquivoNome() : "certificado");
        m.put("tipo", c.getArquivoTipo() != null ? c.getArquivoTipo() : "application/octet-stream");
        m.put("base64", c.getArquivoBase64());
        return m;
    }

    // ════════════════ ADMIN (coordenação) ════════════════

    // GET /api/certificados?status=PENDENTE|APROVADO|REPROVADO — externos
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> todos(@RequestParam(required = false) String status) {
        List<Certificado> lista = (status == null || status.isBlank() || status.equalsIgnoreCase("TODOS"))
            ? certRepo.findByEhFinalFalseOrderByEnviadoEmDesc()
            : certRepo.findByStatusAndEhFinalFalseOrderByEnviadoEmDesc(Certificado.Status.valueOf(status.toUpperCase()));
        return lista.stream().map(Mapper::certificado).toList();
    }

    // GET /api/certificados/pendentes
    @GetMapping("/pendentes")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> pendentes() {
        return todos("PENDENTE");
    }

    // PATCH /api/certificados/{id}/aprovar  { "cargaHoraria"?: n }  (permite ajustar a carga aceita)
    @PatchMapping("/{id}/aprovar")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Map<String, Object> aprovar(@PathVariable Long id,
                                       @RequestBody(required = false) Map<String, Object> body,
                                       @AuthenticationPrincipal Usuario admin) {
        Certificado c = buscarExterno(id);
        Integer carga = body != null ? Body.inteiro(body, "cargaHoraria") : null;
        if (carga != null && carga > 0) c.setCargaHoraria(carga);
        c.setStatus(Certificado.Status.APROVADO);
        c.setMotivoReprovacao(null);
        c.setAnalisadoEm(LocalDateTime.now());
        c.setAnalisadoPor(admin);
        certRepo.save(c);
        return Map.of("mensagem", "Certificado aprovado");
    }

    // PATCH /api/certificados/{id}/reprovar  { "motivo": "..." }
    @PatchMapping("/{id}/reprovar")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Map<String, Object> reprovar(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        @AuthenticationPrincipal Usuario admin) {
        Certificado c = buscarExterno(id);
        String motivo = body != null ? Body.str(body, "motivo") : null;
        if (motivo == null) throw ApiException.badRequest("Informe o motivo da reprovação");
        c.setStatus(Certificado.Status.REPROVADO);
        c.setMotivoReprovacao(motivo);
        c.setAnalisadoEm(LocalDateTime.now());
        c.setAnalisadoPor(admin);
        certRepo.save(c);
        return Map.of("mensagem", "Certificado reprovado");
    }

    // GET /api/certificados/horas-alunos — progresso de horas de todos os alunos
    @GetMapping("/horas-alunos")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> horasAlunos() {
        return usuarioRepo.findByPerfilOrderByNomeAsc(Usuario.Perfil.ALUNO).stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("nome", a.getNome());
            m.put("matricula", a.getMatricula());
            m.put("curso", a.getCurso() != null ? a.getCurso() : "");
            Map<String, Object> r = horasService.resumo(a);
            m.putAll(r);
            m.put("horasFeitas", r.get("horasCompletas"));
            m.put("status", Boolean.TRUE.equals(r.get("concluido")) ? "Concluído" : "Em andamento");
            m.put("certFinalEmitido", certRepo.existsByAlunoIdAndEhFinalTrue(a.getId()));
            return m;
        }).toList();
    }

    // POST /api/certificados/final/{alunoId} — emitir certificado final (aluno atingiu a meta)
    @PostMapping("/final/{alunoId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Map<String, Object> emitirFinal(@PathVariable Long alunoId,
                                           @AuthenticationPrincipal Usuario admin) {
        if (certRepo.existsByAlunoIdAndEhFinalTrue(alunoId)) {
            throw ApiException.badRequest("Certificado final já emitido para este aluno");
        }
        Usuario aluno = usuarioRepo.findById(alunoId)
            .filter(u -> u.getPerfil() == Usuario.Perfil.ALUNO)
            .orElseThrow(() -> ApiException.notFound("Aluno não encontrado"));
        int horas = horasService.horasTotais(alunoId);
        if (horas < aluno.getMetaHoras()) {
            throw ApiException.badRequest("Aluno ainda não atingiu a meta: " + horas + "h de " + aluno.getMetaHoras() + "h");
        }
        Certificado c = Certificado.builder()
            .aluno(aluno)
            .tituloCurso("Certificado de Conclusão de Horas Complementares")
            .instituicao("Centro Universitário Alfredo Nasser — UNIFAN")
            .cargaHoraria(horas)
            .status(Certificado.Status.APROVADO)
            .ehFinal(true)
            .analisadoPor(admin)
            .analisadoEm(LocalDateTime.now())
            .codigoValidacao(UUID.randomUUID().toString().substring(0, 8).toUpperCase())
            .build();
        certRepo.save(c);
        return dadosFinal(aluno, c);
    }

    // GET /api/certificados/final/{alunoId} — dados do certificado final (para gerar o PDF)
    @GetMapping("/final/{alunoId}")
    public Map<String, Object> verFinal(@PathVariable Long alunoId, @AuthenticationPrincipal Usuario u) {
        if (u.getPerfil() != Usuario.Perfil.ADMIN && !Objects.equals(u.getId(), alunoId)) {
            throw ApiException.forbidden("Sem permissão");
        }
        Certificado c = certRepo.findFirstByAlunoIdAndEhFinalTrue(alunoId)
            .orElseThrow(() -> ApiException.notFound("Certificado final ainda não emitido"));
        return dadosFinal(c.getAluno(), c);
    }

    // GET /api/certificados/validar/{codigo} — público: confere se um certificado final é verdadeiro
    @GetMapping("/validar/{codigo}")
    public Map<String, Object> validar(@PathVariable String codigo) {
        return certRepo.findAll().stream()
            .filter(c -> Boolean.TRUE.equals(c.getEhFinal()) && codigo.equalsIgnoreCase(c.getCodigoValidacao()))
            .findFirst()
            .map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("valido", true);
                m.put("aluno", c.getAluno().getNome());
                m.put("horas", c.getCargaHoraria());
                m.put("emitidoEm", c.getAnalisadoEm());
                return m;
            })
            .orElseThrow(() -> ApiException.notFound("Código de validação não encontrado"));
    }

    // ════════════════ helpers ════════════════

    private Map<String, Object> dadosFinal(Usuario aluno, Certificado c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensagem", "Certificado final emitido!");
        m.put("id", c.getId());
        m.put("alunoId", aluno.getId());
        m.put("aluno", aluno.getNome());
        m.put("matricula", aluno.getMatricula());
        m.put("curso", aluno.getCurso());
        m.put("horasTotais", c.getCargaHoraria());
        m.put("horasEventos", horasService.horasEventos(aluno.getId()));
        m.put("horasExternas", horasService.horasExternas(aluno.getId()));
        m.put("emitidoEm", c.getAnalisadoEm());
        m.put("emitidoPor", c.getAnalisadoPor() != null ? c.getAnalisadoPor().getNome() : "Coordenação Acadêmica");
        m.put("codigoValidacao", c.getCodigoValidacao());
        return m;
    }

    private Certificado buscar(Long id) {
        return certRepo.findById(id).orElseThrow(() -> ApiException.notFound("Certificado não encontrado"));
    }

    private Certificado buscarExterno(Long id) {
        Certificado c = buscar(id);
        if (Boolean.TRUE.equals(c.getEhFinal())) throw ApiException.badRequest("Certificado final não pode ser analisado");
        return c;
    }
}
