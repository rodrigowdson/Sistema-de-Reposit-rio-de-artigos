package br.com.repositorio.service;

import br.com.repositorio.dto.UsuarioResponse;
import br.com.repositorio.exception.RecursoNaoEncontradoException;
import br.com.repositorio.exception.RegraNegocioException;
import br.com.repositorio.model.entity.Usuario;
import br.com.repositorio.model.enums.Perfil;
import br.com.repositorio.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAllByOrderByNomeAsc()
                .stream()
                .map(UsuarioResponse::from)
                .toList();
    }

    @Transactional
    public UsuarioResponse alterarPerfil(Long id, Perfil novoPerfil, Usuario adminAtual) {
        Usuario usuario = obter(id);
        if (adminAtual != null && Objects.equals(usuario.getId(), adminAtual.getId()) && novoPerfil != Perfil.ADMIN) {
            throw new RegraNegocioException("Você não pode remover o próprio perfil de administrador.");
        }
        usuario.setPerfil(novoPerfil);
        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse alterarAtivo(Long id, boolean ativo, Usuario adminAtual) {
        Usuario usuario = obter(id);
        if (adminAtual != null && Objects.equals(usuario.getId(), adminAtual.getId()) && !ativo) {
            throw new RegraNegocioException("Você não pode desativar a própria conta.");
        }
        usuario.setAtivo(ativo);
        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    private Usuario obter(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", id));
    }
}
