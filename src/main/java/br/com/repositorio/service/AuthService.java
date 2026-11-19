package br.com.repositorio.service;

import br.com.repositorio.dto.AuthResponse;
import br.com.repositorio.dto.LoginRequest;
import br.com.repositorio.dto.RegistroRequest;
import br.com.repositorio.dto.UsuarioResponse;
import br.com.repositorio.exception.RegraNegocioException;
import br.com.repositorio.model.entity.Usuario;
import br.com.repositorio.model.enums.Perfil;
import br.com.repositorio.repository.UsuarioRepository;
import br.com.repositorio.security.JwtService;
import br.com.repositorio.security.UsuarioDetails;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse registrar(RegistroRequest dados) {
        String email = dados.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RegraNegocioException("Já existe uma conta cadastrada com este e-mail.");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(dados.nome().trim());
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(dados.senha()));
        usuario.setPerfil(Perfil.ALUNO);
        usuario.setAtivo(true);

        Usuario salvo = usuarioRepository.save(usuario);
        return montarResposta(salvo);
    }

    public AuthResponse login(LoginRequest dados) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        dados.email().trim().toLowerCase(),
                        dados.senha()
                )
        );

        UsuarioDetails details = (UsuarioDetails) auth.getPrincipal();
        return montarResposta(details.getUsuario());
    }

    private AuthResponse montarResposta(Usuario usuario) {
        String token = jwtService.gerarToken(usuario);
        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpiracaoSegundos(),
                UsuarioResponse.from(usuario)
        );
    }
}
