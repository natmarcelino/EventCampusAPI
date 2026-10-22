package com.eventcampus.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Usuario {

    /** Meta de horas complementares exigida pelo curso de Engenharia de Software. */
    public static final int META_HORAS_PADRAO = 300;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Column(nullable = false)
    private String matricula;

    private String curso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Perfil perfil;

    @Builder.Default
    @Column(name = "horas_total")
    private Integer horasTotal = META_HORAS_PADRAO;

    @Builder.Default
    @Column(name = "criado_em")
    private LocalDateTime criadoEm = LocalDateTime.now();

    public enum Perfil { ALUNO, PROFESSOR, ADMIN }

    public int getMetaHoras() {
        return horasTotal != null && horasTotal > 0 ? horasTotal : META_HORAS_PADRAO;
    }
}
