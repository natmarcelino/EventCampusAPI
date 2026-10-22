package com.eventcampus.repository;

import com.eventcampus.entity.Inscricao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface InscricaoRepository extends JpaRepository<Inscricao, Long> {
    List<Inscricao> findByAlunoId(Long alunoId);
    List<Inscricao> findByEventoId(Long eventoId);
    Optional<Inscricao> findByAlunoIdAndEventoId(Long alunoId, Long eventoId);
    boolean existsByAlunoIdAndEventoId(Long alunoId, Long eventoId);
    boolean existsByEventoId(Long eventoId);

    /** Soma da carga horária dos eventos em que o aluno teve presença confirmada. */
    @Query("SELECT COALESCE(SUM(e.cargaHoraria), 0) FROM Inscricao i JOIN i.evento e " +
           "WHERE i.aluno.id = :alunoId AND i.presencaConfirmada = true")
    Long somarHorasEventos(@Param("alunoId") Long alunoId);
}
