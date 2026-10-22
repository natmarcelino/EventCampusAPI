package com.eventcampus.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "eventos")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Evento {

    /** Raio padrão (em metros) aceito no check-in por GPS. */
    public static final int RAIO_PADRAO_METROS = 100;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Enumerated(EnumType.STRING)
    private Tipo tipo;

    @Column(nullable = false)
    private LocalDate data;

    private LocalTime horarioInicio;
    private LocalTime horarioFim;

    private String local;

    @Column(name = "carga_horaria")
    private Integer cargaHoraria;

    private Integer vagas;

    @Enumerated(EnumType.STRING)
    private Status status;

    // ── Localização (para validar o check-in por GPS / Haversine) ──
    private Double latitude;
    private Double longitude;

    @Column(name = "raio_metros")
    private Integer raioMetros;

    // ── QR Code ──
    @Column(name = "qr_codigo", unique = true)
    private String qrCodigo;

    @Builder.Default
    @Column(name = "qr_ativo")
    private Boolean qrAtivo = false;

    /** Momento em que o QR deixa de aceitar presenças (null = sem limite). */
    @Column(name = "qr_expira_em")
    private LocalDateTime qrExpiraEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_id")
    private Usuario professor;

    @Builder.Default
    @Column(name = "criado_em")
    private LocalDateTime criadoEm = LocalDateTime.now();

    public enum Tipo { PALESTRA, WORKSHOP, CURSO, SEMINARIO }

    /**
     * RASCUNHO  → criado, ainda não visível para os alunos
     * ATIVO     → publicado, aceita inscrições e presença
     * CONCLUIDO → realizado; presenças contam horas
     * ENCERRADO → cancelado/fechado
     */
    public enum Status { RASCUNHO, ATIVO, CONCLUIDO, ENCERRADO }

    public int getRaioEfetivo() {
        return raioMetros != null && raioMetros > 0 ? raioMetros : RAIO_PADRAO_METROS;
    }

    public boolean temLocalizacao() {
        return latitude != null && longitude != null;
    }
}
