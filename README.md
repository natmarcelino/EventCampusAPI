# EventCampus API: Spring Boot 3.2 + PostgreSQL

## Como rodar

1. Crie o banco no PostgreSQL (se ainda não existir):
   ```sql
   CREATE DATABASE eventcampus;
   ```
2. Coloque a sua senha do PostgreSQL em `src/main/resources/application.properties`:
   ```
   spring.datasource.password=SUA_SENHA_AQUI
   ```
3. Rode:
   ```bash
   mvn spring-boot:run
   ```
   A API sobe em **http://localhost:8080**, e o celular acessa pelo IP do computador.

Na primeira execução, o `DataSeeder`:
- ajusta um banco antigo: libera o status `CONCLUIDO` e muda a meta de horas de 100h para **300h**;
- cria usuários de teste com a senha **123456**, se eles ainda não existirem:

| Perfil      | E-mail                 |
|-------------|------------------------|
| Coordenação | admin@uni.edu.br       |
| Professor   | professor@uni.edu.br   |
| Aluna       | ana@uni.edu.br         |
| Aluno (já tem 300h) | joao@uni.edu.br |
| Alunos      | maria@uni.edu.br, lucas@uni.edu.br |

- cria eventos, inscrições e certificados de exemplo, **só se a tabela de eventos estiver vazia**.

Para desligar: `eventcampus.seed=false`.

---

## Regras de negócio

- **Horas complementares** = carga horária dos eventos com presença confirmada + certificados externos aprovados. A meta padrão é 300h.
- **Presença por QR Code + GPS:** o professor ativa a presença no painel web, que envia a localização do navegador. O aluno escaneia o QR no app, que envia o GPS do celular. A API calcula a distância pela fórmula de Haversine e só aceita o aluno dentro do raio (padrão 100 m). O QR pode ter tempo limite e pode ser regenerado. Quando é regenerado, fotos ou prints do código antigo param de funcionar.
- **Status do evento:**
  - `RASCUNHO`: invisível para os alunos.
  - `ATIVO`: aceita inscrição e presença.
  - `CONCLUIDO`: já foi realizado.
  - `ENCERRADO`: cancelado.
- Professor só gerencia os próprios eventos. A coordenação (`ADMIN`) gerencia todos.
- O cadastro público (`/api/auth/cadastro`) sempre cria **ALUNO**. Professores e coordenadores são criados pela coordenação.
- Todo erro volta no formato `{"erro": "mensagem"}`.

---

## Endpoints

### 🔐 Autenticação
| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/api/auth/login` | Público | `{email, senha}` → `{token, id, nome, perfil, ...}` |
| POST | `/api/auth/cadastro` | Público | Cadastra aluno |
| GET | `/api/auth/me` | Logado | Dados do usuário + horas (aluno) |

### 📅 Eventos
| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| GET | `/api/eventos?status=` | Logado | Eventos visíveis (sem rascunhos) com `inscritos` e `vagasRestantes` |
| GET | `/api/eventos/{id}` | Logado | Detalhe |
| GET | `/api/eventos/meus` | Professor/Admin | Eventos do professor (admin: todos) |
| POST | `/api/eventos` | Professor/Admin | Criar |
| PUT | `/api/eventos/{id}` | Dono/Admin | Editar |
| PATCH | `/api/eventos/{id}/status` | Dono/Admin | `{status}` |
| DELETE | `/api/eventos/{id}` | Dono/Admin | Excluir (só sem inscritos) |
| GET | `/api/eventos/{id}/qr` | Dono/Admin | Código e estado do QR |
| PATCH | `/api/eventos/{id}/ativar-qr` | Dono/Admin | `{latitude, longitude, raioMetros, minutos}` |
| PATCH | `/api/eventos/{id}/encerrar-qr` | Dono/Admin | Fecha a presença |
| PATCH | `/api/eventos/{id}/regenerar-qr` | Dono/Admin | Gera um código novo |

### ✅ Presença e inscrições
| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/api/presenca/confirmar` | Aluno | `{qrCodigo, latitude, longitude}` |
| POST | `/api/inscricoes/{eventoId}` | Aluno | Inscrever-se |
| DELETE | `/api/inscricoes/{eventoId}` | Aluno | Cancelar inscrição |
| GET | `/api/inscricoes/minhas` | Aluno | Minhas inscrições |
| GET | `/api/inscricoes/evento/{eventoId}` | Dono/Admin | Lista de inscritos |
| PATCH | `/api/inscricoes/{id}/presenca` | Dono/Admin | `{confirmar: true/false}` (manual) |

### 📄 Certificados
| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/api/certificados` | Aluno | `{tituloCurso, instituicao, cargaHoraria, arquivoNome?, arquivoTipo?, arquivoBase64?}` |
| GET | `/api/certificados/meus` | Aluno | Meus certificados |
| GET | `/api/certificados/{id}/arquivo` | Dono/Admin | Anexo em Base64 |
| GET | `/api/certificados?status=` | Admin | Externos (PENDENTE/APROVADO/REPROVADO) |
| PATCH | `/api/certificados/{id}/aprovar` | Admin | `{cargaHoraria?}` |
| PATCH | `/api/certificados/{id}/reprovar` | Admin | `{motivo}` |
| GET | `/api/certificados/horas-alunos` | Admin | Progresso de horas de todos |
| POST | `/api/certificados/final/{alunoId}` | Admin | Emite o certificado final |
| GET | `/api/certificados/final/{alunoId}` | Admin/Aluno | Dados do certificado final |
| GET | `/api/certificados/validar/{codigo}` | Público | Confere o código de validação |

### 📊 Coordenação e relatórios
| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| GET | `/api/admin/dashboard` | Admin | Números do sistema |
| GET | `/api/admin/eventos` | Admin | Todos os eventos |
| GET/POST | `/api/admin/usuarios` | Admin | Listar e criar professor, admin ou aluno |
| GET | `/api/relatorios/evento/{id}` | Dono/Admin | Relatório de presença (RF-ADM-003) |
