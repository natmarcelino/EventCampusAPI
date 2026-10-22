package com.eventcampus.config;

import com.eventcampus.entity.*;
import com.eventcampus.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Roda ao iniciar a API:
 *  1. Ajusta o banco antigo (constraint de status e meta de 100h → 300h).
 *  2. Cria usuários de teste (senha 123456) se ainda não existirem.
 *  3. Cria eventos/inscrições/certificados de exemplo se o banco não tiver eventos.
 * Desligue com eventcampus.seed=false no application.properties.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String SENHA = "123456";

    private final UsuarioRepository usuarioRepo;
    private final EventoRepository eventoRepo;
    private final InscricaoRepository inscricaoRepo;
    private final CertificadoRepository certRepo;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    @Value("${eventcampus.seed:true}")
    private boolean seed;

    @Override
    public void run(String... args) {
        ajustarBancoAntigo();
        if (!seed) return;

        Usuario admin = usuario("Coordenação Acadêmica", "admin@uni.edu.br", "ADM-001", null, Usuario.Perfil.ADMIN);
        Usuario prof  = usuario("Prof. Carlos Mendes", "professor@uni.edu.br", "PROF-001", null, Usuario.Perfil.PROFESSOR);
        Usuario ana   = usuario("Ana Clara Silva", "ana@uni.edu.br", "2022/00345", "Engenharia de Software", Usuario.Perfil.ALUNO);
        Usuario joao  = usuario("João Pedro Santos", "joao@uni.edu.br", "2021/00128", "Engenharia de Software", Usuario.Perfil.ALUNO);
        Usuario maria = usuario("Maria Fernanda Lima", "maria@uni.edu.br", "2023/00567", "Sistemas de Informação", Usuario.Perfil.ALUNO);
        Usuario lucas = usuario("Lucas Almeida Costa", "lucas@uni.edu.br", "2022/00891", "Administração", Usuario.Perfil.ALUNO);

        if (eventoRepo.count() > 0) return;   // já tem dados reais: não mexe
        log.info(">> Criando eventos de exemplo...");
        LocalDate hoje = LocalDate.now();

        Evento ev1 = evento("Inovação e Tecnologia no Futuro", Evento.Tipo.PALESTRA, hoje.plusDays(2), "19:00", "21:00",
            "Auditório Central", 4, 120, Evento.Status.ATIVO, prof,
            "Tendências tecnológicas e como elas impactam o mercado e a sociedade.");
        Evento ev2 = evento("Design Thinking na Prática", Evento.Tipo.WORKSHOP, hoje.plusDays(5), "14:00", "18:00",
            "Sala 203", 6, 30, Evento.Status.ATIVO, prof,
            "Metodologias de design centrado no usuário e resolução criativa de problemas.");
        Evento ev3 = evento("Workshop de Inteligência Artificial", Evento.Tipo.WORKSHOP, hoje.plusDays(9), "09:00", "17:00",
            "Lab. Informática 2", 8, 40, Evento.Status.ATIVO, prof,
            "Introdução prática à IA: machine learning, redes neurais e aplicações reais.");
        Evento ev4 = evento("Excel Avançado", Evento.Tipo.CURSO, hoje.minusDays(20), "18:00", "22:00",
            "Lab. Informática 2", 10, 20, Evento.Status.CONCLUIDO, prof,
            "Fórmulas avançadas, tabelas dinâmicas e automação.");
        Evento ev5 = evento("Semana de Engenharia de Software", Evento.Tipo.SEMINARIO, hoje.minusDays(45), "08:00", "18:00",
            "Auditório Central", 20, 200, Evento.Status.CONCLUIDO, prof,
            "Palestras e minicursos sobre arquitetura, testes e carreira.");
        evento("Carreira e Mercado de TI", Evento.Tipo.PALESTRA, hoje.plusDays(15), "19:00", "21:00",
            "Auditório Central", 2, 80, Evento.Status.RASCUNHO, prof, "Bate-papo com profissionais da área.");

        // inscrições
        inscricao(ana, ev1, false);  inscricao(ana, ev2, false);
        inscricao(ana, ev4, true);   inscricao(ana, ev5, true);
        inscricao(joao, ev1, false); inscricao(joao, ev4, true); inscricao(joao, ev5, true);
        inscricao(maria, ev1, false); inscricao(maria, ev5, true);
        inscricao(lucas, ev2, false); inscricao(lucas, ev4, false);

        // certificados externos
        cert(ana, "Curso de Marketing Digital", "Udemy", 20, Certificado.Status.APROVADO, admin, null);
        cert(ana, "Congresso de Engenharia", "CREA-GO", 16, Certificado.Status.PENDENTE, null, null);
        cert(joao, "Bootcamp Java Spring", "DIO", 120, Certificado.Status.APROVADO, admin, null);
        cert(joao, "Maratona de Programação", "SBC", 80, Certificado.Status.APROVADO, admin, null);
        cert(joao, "Curso de Inglês Técnico", "Wizard", 80, Certificado.Status.APROVADO, admin, null);
        cert(maria, "Workshop de UX", "Google", 12, Certificado.Status.PENDENTE, null, null);
        cert(maria, "Excel Básico", "Fundação Bradesco", 4, Certificado.Status.REPROVADO, admin,
            "Certificado ilegível. Envie novamente em melhor qualidade.");
        cert(lucas, "Gestão de Projetos", "SEBRAE", 30, Certificado.Status.PENDENTE, null, null);

        log.info(">> Dados de exemplo criados. Logins (senha {}): admin@uni.edu.br | professor@uni.edu.br | ana@uni.edu.br", SENHA);
    }

    /** Bancos criados pela versão antiga: libera o novo status CONCLUIDO e sobe a meta para 300h. */
    private void ajustarBancoAntigo() {
        try {
            // remove qualquer CHECK antigo da coluna status (criado pelo Hibernate sem o CONCLUIDO)
            jdbc.execute("""
                DO $$ DECLARE r record; BEGIN
                  FOR r IN SELECT conname FROM pg_constraint
                           WHERE conrelid = 'eventos'::regclass AND contype = 'c'
                             AND pg_get_constraintdef(oid) ILIKE '%status%'
                  LOOP EXECUTE 'ALTER TABLE eventos DROP CONSTRAINT ' || quote_ident(r.conname); END LOOP;
                END $$""");
            jdbc.execute("ALTER TABLE eventos ADD CONSTRAINT eventos_status_check " +
                         "CHECK (status IN ('RASCUNHO','ATIVO','CONCLUIDO','ENCERRADO'))");
            int n = jdbc.update("UPDATE usuarios SET horas_total = ? WHERE horas_total IS NULL OR horas_total = 100",
                                Usuario.META_HORAS_PADRAO);
            if (n > 0) log.info(">> Meta de horas atualizada para {}h em {} usuário(s)", Usuario.META_HORAS_PADRAO, n);
            jdbc.update("UPDATE eventos SET qr_ativo = false WHERE qr_ativo IS NULL");
            jdbc.update("UPDATE inscricoes SET presenca_confirmada = false WHERE presenca_confirmada IS NULL");
            jdbc.update("UPDATE inscricoes SET inscrito_em = now() WHERE inscrito_em IS NULL");
            jdbc.update("UPDATE certificados SET eh_final = false WHERE eh_final IS NULL");
            jdbc.update("UPDATE certificados SET enviado_em = now() WHERE enviado_em IS NULL");
        } catch (Exception e) {
            log.warn("Não foi possível ajustar o banco automaticamente: {}", e.getMessage());
        }
    }

    private Usuario usuario(String nome, String email, String matricula, String curso, Usuario.Perfil perfil) {
        return usuarioRepo.findByEmailIgnoreCase(email).orElseGet(() -> usuarioRepo.save(Usuario.builder()
            .nome(nome).email(email).senha(encoder.encode(SENHA))
            .matricula(matricula).curso(curso).perfil(perfil).build()));
    }

    private Evento evento(String titulo, Evento.Tipo tipo, LocalDate data, String ini, String fim, String local,
                          int carga, int vagas, Evento.Status status, Usuario prof, String descricao) {
        return eventoRepo.save(Evento.builder()
            .titulo(titulo).tipo(tipo).data(data)
            .horarioInicio(LocalTime.parse(ini)).horarioFim(LocalTime.parse(fim))
            .local(local).cargaHoraria(carga).vagas(vagas).status(status)
            .professor(prof).descricao(descricao)
            .qrCodigo(UUID.randomUUID().toString()).qrAtivo(false)
            .raioMetros(Evento.RAIO_PADRAO_METROS)
            .build());
    }

    private void inscricao(Usuario aluno, Evento ev, boolean presente) {
        inscricaoRepo.save(Inscricao.builder()
            .aluno(aluno).evento(ev)
            .presencaConfirmada(presente)
            .confirmadaEm(presente ? ev.getData().atTime(ev.getHorarioInicio()).plusMinutes(10) : null)
            .formaConfirmacao(presente ? "QR_CODE" : null)
            .inscritoEm(LocalDateTime.now().minusDays(30))
            .build());
    }

    private void cert(Usuario aluno, String titulo, String inst, int carga, Certificado.Status status,
                      Usuario analisadoPor, String motivo) {
        certRepo.save(Certificado.builder()
            .aluno(aluno).tituloCurso(titulo).instituicao(inst).cargaHoraria(carga)
            .status(status).analisadoPor(analisadoPor)
            .analisadoEm(analisadoPor != null ? LocalDateTime.now().minusDays(3) : null)
            .motivoReprovacao(motivo)
            .enviadoEm(LocalDateTime.now().minusDays(10))
            .build());
    }
}
