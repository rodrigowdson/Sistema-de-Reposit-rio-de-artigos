package br.com.repositorio.controller;

import br.com.repositorio.backup.BackupConfigRequest;
import br.com.repositorio.backup.BackupExecucaoResponse;
import br.com.repositorio.backup.BackupService;
import br.com.repositorio.backup.BackupStatusResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/backups")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("/status")
    public BackupStatusResponse status() {
        return backupService.obterStatus();
    }

    @GetMapping("/historico")
    public List<BackupExecucaoResponse> historico() {
        return backupService.listarHistorico();
    }

    @PostMapping("/executar")
    public BackupExecucaoResponse executarAgora() {
        return backupService.executarBackupManual();
    }

    @PostMapping("/restaurar")
    public BackupExecucaoResponse restaurarDaEc2() {
        return backupService.restaurarBackup();
    }

    @PutMapping("/config")
    public BackupStatusResponse atualizarConfig(@RequestBody @Valid BackupConfigRequest requisicao) {
        return backupService.atualizarConfiguracao(requisicao);
    }
}
