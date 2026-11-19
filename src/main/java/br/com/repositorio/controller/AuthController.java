package br.com.repositorio.controller;

import br.com.repositorio.dto.AuthResponse;
import br.com.repositorio.dto.LoginRequest;
import br.com.repositorio.dto.RegistroRequest;
import br.com.repositorio.dto.UsuarioResponse;
import br.com.repositorio.security.UsuarioDetails;
import br.com.repositorio.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registrar")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registrar(@RequestBody @Valid RegistroRequest dados) {
        return authService.registrar(dados);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody @Valid LoginRequest dados) {
        return authService.login(dados);
    }

    @GetMapping("/me")
    public UsuarioResponse perfilAtual(@AuthenticationPrincipal UsuarioDetails principal) {
        return UsuarioResponse.from(principal.getUsuario());
    }
}
