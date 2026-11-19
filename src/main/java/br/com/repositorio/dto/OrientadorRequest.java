package br.com.repositorio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrientadorRequest(
        @NotBlank(message = "Informe o nome do orientador.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String nome,

        @NotBlank(message = "Informe o e-mail do orientador.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres.")
        String email,

        @Size(max = 50, message = "A titulação deve ter no máximo 50 caracteres.")
        String titulacao
) {
}
