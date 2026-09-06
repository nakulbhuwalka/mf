package com.mf.api.model;

import java.io.Serializable;
import java.util.Objects;

public class NavRecordId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer schemeCode;
    private Integer navDate;

    public NavRecordId() {
        // Default constructor for JPA
    }

    public NavRecordId(final Integer schemeCode, final Integer navDate) {
        this.schemeCode = schemeCode;
        this.navDate = navDate;
    }

    public Integer getSchemeCode() {
        return schemeCode;
    }

    public void setSchemeCode(final Integer schemeCode) {
        this.schemeCode = schemeCode;
    }

    public Integer getNavDate() {
        return navDate;
    }

    public void setNavDate(final Integer navDate) {
        this.navDate = navDate;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final NavRecordId that = (NavRecordId) obj;
        return Objects.equals(schemeCode, that.schemeCode)
                && Objects.equals(navDate, that.navDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemeCode, navDate);
    }
}
