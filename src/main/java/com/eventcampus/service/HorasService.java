package com.eventcampus.service;

import com.eventcampus.entity.Usuario;
import com.eventcampus.repository.CertificadoRepository;
import com.eventcampus.repository.InscricaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Horas complementares do aluno =
 *   carga horária dos eventos com presença confirmada
 * + carga horária dos certificados externos aprovados.
 */
@Service
@RequiredArgsConstructor
public class HorasService {

    private final InscricaoRepository inscricaoRepo;
    private final CertificadoRepository certRepo;

    public int horasEventos(Long alunoId) {
        Long v = inscricaoRepo.somarHorasEventos(alunoId);
        return v == null ? 0 : v.intValue();
    }

    public int horasExternas(Long alunoId) {
        Long v = certRepo.somarHorasExternasAprovadas(alunoId);
        return v == null ? 0 : v.intValue();
    }

    public int horasTotais(Long alunoId) {
        return horasEventos(alunoId) + horasExternas(alunoId);
    }

    public Map<String, Object> resumo(Usuario aluno) {
        int eventos = horasEventos(aluno.getId());
        int externas = horasExternas(aluno.getId());
        int feitas = eventos + externas;
        int meta = aluno.getMetaHoras();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("horasTotal", meta);
        m.put("horasCompletas", feitas);
        m.put("horasEventos", eventos);
        m.put("horasExternas", externas);
        m.put("horasRestantes", Math.max(0, meta - feitas));
        m.put("percentual", Math.min(100, Math.round((feitas * 100.0) / meta)));
        m.put("concluido", feitas >= meta);
        return m;
    }
}
