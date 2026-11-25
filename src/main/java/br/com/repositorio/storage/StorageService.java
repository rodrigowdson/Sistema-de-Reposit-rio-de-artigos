package br.com.repositorio.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    void salvar(String chave, MultipartFile arquivo);

    Resource carregar(String chave);

    void remover(String chave);

    default void mover(String chaveOrigem, String chaveDestino) {
        throw new UnsupportedOperationException("Movimentação de arquivo não suportada neste storage.");
    }

    default boolean testarConexao() {
        return true;
    }
}
