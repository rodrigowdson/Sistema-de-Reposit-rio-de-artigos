package br.com.repositorio.dto;

import br.com.repositorio.model.enums.TipoTrabalho;

import java.util.Set;

public record TrabalhoRequest(
        String titulo,
        String resumo,
        String palavrasChave,
        TipoTrabalho tipo,
        Integer ano,
        Long cursoId,
        Long orientadorId,
        Set<String> autores
) {
}
