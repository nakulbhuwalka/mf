package com.mf.api.controller;

import com.mf.api.dto.SchemePageResponseDto;
import com.mf.api.service.SchemeQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schemes")
public class SchemeController {

    private final SchemeQueryService schemeQueryService;

    public SchemeController(final SchemeQueryService schemeQueryService) {
        this.schemeQueryService = schemeQueryService;
    }

    @GetMapping
    public ResponseEntity<SchemePageResponseDto> getSchemes(
            @RequestParam(name = "page", defaultValue = "0") final int page,
            @RequestParam(name = "size", defaultValue = "50") final int size,
            @RequestParam(name = "search", required = false) final String search,
            @RequestParam(name = "fundHouse", required = false) final String fundHouse,
            @RequestParam(name = "schemeType", required = false) final String schemeType,
            @RequestParam(name = "schemeCategory", required = false) final String schemeCategory) {

        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be less than zero");
        }
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("Page size must be between 1 and 200");
        }

        final SchemePageResponseDto response = schemeQueryService.getSchemes(
                page, size, search, fundHouse, schemeType, schemeCategory
        );
        return ResponseEntity.ok(response);
    }
}
