package br.com.repositorio.dto;

import br.com.repositorio.model.enums.Perfil;
import jakarta.validation.constraints.NotNull;

public record AlterarPerfilRequest(
        @NotNull(message = "Informe o perfil.")
        Perfil perfil
) {
}
