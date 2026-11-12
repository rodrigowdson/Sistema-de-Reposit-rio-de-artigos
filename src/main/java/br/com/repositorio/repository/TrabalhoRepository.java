package br.com.repositorio.repository;

import br.com.repositorio.model.entity.Trabalho;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrabalhoRepository extends JpaRepository<Trabalho, Long>, JpaSpecificationExecutor<Trabalho> {

    /** Busca paginada com filtros dinâmicos; já traz curso e orientador (evita N+1). */
    @Override
    @EntityGraph(attributePaths = {"curso", "orientador"})
    Page<Trabalho> findAll(Specification<Trabalho> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"curso", "orientador"})
    Optional<Trabalho> findById(Long id);

    boolean existsByArquivoHashSha256(String hash);

    boolean existsByCursoId(Long cursoId);

    boolean existsByOrientadorId(Long orientadorId);

    /** Incremento atômico: seguro com downloads simultâneos. */
    @Modifying
    @Query("update Trabalho t set t.totalDownloads = t.totalDownloads + 1 where t.id = :id")
    void incrementarDownloads(@Param("id") Long id);

    /** Anos disponíveis, para montar o filtro na tela. */
    @Query("select distinct t.ano from Trabalho t where t.status = br.com.repositorio.model.enums.StatusTrabalho.PUBLICADO order by t.ano desc")
    List<Integer> listarAnosPublicados();
}
