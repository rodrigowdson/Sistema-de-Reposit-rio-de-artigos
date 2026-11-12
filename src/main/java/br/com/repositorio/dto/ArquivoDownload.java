package br.com.repositorio.dto;

import org.springframework.core.io.Resource;

public record ArquivoDownload(
        Resource resource,
        String nomeOriginal,
        String contentType,
        Long tamanhoBytes
) {
}
