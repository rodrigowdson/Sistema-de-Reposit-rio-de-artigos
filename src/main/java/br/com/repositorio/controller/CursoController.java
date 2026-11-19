package br.com.repositorio.controller;

import br.com.repositorio.dto.CursoRequest;
import br.com.repositorio.dto.CursoResponse;
import br.com.repositorio.service.CursoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cursos")
public class CursoController {

    private final CursoService service;

    public CursoController(CursoService service) {
        this.service = service;
    }

    @GetMapping
    public List<CursoResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public CursoResponse buscar(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CursoResponse criar(@RequestBody @Valid CursoRequest requisicao) {
        return service.criar(requisicao);
    }

    @PutMapping("/{id}")
    public CursoResponse atualizar(@PathVariable Long id, @RequestBody @Valid CursoRequest requisicao) {
        return service.atualizar(id, requisicao);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
