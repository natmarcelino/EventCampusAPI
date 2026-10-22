package com.eventcampus.controller;

import com.eventcampus.entity.Usuario;
import com.eventcampus.exception.ApiException;
import com.eventcampus.repository.UsuarioRepository;
import com.eventcampus.security.JwtUtil;
import com.eventcampus.service.Body;
import com.eventcampus.service.HorasService;
import com.eventcampus.service.Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioRepository usuarioRepo;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final HorasService horasService;

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        String email = Body.str(body, "email");
        String senha = Body.str(body, "senha");
        if (email == null || senha == null) {
            throw ApiException.badRequest("Informe e-mail e senha");
        }
        Usuario u = usuarioRepo.findByEmailIgnoreCase(email)
            .filter(x -> passwordEncoder.matches(senha, x.getSenha()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos"));

        Map<String, Object> m = new LinkedHashMap<>(Mapper.usuario(u));
        m.put("token", jwtUtil.gerarToken(u.getEmail(), u.getPerfil().name()));
        return ResponseEntity.ok(m);
    }

    // POST /api/auth/cadastro — auto-cadastro público: SEMPRE cria ALUNO
    // (professores e administradores são criados pela coordenação em /api/admin/usuarios)
    @PostMapping("/cadastro")
    public ResponseEntity<?> cadastro(@RequestBody Map<String, Object> body) {
        String nome  = Body.strObrig(body, "nome", "o nome");
        String email = Body.strObrig(body, "email", "o e-mail").toLowerCase();
        String senha = Body.strObrig(body, "senha", "a senha");
        if (senha.length() < 6) throw ApiException.badRequest("A senha deve ter pelo menos 6 caracteres");
        if (usuarioRepo.existsByEmailIgnoreCase(email)) throw ApiException.badRequest("E-mail já cadastrado");

        Usuario u = Usuario.builder()
            .nome(nome)
            .email(email)
            .senha(passwordEncoder.encode(senha))
            .matricula(Body.str(body, "matricula") != null ? Body.str(body, "matricula") : "")
            .curso(Body.str(body, "curso"))
            .perfil(Usuario.Perfil.ALUNO)
            .build();
        usuarioRepo.save(u);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("mensagem", "Usuário cadastrado com sucesso", "id", u.getId()));
    }

    // GET /api/auth/me — dados do usuário logado (+ horas, se for aluno)
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal Usuario u) {
        if (u == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Não autenticado");
        Map<String, Object> m = new LinkedHashMap<>(Mapper.usuario(u));
        if (u.getPerfil() == Usuario.Perfil.ALUNO) {
            m.putAll(horasService.resumo(u));
        }
        return ResponseEntity.ok(m);
    }
}
