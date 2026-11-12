package br.com.repositorio.model.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
@AttributeOverrides({
    @AttributeOverride(name = "nomeOriginal", column = @Column(name = "arquivo_nome_original", nullable = false, length = 255)),
    @AttributeOverride(name = "chaveStorage", column = @Column(name = "arquivo_chave", nullable = false, length = 500)),
    @AttributeOverride(name = "contentType", column = @Column(name = "arquivo_content_type", nullable = false, length = 100)),
    @AttributeOverride(name = "tamanhoBytes", column = @Column(name = "arquivo_tamanho_bytes", nullable = false)),
    @AttributeOverride(name = "hashSha256", column = @Column(name = "arquivo_hash_sha256", nullable = false, length = 64))
})
public class ArquivoInfo {

    private String nomeOriginal;
    private String chaveStorage;
    private String contentType;
    private Long tamanhoBytes;
    private String hashSha256;

    public ArquivoInfo() {
    }

    public ArquivoInfo(String nomeOriginal, String chaveStorage, String contentType, Long tamanhoBytes, String hashSha256) {
        this.nomeOriginal = nomeOriginal;
        this.chaveStorage = chaveStorage;
        this.contentType = contentType;
        this.tamanhoBytes = tamanhoBytes;
        this.hashSha256 = hashSha256;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public void setNomeOriginal(String nomeOriginal) {
        this.nomeOriginal = nomeOriginal;
    }

    public String getChaveStorage() {
        return chaveStorage;
    }

    public void setChaveStorage(String chaveStorage) {
        this.chaveStorage = chaveStorage;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public void setTamanhoBytes(Long tamanhoBytes) {
        this.tamanhoBytes = tamanhoBytes;
    }

    public String getHashSha256() {
        return hashSha256;
    }

    public void setHashSha256(String hashSha256) {
        this.hashSha256 = hashSha256;
    }
}
