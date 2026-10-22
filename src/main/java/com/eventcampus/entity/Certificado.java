package com.eventcampus.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "certificados")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Certificado {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Usuario aluno;

    // Certificado externo (enviado pelo aluno)
    @Column(name = "titulo_curso")
    private String tituloCurso;

    private String instituicao;

    @Column(name = "carga_horaria")
    private Integer cargaHoraria;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "motivo_reprovacao")
    private String motivoReprovacao;

    // Anexo (opcional) — guardado em Base64 para simplificar
    @Column(name = "arquivo_nome")
    private String arquivoNome;

    @Column(name = "arquivo_tipo")
    private String arquivoTipo;

    @JsonIgnore
    @Column(name = "arquivo_base64", columnDefinition = "TEXT")
    private String arquivoBase64;

    @Builder.Default
    @Column(name = "enviado_em")
    private LocalDateTime enviadoEm = LocalDateTime.now();

    @Column(name = "analisado_em")
    private LocalDateTime analisadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analisado_por")
    private Usuario analisadoPor;

    // Certificado final (emitido pela coordenação)
    @Builder.Default
    @Column(name = "eh_final")
    private Boolean ehFinal = false;

    @Column(name = "codigo_validacao")
    private String codigoValidacao;

    public enum Status { PENDENTE, APROVADO, REPROVADO }
}
