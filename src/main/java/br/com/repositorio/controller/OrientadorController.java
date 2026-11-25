package br.com.repositorio.controller;

import br.com.repositorio.dto.AtivoRequest;
import br.com.repositorio.dto.OrientadorRequest;
import br.com.repositorio.dto.OrientadorResponse;
import br.com.repositorio.security.UsuarioDetails;
import br.com.repositorio.service.OrientadorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orientadores")
public class OrientadorController {

    private final OrientadorService service;

    public OrientadorController(OrientadorService service) {
        this.service = service;
    }

    @GetMapping
    public List<OrientadorResponse> listar(
            @RequestParam(defaultValue = "false") boolean apenasAtivos,
            Authentication authentication
    ) {
        return service.listar(apenasAtivos, ehModerador(authentication));
    }

    @GetMapping("/{id}")
    public OrientadorResponse buscar(@PathVariable Long id, Authentication authentication) {
        return service.buscarPorId(id, ehModerador(authentication));
    }

    private boolean ehModerador(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (authentication.getPrincipal() instanceof UsuarioDetails details
                && details.getUsuario() != null
                && details.getUsuario().getPerfil() != null) {
            return details.getUsuario().getPerfil().podeModerar();
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_ADMIN".equals(role) || "ROLE_BIBLIOTECARIO".equals(role)) {
                return true;
            }
        }
        return false;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrientadorResponse criar(@RequestBody @Valid OrientadorRequest requisicao) {
        return service.criar(requisicao);
    }

    @PutMapping("/{id}")
    public OrientadorResponse atualizar(@PathVariable Long id, @RequestBody @Valid OrientadorRequest requisicao) {
        return service.atualizar(id, requisicao);
    }

    @PatchMapping("/{id}/ativo")
    public OrientadorResponse atualizarAtivo(@PathVariable Long id, @RequestBody @Valid AtivoRequest requisicao) {
        return service.alterarAtivo(id, requisicao.ativo());
    }
}
