package br.com.repositorio.dto;

import br.com.repositorio.model.entity.Orientador;

public record OrientadorResponse(
        Long id,
        String nome,
        String email,
        String titulacao,
        boolean ativo
) {
    public static OrientadorResponse from(Orientador o) {
        return new OrientadorResponse(o.getId(), o.getNome(), o.getEmail(), o.getTitulacao(), o.isAtivo());
    }

    public static OrientadorResponse fromPublico(Orientador o) {
        return new OrientadorResponse(o.getId(), o.getNome(), null, o.getTitulacao(), o.isAtivo());
    }
}
