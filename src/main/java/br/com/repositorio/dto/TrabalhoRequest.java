package br.com.repositorio.dto;

import br.com.repositorio.model.enums.TipoTrabalho;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record TrabalhoRequest(
        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 300, message = "O título deve ter no máximo 300 caracteres.")
        String titulo,

        String resumo,

        @Size(max = 500, message = "As palavras-chave devem ter no máximo 500 caracteres.")
        String palavrasChave,

        @NotNull(message = "O tipo de trabalho é obrigatório.")
        TipoTrabalho tipo,

        @NotNull(message = "O ano é obrigatório.")
        Integer ano,

        @NotNull(message = "O ID do curso é obrigatório.")
        Long cursoId,

        @NotNull(message = "O ID do orientador é obrigatório.")
        Long orientadorId,

        @NotEmpty(message = "Informe ao menos um autor.")
        Set<String> autores
) {
}
