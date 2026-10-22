package com.eventcampus.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Faz toda resposta de erro da API seguir o formato {"erro": "mensagem"}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> negado(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("erro", "Você não tem permissão para esta ação"));
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeParseException.class,
                       ClassCastException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalido(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("erro", "Dados inválidos: verifique os campos enviados"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> generico(Exception e) {
        // Erros "normais" do Spring MVC (rota inexistente, método errado, parâmetro inválido...)
        if (e instanceof ErrorResponse er) {
            int status = er.getStatusCode().value();
            String msg = status == 404 ? "Rota não encontrada"
                       : status == 405 ? "Método HTTP não permitido nesta rota"
                       : "Requisição inválida";
            return ResponseEntity.status(status).body(Map.of("erro", msg));
        }
        if (e instanceof MethodArgumentTypeMismatchException) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Parâmetro inválido na URL"));
        }
        log.error("Erro inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("erro", "Erro interno no servidor"));
    }
}
