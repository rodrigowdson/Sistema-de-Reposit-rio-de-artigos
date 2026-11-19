package br.com.repositorio.dto;

import br.com.repositorio.model.enums.StatusTrabalho;
import br.com.repositorio.model.enums.TipoTrabalho;

public record TrabalhoFiltro(
        Long cursoId,
        Long orientadorId,
        Integer ano,
        TipoTrabalho tipo,
        StatusTrabalho status,
        String termo
) {
}
