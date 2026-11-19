package br.com.repositorio.service;

import br.com.repositorio.dto.OrientadorRequest;
import br.com.repositorio.dto.OrientadorResponse;
import br.com.repositorio.exception.RecursoNaoEncontradoException;
import br.com.repositorio.exception.RegraNegocioException;
import br.com.repositorio.model.entity.Orientador;
import br.com.repositorio.repository.OrientadorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrientadorService {

    private final OrientadorRepository orientadorRepository;

    public OrientadorService(OrientadorRepository orientadorRepository) {
        this.orientadorRepository = orientadorRepository;
    }

    @Transactional(readOnly = true)
    public List<OrientadorResponse> listar(boolean apenasAtivos) {
        List<Orientador> lista = apenasAtivos
                ? orientadorRepository.findByAtivoTrueOrderByNomeAsc()
                : orientadorRepository.findAllByOrderByNomeAsc();
        return lista.stream().map(OrientadorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrientadorResponse buscarPorId(Long id) {
        return OrientadorResponse.from(obter(id));
    }

    @Transactional
    public OrientadorResponse criar(OrientadorRequest dados) {
        String email = dados.email().trim().toLowerCase();
        if (orientadorRepository.existsByEmailIgnoreCase(email)) {
            throw new RegraNegocioException("Já existe um orientador cadastrado com este e-mail.");
        }

        Orientador orientador = new Orientador();
        orientador.setNome(dados.nome().trim());
        orientador.setEmail(email);
        orientador.setTitulacao(limparOpcional(dados.titulacao()));
        orientador.setAtivo(true);
        return OrientadorResponse.from(orientadorRepository.save(orientador));
    }

    @Transactional
    public OrientadorResponse atualizar(Long id, OrientadorRequest dados) {
        Orientador orientador = obter(id);
        String email = dados.email().trim().toLowerCase();
        if (orientadorRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new RegraNegocioException("Já existe outro orientador cadastrado com este e-mail.");
        }

        orientador.setNome(dados.nome().trim());
        orientador.setEmail(email);
        orientador.setTitulacao(limparOpcional(dados.titulacao()));
        return OrientadorResponse.from(orientadorRepository.save(orientador));
    }

    @Transactional
    public OrientadorResponse alterarAtivo(Long id, boolean ativo) {
        Orientador orientador = obter(id);
        orientador.setAtivo(ativo);
        return OrientadorResponse.from(orientadorRepository.save(orientador));
    }

    private Orientador obter(Long id) {
        return orientadorRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orientador", id));
    }

    private String limparOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
