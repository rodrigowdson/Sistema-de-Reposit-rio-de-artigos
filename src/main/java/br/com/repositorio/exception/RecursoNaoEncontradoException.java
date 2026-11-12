package br.com.repositorio.exception;

public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String nome, Long id) {
        super(nome + " com id " + id + " não foi encontrado.");
    }
}
