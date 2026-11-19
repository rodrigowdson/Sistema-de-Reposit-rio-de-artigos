package br.com.repositorio.backup;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record BackupConfigRequest(
        Boolean habilitado,

        @Min(value = 1, message = "O intervalo mínimo é de 1 minuto.")
        @Max(value = 10080, message = "O intervalo máximo é de 10080 minutos (7 dias).")
        Long intervaloMinutos,

        String ec2User,
        String ec2Host,
        String ec2Key,
        String ec2Dir
) {
}
