package com.eventcampus.repository;

import com.eventcampus.entity.Certificado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CertificadoRepository extends JpaRepository<Certificado, Long> {
    List<Certificado> findByAlunoIdOrderByEnviadoEmDesc(Long alunoId);
    List<Certificado> findByEhFinalFalseOrderByEnviadoEmDesc();
    List<Certificado> findByStatusAndEhFinalFalseOrderByEnviadoEmDesc(Certificado.Status status);
    long countByStatusAndEhFinalFalse(Certificado.Status status);
    long countByEhFinalTrue();
    boolean existsByAlunoIdAndEhFinalTrue(Long alunoId);
    Optional<Certificado> findFirstByAlunoIdAndEhFinalTrue(Long alunoId);

    @Query("SELECT COALESCE(SUM(c.cargaHoraria), 0) FROM Certificado c " +
           "WHERE c.aluno.id = :alunoId AND c.status = :status " +
           "AND (c.ehFinal = false OR c.ehFinal IS NULL)")
    Long somarHorasExternas(@Param("alunoId") Long alunoId, @Param("status") Certificado.Status status);

    /** Soma das horas de certificados EXTERNOS aprovados (não conta o certificado final). */
    default Long somarHorasExternasAprovadas(Long alunoId) {
        return somarHorasExternas(alunoId, Certificado.Status.APROVADO);
    }
}
