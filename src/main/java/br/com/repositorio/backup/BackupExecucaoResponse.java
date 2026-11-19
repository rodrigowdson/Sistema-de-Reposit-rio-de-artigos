package br.com.repositorio.backup;

import java.time.LocalDateTime;

public record BackupExecucaoResponse(
        String id,
        String tipo,
        String origem,
        String status,
        LocalDateTime iniciadoEm,
        LocalDateTime finalizadoEm,
        long duracaoMs,
        String destino,
        String mensagem,
        String saida
) {
}
