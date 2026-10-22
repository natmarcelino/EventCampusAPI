package com.eventcampus.service;

import com.eventcampus.entity.*;
import com.eventcampus.exception.ApiException;
import com.eventcampus.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Check-in por QR Code com validação de localização (Haversine).
 *
 * Regras:
 *  1. O QR precisa existir (código atual do evento) e estar ativo/não expirado.
 *  2. O aluno precisa estar inscrito e ainda não ter presença.
 *  3. Se o evento tem localização cadastrada, o aluno precisa enviar o GPS
 *     e estar dentro do raio permitido. Isso impede que alguém confirme
 *     presença de casa usando foto/print do QR Code.
 */
@Service
@RequiredArgsConstructor
public class PresencaService {

    private final EventoRepository eventoRepo;
    private final InscricaoRepository inscricaoRepo;

    @Transactional
    public Map<String, Object> confirmarPorQR(Usuario aluno, String qrCodigo, Double latitude, Double longitude) {
        if (aluno.getPerfil() != Usuario.Perfil.ALUNO) {
            throw ApiException.forbidden("Somente alunos confirmam presença pelo QR Code");
        }
        if (qrCodigo == null || qrCodigo.isBlank()) throw ApiException.badRequest("QR Code inválido");

        Evento evento = eventoRepo.findByQrCodigo(qrCodigo.trim())
            .orElseThrow(() -> ApiException.badRequest("QR Code inválido ou desatualizado"));

        if (evento.getQrExpiraEm() != null && LocalDateTime.now().isAfter(evento.getQrExpiraEm())) {
            evento.setQrAtivo(false);
            eventoRepo.save(evento);
        }
        if (!Boolean.TRUE.equals(evento.getQrAtivo())) {
            throw ApiException.badRequest("A presença deste evento não está aberta no momento");
        }

        Inscricao inscricao = inscricaoRepo.findByAlunoIdAndEventoId(aluno.getId(), evento.getId())
            .orElseThrow(() -> ApiException.badRequest("Você não está inscrito neste evento"));
        if (Boolean.TRUE.equals(inscricao.getPresencaConfirmada())) {
            throw ApiException.badRequest("Sua presença já foi confirmada neste evento");
        }

        Double distancia = null;
        if (evento.temLocalizacao()) {
            if (latitude == null || longitude == null) {
                throw ApiException.badRequest("Ative a localização do celular para confirmar a presença");
            }
            distancia = GeoUtil.distanciaMetros(evento.getLatitude(), evento.getLongitude(), latitude, longitude);
            if (distancia > evento.getRaioEfetivo()) {
                throw ApiException.badRequest(String.format(
                    "Você está a %d m do local do evento. É preciso estar a no máximo %d m.",
                    Math.round(distancia), evento.getRaioEfetivo()));
            }
        }

        inscricao.setPresencaConfirmada(true);
        inscricao.setConfirmadaEm(LocalDateTime.now());
        inscricao.setFormaConfirmacao("QR_CODE");
        inscricao.setDistanciaMetros(distancia != null ? Math.round(distancia * 10) / 10.0 : null);
        inscricaoRepo.save(inscricao);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensagem", "Presença confirmada em \"" + evento.getTitulo() + "\"! +"
            + (evento.getCargaHoraria() != null ? evento.getCargaHoraria() : 0) + "h complementares");
        m.put("evento", evento.getTitulo());
        m.put("eventoId", evento.getId());
        m.put("cargaHoraria", evento.getCargaHoraria());
        m.put("distanciaMetros", inscricao.getDistanciaMetros());
        m.put("horario", evento.getHorarioInicio());
        return m;
    }
}
