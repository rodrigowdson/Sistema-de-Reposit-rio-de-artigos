package br.com.repositorio.backup;

import java.time.LocalDateTime;

public record BackupStatusResponse(
        boolean habilitado,
        long intervaloMinutos,
        boolean emExecucao,
        String ec2User,
        String ec2Host,
        String ec2Dir,
        boolean chaveConfigurada,
        String diretorioLocal,
        LocalDateTime proximoAgendamento,
        BackupExecucaoResponse ultimaExecucao,
        int totalExecucoesNoHistorico
) {
}
