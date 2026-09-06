package com.mf.api.repository;

import com.mf.api.model.Scheme;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class SchemeSpecifications {

    private SchemeSpecifications() {
        // Private constructor for utility class
    }

    public static Specification<Scheme> withFilters(
            final String search,
            final String fundHouse,
            final String schemeType,
            final String schemeCategory) {

        return (root, query, criteriaBuilder) -> {
            final List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                final String searchPattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("schemeName")),
                        searchPattern
                ));
            }

            if (fundHouse != null && !fundHouse.isBlank()) {
                final String fundHousePattern = "%" + fundHouse.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("fundHouse")),
                        fundHousePattern
                ));
            }

            if (schemeType != null && !schemeType.isBlank()) {
                final String typeVal = schemeType.trim().toLowerCase(Locale.ROOT);
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("schemeType")),
                        typeVal
                ));
            }

            if (schemeCategory != null && !schemeCategory.isBlank()) {
                final String categoryPattern = "%" + schemeCategory.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("schemeCategory")),
                        categoryPattern
                ));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
