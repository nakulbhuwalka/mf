package com.mf.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "schemes")
public class Scheme {

    @Id
    @Column(name = "scheme_code", nullable = false)
    private Integer schemeCode;

    @Column(name = "scheme_name", nullable = false, length = 500)
    private String schemeName;

    @Column(name = "fund_house", length = 255)
    private String fundHouse;

    @Column(name = "scheme_type", length = 100)
    private String schemeType;

    @Column(name = "scheme_category", length = 255)
    private String schemeCategory;

    @Column(name = "isin_growth", length = 20)
    private String isinGrowth;

    @Column(name = "isin_div_reinvestment", length = 20)
    private String isinDivReinvestment;

    @Column(name = "nav_synced", nullable = false)
    private boolean navSynced;

    public Scheme() {
        // Default constructor for JPA
    }

    public Scheme(final Integer schemeCode,
                  final String schemeName,
                  final String fundHouse,
                  final String schemeType,
                  final String schemeCategory,
                  final String isinGrowth,
                  final String isinDivReinvestment,
                  final boolean navSynced) {
        this.schemeCode = schemeCode;
        this.schemeName = schemeName;
        this.fundHouse = fundHouse;
        this.schemeType = schemeType;
        this.schemeCategory = schemeCategory;
        this.isinGrowth = isinGrowth;
        this.isinDivReinvestment = isinDivReinvestment;
        this.navSynced = navSynced;
    }

    public Integer getSchemeCode() {
        return schemeCode;
    }

    public void setSchemeCode(final Integer schemeCode) {
        this.schemeCode = schemeCode;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public void setSchemeName(final String schemeName) {
        this.schemeName = schemeName;
    }

    public String getFundHouse() {
        return fundHouse;
    }

    public void setFundHouse(final String fundHouse) {
        this.fundHouse = fundHouse;
    }

    public String getSchemeType() {
        return schemeType;
    }

    public void setSchemeType(final String schemeType) {
        this.schemeType = schemeType;
    }

    public String getSchemeCategory() {
        return schemeCategory;
    }

    public void setSchemeCategory(final String schemeCategory) {
        this.schemeCategory = schemeCategory;
    }

    public String getIsinGrowth() {
        return isinGrowth;
    }

    public void setIsinGrowth(final String isinGrowth) {
        this.isinGrowth = isinGrowth;
    }

    public String getIsinDivReinvestment() {
        return isinDivReinvestment;
    }

    public void setIsinDivReinvestment(final String isinDivReinvestment) {
        this.isinDivReinvestment = isinDivReinvestment;
    }

    public boolean isNavSynced() {
        return navSynced;
    }

    public void setNavSynced(final boolean navSynced) {
        this.navSynced = navSynced;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Scheme other = (Scheme) obj;
        return Objects.equals(schemeCode, other.schemeCode);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(schemeCode);
    }
}
