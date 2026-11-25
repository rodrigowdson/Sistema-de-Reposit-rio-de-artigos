package br.com.repositorio.security;

import br.com.repositorio.model.entity.Usuario;
import br.com.repositorio.model.enums.Perfil;
import br.com.repositorio.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties properties;

    public AdminSeedRunner(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            SecurityProperties properties
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByPerfil(Perfil.ADMIN)) {
            return;
        }

        SecurityProperties.AdminInicial adminCfg = properties.getAdminInicial();
        if (!StringUtils.hasText(adminCfg.getEmail()) || !StringUtils.hasText(adminCfg.getSenha())) {
            log.warn("Nenhum usuário ADMIN encontrado e APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD não configurados.");
            return;
        }

        if (usuarioRepository.existsByEmailIgnoreCase(adminCfg.getEmail().trim())) {
            return;
        }

        Usuario admin = new Usuario();
        admin.setNome(StringUtils.hasText(adminCfg.getNome()) ? adminCfg.getNome().trim() : "Administrador");
        admin.setEmail(adminCfg.getEmail().trim().toLowerCase());
        admin.setSenhaHash(passwordEncoder.encode(adminCfg.getSenha()));
        admin.setPerfil(Perfil.ADMIN);
        admin.setAtivo(true);

        usuarioRepository.save(admin);
        log.info("Usuário ADMIN inicial criado: {}", admin.getEmail());
        if ("admin123".equals(adminCfg.getSenha()) || "troque-esta-senha-admin".equals(adminCfg.getSenha())) {
            log.warn("ATENÇÃO: O usuário ADMIN inicial foi criado com senha padrão. Defina APP_ADMIN_PASSWORD no ambiente da EC2.");
        }
    }
}
