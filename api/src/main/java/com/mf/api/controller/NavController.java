package com.mf.api.controller;

import com.mf.api.dto.NavHistoryResponseDto;
import com.mf.api.service.NavService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schemes")
public class NavController {

    private final NavService navService;

    public NavController(final NavService navService) {
        this.navService = navService;
    }

    @GetMapping("/{scheme_code}/nav")
    public ResponseEntity<NavHistoryResponseDto> getSchemeNav(
            @PathVariable("scheme_code") final int schemeCode,
            @RequestParam(name = "startDate", required = false) final String startDate,
            @RequestParam(name = "endDate", required = false) final String endDate,
            @RequestParam(name = "sort", defaultValue = "desc") final String sort,
            @RequestParam(name = "forceRefresh", defaultValue = "false") final boolean forceRefresh) {

        final NavHistoryResponseDto response = navService.getSchemeNav(
                schemeCode, startDate, endDate, sort, forceRefresh
        );
        return ResponseEntity.ok(response);
    }
}
