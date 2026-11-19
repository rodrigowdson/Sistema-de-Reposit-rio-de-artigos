package br.com.repositorio.repository;

import br.com.repositorio.model.entity.Orientador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrientadorRepository extends JpaRepository<Orientador, Long> {

    List<Orientador> findAllByOrderByNomeAsc();

    List<Orientador> findByAtivoTrueOrderByNomeAsc();

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
