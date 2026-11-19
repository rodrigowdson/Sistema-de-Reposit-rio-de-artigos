package br.com.repositorio.backup;

import br.com.repositorio.exception.RegraNegocioException;
import br.com.repositorio.storage.StorageProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final int LIMITE_HISTORICO = 50;
    private static final int LIMITE_SAIDA_CHARS = 2000;
    private static final Pattern PADRAO_IDENTIFICADOR = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern PADRAO_DIRETORIO_REMOTO = Pattern.compile("^/[a-zA-Z0-9._/-]+$");

    private final Path diretorioLocal;
    private final long timeoutSegundos;
    private final ThreadPoolTaskScheduler scheduler;
    private final AtomicBoolean emExecucao = new AtomicBoolean(false);
    private final Deque<BackupExecucaoResponse> historico = new ConcurrentLinkedDeque<>();

    private final Object configLock = new Object();
    private boolean habilitado;
    private long intervaloMinutos;
    private String ec2User;
    private String ec2Host;
    private String ec2Key;
    private String ec2Dir;
    private ScheduledFuture<?> tarefaAgendada;
    private LocalDateTime proximoAgendamento;

    public BackupService(BackupProperties backupProperties, StorageProperties storageProperties) {
        this.diretorioLocal = Paths.get(storageProperties.getLocalDir()).toAbsolutePath().normalize();
        this.timeoutSegundos = Math.max(30L, backupProperties.getTimeoutSegundos());

        this.habilitado = backupProperties.isHabilitado();
        this.intervaloMinutos = Math.max(1L, backupProperties.getIntervaloMinutos());
        this.ec2User = limpar(backupProperties.getEc2User(), "admin");
        this.ec2Host = limpar(backupProperties.getEc2Host(), "");
        this.ec2Key = limpar(backupProperties.getEc2Key(), "");
        this.ec2Dir = limpar(backupProperties.getEc2Dir(), "/home/admin/backup-repositorio");

        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.setPoolSize(1);
        this.scheduler.setThreadNamePrefix("backup-ec2-");
        this.scheduler.setDaemon(true);
    }

    @PostConstruct
    void iniciar() {
        this.scheduler.initialize();
        synchronized (configLock) {
            reagendarInterno();
        }
    }

    @PreDestroy
    void encerrar() {
        synchronized (configLock) {
            if (tarefaAgendada != null) {
                tarefaAgendada.cancel(false);
            }
        }
        scheduler.shutdown();
    }

    public BackupStatusResponse obterStatus() {
        synchronized (configLock) {
            return new BackupStatusResponse(
                    habilitado,
                    intervaloMinutos,
                    emExecucao.get(),
                    ec2User,
                    ec2Host,
                    ec2Dir,
                    chaveExiste(ec2Key),
                    diretorioLocal.toString(),
                    proximoAgendamento,
                    historico.peekFirst(),
                    historico.size()
            );
        }
    }

    public List<BackupExecucaoResponse> listarHistorico() {
        return List.copyOf(historico);
    }

    public BackupStatusResponse atualizarConfiguracao(BackupConfigRequest req) {
        synchronized (configLock) {
            String novoUser = req.ec2User() != null ? req.ec2User().trim() : ec2User;
            String novoHost = req.ec2Host() != null ? req.ec2Host().trim() : ec2Host;
            String novaKey = req.ec2Key() != null ? req.ec2Key().trim() : ec2Key;
            String novoDir = req.ec2Dir() != null ? removerBarraFinal(req.ec2Dir().trim()) : ec2Dir;
            long novoIntervalo = req.intervaloMinutos() != null ? req.intervaloMinutos() : intervaloMinutos;
            boolean novoHabilitado = req.habilitado() != null ? req.habilitado() : habilitado;

            if (StringUtils.hasText(novoUser) && !PADRAO_IDENTIFICADOR.matcher(novoUser).matches()) {
                throw new RegraNegocioException("Usuário SSH inválido.");
            }
            if (StringUtils.hasText(novoHost) && !PADRAO_IDENTIFICADOR.matcher(novoHost).matches()) {
                throw new RegraNegocioException("Host/IP da EC2 inválido.");
            }
            if (StringUtils.hasText(novoDir)
                    && (!PADRAO_DIRETORIO_REMOTO.matcher(novoDir).matches() || novoDir.contains(".."))) {
                throw new RegraNegocioException("Diretório remoto da EC2 inválido. Use caminho absoluto como /home/admin/backup-repositorio.");
            }
            if (StringUtils.hasText(novaKey) && (novaKey.contains("\"") || novaKey.contains("'") || novaKey.contains("\n"))) {
                throw new RegraNegocioException("Caminho da chave SSH inválido.");
            }

            if (novoHabilitado) {
                validarConfiguracaoPronta(novoUser, novoHost, novaKey, novoDir);
            }

            this.ec2User = novoUser;
            this.ec2Host = novoHost;
            this.ec2Key = novaKey;
            this.ec2Dir = novoDir;
            this.intervaloMinutos = novoIntervalo;
            this.habilitado = novoHabilitado;

            reagendarInterno();
            return obterStatus();
        }
    }

    public BackupExecucaoResponse executarBackupManual() {
        return executarOperacao("BACKUP", "MANUAL");
    }

    public BackupExecucaoResponse restaurarBackup() {
        return executarOperacao("RESTAURACAO", "MANUAL");
    }

    private void executarBackupAgendado() {
        try {
            executarOperacao("BACKUP", "AGENDADO");
        } catch (RuntimeException e) {
            log.warn("Backup agendado não pôde ser executado: {}", e.getMessage());
        } finally {
            synchronized (configLock) {
                if (habilitado) {
                    proximoAgendamento = LocalDateTime.now().plusMinutes(intervaloMinutos);
                }
            }
        }
    }

    private BackupExecucaoResponse executarOperacao(String tipo, String origem) {
        if (!emExecucao.compareAndSet(false, true)) {
            if ("AGENDADO".equals(origem)) {
                log.info("Backup agendado ignorado pois já existe uma execução em andamento.");
                return null;
            }
            throw new RegraNegocioException("Já existe um backup ou restauração em andamento.");
        }

        String user;
        String host;
        String key;
        String dir;
        synchronized (configLock) {
            user = this.ec2User;
            host = this.ec2Host;
            key = this.ec2Key;
            dir = removerBarraFinal(this.ec2Dir);
        }

        LocalDateTime iniciadoEm = LocalDateTime.now();
        long inicioNs = System.nanoTime();
        String destino = user + "@" + host + ":" + dir + "/arquivos";

        try {
            Path chavePath = validarConfiguracaoPronta(user, host, key, dir);
            Files.createDirectories(diretorioLocal);

            String remoto = user + "@" + host;
            String pastaRemotaArquivos = dir + "/arquivos";
            String chaveStr = chavePath.toString();

            ResultadoComando mkdirRes = rodarProcesso(List.of(
                    "ssh",
                    "-i", chaveStr,
                    "-o", "StrictHostKeyChecking=accept-new",
                    "-o", "BatchMode=yes",
                    "-o", "ConnectTimeout=15",
                    remoto,
                    "mkdir -p " + pastaRemotaArquivos
            ));

            if (mkdirRes.exitCode != 0) {
                return registrar(tipo, origem, "ERRO", iniciadoEm, inicioNs, destino,
                        "Falha ao preparar pasta na EC2 (código " + mkdirRes.exitCode + ").", mkdirRes.saida);
            }

            List<String> comandoSync = montarComandoSync(tipo, chaveStr, remoto, pastaRemotaArquivos);
            ResultadoComando syncRes = rodarProcesso(comandoSync);

            if (syncRes.exitCode != 0) {
                return registrar(tipo, origem, "ERRO", iniciadoEm, inicioNs, destino,
                        "Falha na sincronização com a EC2 (código " + syncRes.exitCode + ").", syncRes.saida);
            }

            String msgOk = "BACKUP".equals(tipo)
                    ? "Backup enviado para a EC2 com sucesso."
                    : "Backup baixado da EC2 para o storage local com sucesso.";

            log.info("{} ({}) concluído em {} ms -> {}", tipo, origem, duracaoMs(inicioNs), destino);
            return registrar(tipo, origem, "SUCESSO", iniciadoEm, inicioNs, destino, msgOk, syncRes.saida);
        } catch (RegraNegocioException e) {
            registrar(tipo, origem, "ERRO", iniciadoEm, inicioNs, destino, e.getMessage(), "");
            throw e;
        } catch (Exception e) {
            log.error("Erro durante {} ({}) com a EC2", tipo, origem, e);
            return registrar(tipo, origem, "ERRO", iniciadoEm, inicioNs, destino,
                    "Erro ao executar processo de backup: " + e.getMessage(), "");
        } finally {
            emExecucao.set(false);
        }
    }

    private List<String> montarComandoSync(String tipo, String chaveStr, String remoto, String pastaRemotaArquivos) {
        boolean temRsync = Files.isExecutable(Path.of("/usr/bin/rsync"))
                || Files.isExecutable(Path.of("/bin/rsync"));

        String localComBarra = diretorioLocal.toString() + "/";
        String remotoComBarra = remoto + ":" + pastaRemotaArquivos + "/";

        if (temRsync) {
            String chaveArg = chaveStr.contains(" ") ? "\"" + chaveStr + "\"" : chaveStr;
            String sshCmd = "ssh -i " + chaveArg
                    + " -o StrictHostKeyChecking=accept-new -o BatchMode=yes -o ConnectTimeout=15";

            List<String> cmd = new ArrayList<>();
            cmd.add("rsync");
            cmd.add("-avz");
            cmd.add("-e");
            cmd.add(sshCmd);
            if ("BACKUP".equals(tipo)) {
                cmd.add(localComBarra);
                cmd.add(remotoComBarra);
            } else {
                cmd.add(remotoComBarra);
                cmd.add(localComBarra);
            }
            return cmd;
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("scp");
        cmd.add("-r");
        cmd.add("-i");
        cmd.add(chaveStr);
        cmd.add("-o");
        cmd.add("StrictHostKeyChecking=accept-new");
        cmd.add("-o");
        cmd.add("BatchMode=yes");
        cmd.add("-o");
        cmd.add("ConnectTimeout=15");
        if ("BACKUP".equals(tipo)) {
            cmd.add(diretorioLocal.toString() + "/.");
            cmd.add(remotoComBarra);
        } else {
            cmd.add(remoto + ":" + pastaRemotaArquivos + "/.");
            cmd.add(localComBarra);
        }
        return cmd;
    }

    private ResultadoComando rodarProcesso(List<String> comando) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.redirectErrorStream(true);
        Process processo = pb.start();

        CompletableFuture<String> leituraSaida = CompletableFuture.supplyAsync(() -> lerSaidaLimitada(processo));
        boolean terminou = processo.waitFor(timeoutSegundos, TimeUnit.SECONDS);

        if (!terminou) {
            processo.destroyForcibly();
            return new ResultadoComando(-1, "Tempo limite excedido (" + timeoutSegundos + "s).");
        }

        String saida = leituraSaida.join();
        return new ResultadoComando(processo.exitValue(), saida);
    }

    private String lerSaidaLimitada(Process processo) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                if (sb.length() < LIMITE_SAIDA_CHARS) {
                    sb.append(linha).append('\n');
                }
            }
        } catch (IOException ignored) {
        }
        return sb.toString().trim();
    }

    private void reagendarInterno() {
        if (tarefaAgendada != null) {
            tarefaAgendada.cancel(false);
            tarefaAgendada = null;
            proximoAgendamento = null;
        }

        if (!habilitado) {
            log.info("Backup automático na EC2 está desabilitado.");
            return;
        }

        Duration intervalo = Duration.ofMinutes(intervaloMinutos);
        Instant primeiraExecucao = Instant.now().plus(intervalo);
        tarefaAgendada = scheduler.scheduleWithFixedDelay(this::executarBackupAgendado, primeiraExecucao, intervalo);
        proximoAgendamento = LocalDateTime.ofInstant(primeiraExecucao, ZoneId.systemDefault());
        log.info("Backup automático na EC2 agendado a cada {} minuto(s). Próxima execução: {}",
                intervaloMinutos, proximoAgendamento);
    }

    private Path validarConfiguracaoPronta(String user, String host, String key, String dir) {
        if (!StringUtils.hasText(user) || !PADRAO_IDENTIFICADOR.matcher(user).matches()) {
            throw new RegraNegocioException("Configure o usuário SSH da EC2 (BACKUP_EC2_USER).");
        }
        if (!StringUtils.hasText(host) || !PADRAO_IDENTIFICADOR.matcher(host).matches()) {
            throw new RegraNegocioException("Configure o IP/host da EC2 (BACKUP_EC2_HOST).");
        }
        if (!StringUtils.hasText(dir) || !PADRAO_DIRETORIO_REMOTO.matcher(dir).matches() || dir.contains("..")) {
            throw new RegraNegocioException("Configure um diretório remoto válido na EC2 (BACKUP_EC2_DIR).");
        }
        if (!StringUtils.hasText(key)) {
            throw new RegraNegocioException("Configure o caminho da chave .pem da EC2 (BACKUP_EC2_KEY).");
        }
        Path chavePath = resolverCaminhoChave(key);
        if (!Files.isRegularFile(chavePath)) {
            throw new RegraNegocioException("Chave SSH não encontrada em: " + chavePath);
        }
        return chavePath;
    }

    private boolean chaveExiste(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        try {
            return Files.isRegularFile(resolverCaminhoChave(key));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Path resolverCaminhoChave(String key) {
        String expandido = key.startsWith("~/")
                ? System.getProperty("user.home") + key.substring(1)
                : key;
        return Paths.get(expandido).toAbsolutePath().normalize();
    }

    private BackupExecucaoResponse registrar(
            String tipo,
            String origem,
            String status,
            LocalDateTime iniciadoEm,
            long inicioNs,
            String destino,
            String mensagem,
            String saida
    ) {
        BackupExecucaoResponse item = new BackupExecucaoResponse(
                UUID.randomUUID().toString(),
                tipo,
                origem,
                status,
                iniciadoEm,
                LocalDateTime.now(),
                duracaoMs(inicioNs),
                destino,
                mensagem,
                saida
        );
        historico.addFirst(item);
        while (historico.size() > LIMITE_HISTORICO) {
            historico.pollLast();
        }
        return item;
    }

    private long duracaoMs(long inicioNs) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNs);
    }

    private String limpar(String valor, String padrao) {
        return StringUtils.hasText(valor) ? valor.trim() : padrao;
    }

    private String removerBarraFinal(String dir) {
        if (dir == null) {
            return "";
        }
        return dir.length() > 1 && dir.endsWith("/") ? dir.substring(0, dir.length() - 1) : dir;
    }

    private record ResultadoComando(int exitCode, String saida) {
    }
}
