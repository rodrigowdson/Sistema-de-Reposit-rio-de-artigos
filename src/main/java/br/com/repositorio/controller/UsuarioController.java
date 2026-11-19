package br.com.repositorio.controller;

import br.com.repositorio.dto.AlterarPerfilRequest;
import br.com.repositorio.dto.AtivoRequest;
import br.com.repositorio.dto.UsuarioResponse;
import br.com.repositorio.security.UsuarioDetails;
import br.com.repositorio.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<UsuarioResponse> listarTodos() {
        return service.listar();
    }

    @PatchMapping("/{id}/perfil")
    public UsuarioResponse atualizarPerfil(
            @PathVariable Long id,
            @RequestBody @Valid AlterarPerfilRequest requisicao,
            @AuthenticationPrincipal UsuarioDetails autenticado
    ) {
        return service.alterarPerfil(id, requisicao.perfil(), autenticado.getUsuario());
    }

    @PatchMapping("/{id}/ativo")
    public UsuarioResponse atualizarSituacao(
            @PathVariable Long id,
            @RequestBody @Valid AtivoRequest requisicao,
            @AuthenticationPrincipal UsuarioDetails autenticado
    ) {
        return service.alterarAtivo(id, requisicao.ativo(), autenticado.getUsuario());
    }
}
