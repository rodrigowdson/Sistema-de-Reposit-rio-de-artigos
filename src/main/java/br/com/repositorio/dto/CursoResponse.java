package br.com.repositorio.dto;

import br.com.repositorio.model.entity.Curso;

public record CursoResponse(
        Long id,
        String nome,
        String sigla
) {
    public static CursoResponse from(Curso c) {
        return new CursoResponse(c.getId(), c.getNome(), c.getSigla());
    }
}
