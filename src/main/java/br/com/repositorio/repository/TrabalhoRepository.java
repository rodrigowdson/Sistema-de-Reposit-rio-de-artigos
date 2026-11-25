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

    @Override
    @EntityGraph(attributePaths = {"curso", "orientador", "enviadoPor"})
    Page<Trabalho> findAll(Specification<Trabalho> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"curso", "orientador", "enviadoPor"})
    Optional<Trabalho> findById(Long id);

    @EntityGraph(attributePaths = {"curso", "orientador", "enviadoPor"})
    Page<Trabalho> findByEnviadoPorId(Long usuarioId, Pageable pageable);

    boolean existsByArquivoHashSha256(String hash);

    boolean existsByCursoId(Long cursoId);

    boolean existsByOrientadorId(Long orientadorId);

    @Modifying
    @Query("update Trabalho t set t.totalDownloads = t.totalDownloads + 1 where t.id = :id")
    void incrementarDownloads(@Param("id") Long id);

    @Query("select distinct t.ano from Trabalho t where t.status = br.com.repositorio.model.enums.StatusTrabalho.PUBLICADO order by t.ano desc")
    List<Integer> listarAnosPublicados();

    @Query("""
            select distinct t from Trabalho t
            left join fetch t.curso
            left join fetch t.orientador
            left join fetch t.enviadoPor
            left join fetch t.autores
            order by t.id asc
            """)
    List<Trabalho> findAllComRelacionamentos();
}
