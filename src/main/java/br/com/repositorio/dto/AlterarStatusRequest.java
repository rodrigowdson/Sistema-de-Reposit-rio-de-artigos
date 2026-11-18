package br.com.repositorio.dto;

import br.com.repositorio.model.enums.StatusTrabalho;
import jakarta.validation.constraints.NotNull;

public record AlterarStatusRequest(
        @NotNull(message = "O novo status é obrigatório.")
        StatusTrabalho status
) {
}
