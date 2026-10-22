package com.eventcampus.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inscricoes",
  uniqueConstraints = @UniqueConstraint(columnNames = {"aluno_id", "evento_id"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Inscricao {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Usuario aluno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Builder.Default
    @Column(name = "presenca_confirmada")
    private Boolean presencaConfirmada = false;

    @Column(name = "confirmada_em")
    private LocalDateTime confirmadaEm;

    /** QR_CODE (aluno escaneou) ou MANUAL (professor confirmou). */
    @Column(name = "forma_confirmacao")
    private String formaConfirmacao;

    @Column(name = "distancia_metros")
    private Double distanciaMetros;

    @Builder.Default
    @Column(name = "inscrito_em")
    private LocalDateTime inscritoEm = LocalDateTime.now();
}
