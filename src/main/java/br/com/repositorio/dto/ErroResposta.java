package br.com.repositorio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResposta(
        int status,
        String erro,
        String mensagem,
        String caminho,
        LocalDateTime timestamp,
        List<CampoErro> campos
) {
    public record CampoErro(String campo, String mensagem) {}

    public static ErroResposta de(int status, String erro, String mensagem, String caminho) {
        return new ErroResposta(status, erro, mensagem, caminho, LocalDateTime.now(), null);
    }

    public static ErroResposta deCampos(int status, String erro, String mensagem, String caminho, List<CampoErro> campos) {
        return new ErroResposta(status, erro, mensagem, caminho, LocalDateTime.now(), campos);
    }
}
