package br.com.repositorio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CursoRequest(
        @NotBlank(message = "Informe o nome do curso.")
        @Size(max = 150, message = "O nome do curso deve ter no máximo 150 caracteres.")
        String nome,

        @Size(max = 20, message = "A sigla deve ter no máximo 20 caracteres.")
        String sigla
) {
}
