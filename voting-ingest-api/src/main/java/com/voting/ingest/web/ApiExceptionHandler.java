package com.voting.ingest.web;

import com.voting.domain.DomainException;
import com.voting.domain.election.ElectionClosedException;
import com.voting.ingest.kafka.EventPublicationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traducao de falhas para HTTP.
 *
 * <p>A distincao que importa para o eleitor: 400 significa "seu voto tem um problema, corrija";
 * 403 significa "a votacao nao esta aberta, e nao ha o que corrigir"; 503 significa "o voto
 * esta bom, o sistema e que nao conseguiu registra-lo - tente de novo". Reenviar apos um 503 e
 * seguro, porque a duplicata seria filtrada na apuracao.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Corpo de erro simples; nao expoe detalhes internos ao cliente. */
    public record ApiError(String error, String message) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        String detalhes = e.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ApiError("VOTO_INVALIDO", detalhes));
    }

    /**
     * Recusa por prazo. A borda antecipa a resposta por cortesia; quem decide de verdade e o
     * Flink, unico ponto que ve todos os votos com um relogio so.
     */
    @ExceptionHandler(ElectionClosedException.class)
    public ResponseEntity<ApiError> electionClosed(ElectionClosedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(e.code(), e.getMessage()));
    }

    /**
     * Recusas que o dominio sabe nomear. O codigo vem da propria excecao, entao acrescentar
     * uma regra nova nao exige tocar neste handler - e o cliente decide pelo codigo, nao pelo
     * texto da mensagem, que pode mudar.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> domainRule(DomainException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.code(), e.getMessage()));
    }

    /** Invariantes dos value objects, que ainda falam a lingua do {@code java.lang}. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> invalidVote(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ApiError("VOTO_INVALIDO", e.getMessage()));
    }

    @ExceptionHandler(EventPublicationException.class)
    public ResponseEntity<ApiError> publicationFailed(EventPublicationException e) {
        LOG.error("voto nao registrado", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("REGISTRO_INDISPONIVEL", "voto nao registrado; tente novamente"));
    }
}
