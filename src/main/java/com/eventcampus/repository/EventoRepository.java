package com.eventcampus.repository;

import com.eventcampus.entity.Evento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventoRepository extends JpaRepository<Evento, Long> {
    List<Evento> findByProfessorIdOrderByDataDesc(Long professorId);
    List<Evento> findByStatus(Evento.Status status);
    List<Evento> findByStatusInOrderByDataAsc(Collection<Evento.Status> status);
    List<Evento> findAllByOrderByDataDesc();
    Optional<Evento> findByQrCodigo(String qrCodigo);
    long countByStatus(Evento.Status status);
    List<Evento> findByDataBetween(LocalDate inicio, LocalDate fim);

    @Query("SELECT COUNT(i) FROM Inscricao i WHERE i.evento.id = :eventoId")
    Long contarInscritos(@Param("eventoId") Long eventoId);

    @Query("SELECT COUNT(i) FROM Inscricao i WHERE i.evento.id = :eventoId AND i.presencaConfirmada = true")
    Long contarPresentes(@Param("eventoId") Long eventoId);
}
