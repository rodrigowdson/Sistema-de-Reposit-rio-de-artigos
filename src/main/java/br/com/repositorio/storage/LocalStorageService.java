package br.com.repositorio.storage;

import br.com.repositorio.exception.ArmazenamentoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(name = "app.storage.tipo", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path baseDir;

    public LocalStorageService(StorageProperties properties) {
        this.baseDir = Paths.get(properties.getLocalDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new ArmazenamentoException("Não foi possível criar o diretório do storage local: " + this.baseDir, e);
        }
        if (!Files.isWritable(this.baseDir)) {
            throw new ArmazenamentoException("Sem permissão de escrita no diretório do storage local: " + this.baseDir);
        }
        log.info("Storage local estruturado inicializado em: {}", this.baseDir);
    }

    @Override
    public void salvar(String chave, MultipartFile arquivo) {
        try (InputStream in = arquivo.getInputStream()) {
            Path destino = resolverSeguro(chave);
            Path pai = destino.getParent();
            if (pai != null) {
                Files.createDirectories(pai);
            }
            Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ArmazenamentoException("Falha ao salvar o arquivo no storage local.", e);
        }
    }

    @Override
    public Resource carregar(String chave) {
        Path caminho = resolverSeguro(chave);
        if (!Files.exists(caminho)) {
            throw new ArmazenamentoException("Arquivo não encontrado no storage local: " + chave);
        }
        return new FileSystemResource(caminho);
    }

    @Override
    public void remover(String chave) {
        try {
            Path caminho = resolverSeguro(chave);
            Files.deleteIfExists(caminho);
            limparPastasVazias(caminho.getParent());
        } catch (IOException e) {
            throw new ArmazenamentoException("Falha ao remover arquivo do storage local: " + chave, e);
        }
    }

    @Override
    public void mover(String chaveOrigem, String chaveDestino) {
        Path origem = resolverSeguro(chaveOrigem);
        Path destino = resolverSeguro(chaveDestino);
        if (origem.equals(destino)) {
            return;
        }
        if (!Files.exists(origem)) {
            throw new ArmazenamentoException("Arquivo de origem não encontrado no storage local: " + chaveOrigem);
        }
        try {
            Path pai = destino.getParent();
            if (pai != null) {
                Files.createDirectories(pai);
            }
            Files.move(origem, destino, StandardCopyOption.REPLACE_EXISTING);
            limparPastasVazias(origem.getParent());
        } catch (IOException e) {
            throw new ArmazenamentoException("Falha ao mover arquivo no storage local.", e);
        }
    }

    private void limparPastasVazias(Path diretorio) {
        Path atual = diretorio;
        while (atual != null && !atual.equals(baseDir) && atual.startsWith(baseDir)) {
            try (var stream = Files.list(atual)) {
                if (stream.findAny().isPresent()) {
                    break;
                }
            } catch (IOException e) {
                break;
            }
            try {
                Files.deleteIfExists(atual);
            } catch (IOException e) {
                break;
            }
            atual = atual.getParent();
        }
    }

    @Override
    public boolean testarConexao() {
        return Files.isDirectory(baseDir) && Files.isWritable(baseDir);
    }

    public Path getBaseDir() {
        return baseDir;
    }

    private Path resolverSeguro(String chave) {
        Path destino = baseDir.resolve(chave).normalize();
        if (!destino.startsWith(baseDir)) {
            throw new ArmazenamentoException("Chave de armazenamento inválida: " + chave);
        }
        return destino;
    }
}
