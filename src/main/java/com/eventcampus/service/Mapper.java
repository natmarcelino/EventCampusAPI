package com.eventcampus.service;

import com.eventcampus.entity.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converte entidades em Map (aceita valores nulos, ao contrário de Map.of). */
public final class Mapper {
    private Mapper() {}

    public static Map<String, Object> evento(Evento e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("titulo", e.getTitulo());
        m.put("descricao", e.getDescricao());
        m.put("tipo", e.getTipo());
        m.put("data", e.getData());
        m.put("horarioInicio", e.getHorarioInicio());
        m.put("horarioFim", e.getHorarioFim());
        m.put("local", e.getLocal());
        m.put("cargaHoraria", e.getCargaHoraria());
        m.put("vagas", e.getVagas());
        m.put("status", e.getStatus());
        m.put("qrAtivo", Boolean.TRUE.equals(e.getQrAtivo()));
        m.put("qrExpiraEm", e.getQrExpiraEm());
        m.put("latitude", e.getLatitude());
        m.put("longitude", e.getLongitude());
        m.put("raioMetros", e.getRaioEfetivo());
        if (e.getProfessor() != null) {
            m.put("professorId", e.getProfessor().getId());
            m.put("professor", e.getProfessor().getNome());
        }
        return m;
    }

    public static Map<String, Object> usuario(Usuario u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("nome", u.getNome());
        m.put("email", u.getEmail());
        m.put("matricula", u.getMatricula());
        m.put("curso", u.getCurso());
        m.put("perfil", u.getPerfil());
        m.put("horasTotal", u.getMetaHoras());
        m.put("criadoEm", u.getCriadoEm());
        return m;
    }

    public static Map<String, Object> certificado(Certificado c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("aluno", c.getAluno().getNome());
        m.put("alunoId", c.getAluno().getId());
        m.put("matricula", c.getAluno().getMatricula());
        m.put("curso", c.getAluno().getCurso());
        m.put("tituloCurso", c.getTituloCurso());
        m.put("instituicao", c.getInstituicao() != null ? c.getInstituicao() : "");
        m.put("cargaHoraria", c.getCargaHoraria());
        m.put("status", c.getStatus());
        m.put("ehFinal", Boolean.TRUE.equals(c.getEhFinal()));
        m.put("enviadoEm", c.getEnviadoEm());
        m.put("analisadoEm", c.getAnalisadoEm());
        m.put("analisadoPor", c.getAnalisadoPor() != null ? c.getAnalisadoPor().getNome() : null);
        m.put("motivoReprovacao", c.getMotivoReprovacao() != null ? c.getMotivoReprovacao() : "");
        m.put("temArquivo", c.getArquivoBase64() != null && !c.getArquivoBase64().isBlank());
        m.put("arquivoNome", c.getArquivoNome());
        m.put("codigoValidacao", c.getCodigoValidacao());
        return m;
    }
}
