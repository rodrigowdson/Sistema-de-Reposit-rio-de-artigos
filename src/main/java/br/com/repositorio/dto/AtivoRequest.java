package br.com.repositorio.dto;

import jakarta.validation.constraints.NotNull;

public record AtivoRequest(
        @NotNull(message = "Informe o status ativo (true/false).")
        Boolean ativo
) {
}
