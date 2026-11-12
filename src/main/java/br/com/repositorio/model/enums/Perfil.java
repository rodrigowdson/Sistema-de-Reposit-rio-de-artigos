package br.com.repositorio.model.enums;

public enum Perfil {
    ADMIN,
    BIBLIOTECARIO,
    PROFESSOR,
    ALUNO;

    public boolean podeModerar() {
        return this == ADMIN || this == BIBLIOTECARIO;
    }
}
