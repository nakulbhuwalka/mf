package com.mf.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SyncSummaryDto(
        @JsonProperty("status")
        String status,

        @JsonProperty("totalFetched")
        int totalFetched,

        @JsonProperty("inserted")
        int inserted,

        @JsonProperty("updated")
        int updated,

        @JsonProperty("durationMs")
        long durationMs,

        @JsonProperty("syncedAt")
        String syncedAt
) {
}
