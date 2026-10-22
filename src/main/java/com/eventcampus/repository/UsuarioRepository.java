package com.eventcampus.repository;

import com.eventcampus.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<Usuario> findByPerfil(Usuario.Perfil perfil);
    List<Usuario> findByPerfilOrderByNomeAsc(Usuario.Perfil perfil);
    long countByPerfil(Usuario.Perfil perfil);
}
