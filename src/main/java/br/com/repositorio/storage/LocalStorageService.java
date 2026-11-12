package br.com.repositorio.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class LocalStorageService implements StorageService {

    private final Path baseDir;

    public LocalStorageService(@Value("${app.storage.local-dir:storage}") String localDir) {
        this.baseDir = Paths.get(localDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar o diretório do storage local", e);
        }
    }

    @Override
    public void salvar(String chave, MultipartFile arquivo) {
        try {
            Path destino = baseDir.resolve(chave).normalize();
            Path pai = destino.getParent();
            if (pai != null) {
                Files.createDirectories(pai);
            }
            Files.copy(arquivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao salvar o arquivo no storage local", e);
        }
    }

    @Override
    public Resource carregar(String chave) {
        Path caminho = baseDir.resolve(chave).normalize();
        if (!Files.exists(caminho)) {
            throw new IllegalArgumentException("Arquivo não encontrado no storage: " + chave);
        }
        return new FileSystemResource(caminho);
    }

    @Override
    public void remover(String chave) {
        try {
            Path caminho = baseDir.resolve(chave).normalize();
            Files.deleteIfExists(caminho);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao remover arquivo do storage local: " + chave, e);
        }
    }
}
