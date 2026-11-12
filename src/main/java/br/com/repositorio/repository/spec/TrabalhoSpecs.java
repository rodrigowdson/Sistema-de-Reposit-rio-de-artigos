package br.com.repositorio.repository.spec;

import br.com.repositorio.dto.TrabalhoFiltro;
import br.com.repositorio.model.entity.Trabalho;
import br.com.repositorio.model.enums.StatusTrabalho;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class TrabalhoSpecs {

    private TrabalhoSpecs() {
    }

    public static Specification<Trabalho> comFiltro(TrabalhoFiltro filtro, boolean apenasPublicados) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filtro == null) {
                if (apenasPublicados) {
                    predicates.add(cb.equal(root.get("status"), StatusTrabalho.PUBLICADO));
                }
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            if (apenasPublicados) {
                predicates.add(cb.equal(root.get("status"), StatusTrabalho.PUBLICADO));
            }

            if (filtro.cursoId() != null) {
                predicates.add(cb.equal(root.get("curso").get("id"), filtro.cursoId()));
            }

            if (filtro.orientadorId() != null) {
                predicates.add(cb.equal(root.get("orientador").get("id"), filtro.orientadorId()));
            }

            if (filtro.ano() != null) {
                predicates.add(cb.equal(root.get("ano"), filtro.ano()));
            }

            if (filtro.tipo() != null) {
                predicates.add(cb.equal(root.get("tipo"), filtro.tipo()));
            }

            if (filtro.termo() != null && !filtro.termo().isBlank()) {
                String termo = "%" + filtro.termo().trim().toLowerCase() + "%";
                Predicate titulo = cb.like(cb.lower(root.get("titulo")), termo);
                Predicate resumo = cb.like(cb.lower(root.get("resumo")), termo);
                Predicate palavras = cb.like(cb.lower(root.get("palavrasChave")), termo);
                Predicate autor = cb.isNotNull(root.join("autores"));
                predicates.add(cb.or(titulo, resumo, palavras, autor));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
