package br.com.repositorio.dto;

import br.com.repositorio.model.enums.TipoTrabalho;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record TrabalhoRequest(
        @NotBlank(message = "Informe o título do trabalho.")
        @Size(max = 300, message = "O título deve ter no máximo 300 caracteres.")
        String titulo,

        String resumo,

        @Size(max = 500, message = "As palavras-chave devem ter no máximo 500 caracteres.")
        String palavrasChave,

        @NotNull(message = "Informe o tipo do trabalho.")
        TipoTrabalho tipo,

        @NotNull(message = "Informe o ano do trabalho.")
        @Min(value = 1900, message = "Ano inválido.")
        @Max(value = 2100, message = "Ano inválido.")
        Integer ano,

        @NotNull(message = "Informe o curso.")
        Long cursoId,

        @NotNull(message = "Informe o orientador.")
        Long orientadorId,

        @NotEmpty(message = "Informe pelo menos um autor.")
        Set<@NotBlank(message = "Nome do autor não pode estar vazio.") @Size(max = 150) String> autores
) {
}
