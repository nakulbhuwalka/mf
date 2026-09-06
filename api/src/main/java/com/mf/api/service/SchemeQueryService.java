package com.mf.api.service;

import com.mf.api.dto.PageMetadataDto;
import com.mf.api.dto.SchemePageResponseDto;
import com.mf.api.dto.SchemeResponseDto;
import com.mf.api.model.Scheme;
import com.mf.api.repository.SchemeRepository;
import com.mf.api.repository.SchemeSpecifications;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchemeQueryService {

    private final SchemeRepository schemeRepository;

    public SchemeQueryService(final SchemeRepository schemeRepository) {
        this.schemeRepository = schemeRepository;
    }

    @Transactional(readOnly = true)
    public SchemePageResponseDto getSchemes(
            final int page,
            final int size,
            final String search,
            final String fundHouse,
            final String schemeType,
            final String schemeCategory) {

        final int boundedSize = Math.clamp(size, 1, 200);
        final int boundedPage = Math.max(page, 0);
        final Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by("schemeCode").ascending());

        final Specification<Scheme> spec = SchemeSpecifications.withFilters(
                search, fundHouse, schemeType, schemeCategory
        );

        final Page<Scheme> schemePage = schemeRepository.findAll(spec, pageable);

        final List<SchemeResponseDto> content = schemePage.getContent().stream()
                .map(SchemeResponseDto::fromEntity)
                .toList();

        final PageMetadataDto pageMetadata = new PageMetadataDto(
                schemePage.getNumber(),
                schemePage.getSize(),
                schemePage.getTotalElements(),
                schemePage.getTotalPages()
        );

        return new SchemePageResponseDto(content, pageMetadata);
    }
}
