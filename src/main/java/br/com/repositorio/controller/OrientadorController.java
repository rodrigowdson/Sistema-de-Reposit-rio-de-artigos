package br.com.repositorio.controller;

import br.com.repositorio.model.entity.Orientador;
import br.com.repositorio.repository.OrientadorRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orientadores")
public class OrientadorController {

    private final OrientadorRepository orientadorRepository;

    public OrientadorController(OrientadorRepository orientadorRepository) {
        this.orientadorRepository = orientadorRepository;
    }

    @GetMapping
    public ResponseEntity<List<Orientador>> listar(@RequestParam(name = "apenasAtivos", defaultValue = "false") boolean apenasAtivos) {
        List<Orientador> todos = orientadorRepository.findAll();
        if (apenasAtivos) {
            return ResponseEntity.ok(todos.stream().filter(Orientador::isAtivo).toList());
        }
        return ResponseEntity.ok(todos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Orientador> buscarPorId(@PathVariable Long id) {
        return orientadorRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
