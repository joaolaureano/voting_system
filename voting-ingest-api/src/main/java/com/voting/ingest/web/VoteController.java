package com.voting.ingest.web;

import com.voting.application.usecase.CastVoteUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recebe votos. Traduz HTTP para o caso de uso e de volta - nada alem disso.
 *
 * <p>O caminho aqui e relativo: o prefixo de versao vem de {@link ApiVersionConfiguration},
 * entao a rota efetiva e {@code /api/v1/votes}.
 */
@RestController
@RequestMapping("/votes")
public class VoteController {

    private final CastVoteUseCase castVote;

    public VoteController(CastVoteUseCase castVote) {
        this.castVote = castVote;
    }

    @PostMapping
    public ResponseEntity<CastVoteResponse> cast(@Valid @RequestBody CastVoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CastVoteResponse.of(castVote.execute(request.toCommand())));
    }
}
