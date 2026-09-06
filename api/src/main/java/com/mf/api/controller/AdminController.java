package com.mf.api.controller;

import com.mf.api.dto.SyncSummaryDto;
import com.mf.api.service.SchemeSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/schemes")
public class AdminController {

    private final SchemeSyncService schemeSyncService;

    public AdminController(final SchemeSyncService schemeSyncService) {
        this.schemeSyncService = schemeSyncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncSummaryDto> syncSchemes() {
        final SyncSummaryDto summary = schemeSyncService.syncSchemes();
        return ResponseEntity.ok(summary);
    }
}
