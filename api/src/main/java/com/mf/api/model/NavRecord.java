package com.mf.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "nav_records")
@IdClass(NavRecordId.class)
public class NavRecord {

    @Id
    @Column(name = "scheme_code", nullable = false)
    private Integer schemeCode;

    @Id
    @Column(name = "nav_date", nullable = false)
    private Integer navDate;

    @Column(name = "nav_value", nullable = false)
    private Float navValue;

    public NavRecord() {
        // Default constructor for JPA
    }

    public NavRecord(final Integer schemeCode, final Integer navDate, final Float navValue) {
        this.schemeCode = schemeCode;
        this.navDate = navDate;
        this.navValue = navValue;
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

    public Float getNavValue() {
        return navValue;
    }

    public void setNavValue(final Float navValue) {
        this.navValue = navValue;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final NavRecord other = (NavRecord) obj;
        return Objects.equals(schemeCode, other.schemeCode)
                && Objects.equals(navDate, other.navDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemeCode, navDate);
    }
}
