package br.com.repositorio.service;

import br.com.repositorio.dto.CursoRequest;
import br.com.repositorio.dto.CursoResponse;
import br.com.repositorio.exception.RecursoNaoEncontradoException;
import br.com.repositorio.exception.RegraNegocioException;
import br.com.repositorio.model.entity.Curso;
import br.com.repositorio.repository.CursoRepository;
import br.com.repositorio.repository.TrabalhoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CursoService {

    private final CursoRepository cursoRepository;
    private final TrabalhoRepository trabalhoRepository;

    public CursoService(CursoRepository cursoRepository, TrabalhoRepository trabalhoRepository) {
        this.cursoRepository = cursoRepository;
        this.trabalhoRepository = trabalhoRepository;
    }

    @Transactional(readOnly = true)
    public List<CursoResponse> listar() {
        return cursoRepository.findAllByOrderByNomeAsc()
                .stream()
                .map(CursoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CursoResponse buscarPorId(Long id) {
        return CursoResponse.from(obter(id));
    }

    @Transactional
    public CursoResponse criar(CursoRequest dados) {
        String nome = dados.nome().trim();
        if (cursoRepository.existsByNomeIgnoreCase(nome)) {
            throw new RegraNegocioException("Já existe um curso cadastrado com este nome.");
        }

        Curso curso = new Curso();
        curso.setNome(nome);
        curso.setSigla(limparOpcional(dados.sigla()));
        return CursoResponse.from(cursoRepository.save(curso));
    }

    @Transactional
    public CursoResponse atualizar(Long id, CursoRequest dados) {
        Curso curso = obter(id);
        String nome = dados.nome().trim();
        if (cursoRepository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new RegraNegocioException("Já existe outro curso cadastrado com este nome.");
        }

        curso.setNome(nome);
        curso.setSigla(limparOpcional(dados.sigla()));
        return CursoResponse.from(cursoRepository.save(curso));
    }

    @Transactional
    public void excluir(Long id) {
        Curso curso = obter(id);
        if (trabalhoRepository.existsByCursoId(id)) {
            throw new RegraNegocioException("Não é possível excluir o curso pois existem trabalhos vinculados.");
        }
        cursoRepository.delete(curso);
    }

    private Curso obter(Long id) {
        return cursoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Curso", id));
    }

    private String limparOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
