package br.com.repositorio.dto;

import br.com.repositorio.model.entity.Trabalho;
import br.com.repositorio.model.enums.StatusTrabalho;
import br.com.repositorio.model.enums.TipoTrabalho;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

public record TrabalhoResponse(
        Long id,
        String titulo,
        String resumo,
        String palavrasChave,
        TipoTrabalho tipo,
        Integer ano,
        StatusTrabalho status,
        String cursoNome,
        String orientadorNome,
        Set<String> autores,
        String arquivoNomeOriginal,
        String arquivoHashSha256,
        Long totalDownloads,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
    public static TrabalhoResponse from(Trabalho t) {
        return new TrabalhoResponse(
                t.getId(),
                t.getTitulo(),
                t.getResumo(),
                t.getPalavrasChave(),
                t.getTipo(),
                t.getAno(),
                t.getStatus(),
                t.getCurso() != null ? t.getCurso().getNome() : null,
                t.getOrientador() != null ? t.getOrientador().getNome() : null,
                t.getAutores() != null ? new LinkedHashSet<>(t.getAutores()) : Set.of(),
                t.getArquivo() != null ? t.getArquivo().getNomeOriginal() : null,
                t.getArquivo() != null ? t.getArquivo().getHashSha256() : null,
                t.getTotalDownloads(),
                t.getCriadoEm(),
                t.getAtualizadoEm()
        );
    }
}
