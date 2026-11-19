package br.com.repositorio.controller;

import br.com.repositorio.dto.AtivoRequest;
import br.com.repositorio.dto.OrientadorRequest;
import br.com.repositorio.dto.OrientadorResponse;
import br.com.repositorio.service.OrientadorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    public List<OrientadorResponse> listar(@RequestParam(defaultValue = "false") boolean apenasAtivos) {
        return service.listar(apenasAtivos);
    }

    @GetMapping("/{id}")
    public OrientadorResponse buscar(@PathVariable Long id) {
        return service.buscarPorId(id);
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
