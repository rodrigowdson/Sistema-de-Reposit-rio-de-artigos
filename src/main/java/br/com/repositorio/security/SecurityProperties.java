package br.com.repositorio.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private AdminInicial adminInicial = new AdminInicial();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public AdminInicial getAdminInicial() {
        return adminInicial;
    }

    public void setAdminInicial(AdminInicial adminInicial) {
        this.adminInicial = adminInicial;
    }

    public static class Jwt {
        private String secret = "troque-esta-chave-jwt-em-producao-por-uma-string-longa-e-aleatoria";
        private long expiracaoHoras = 24;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpiracaoHoras() {
            return expiracaoHoras;
        }

        public void setExpiracaoHoras(long expiracaoHoras) {
            this.expiracaoHoras = expiracaoHoras;
        }
    }

    public static class AdminInicial {
        private String nome = "Administrador";
        private String email = "admin@repositorio.local";
        private String senha = "";

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getSenha() {
            return senha;
        }

        public void setSenha(String senha) {
            this.senha = senha;
        }
    }
}
