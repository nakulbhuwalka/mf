package com.mf.api.repository;

import com.mf.api.model.NavRecord;
import com.mf.api.model.NavRecordId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NavRecordRepository extends JpaRepository<NavRecord, NavRecordId> {

    @Query("SELECT n FROM NavRecord n WHERE n.schemeCode = :schemeCode "
            + "AND (:startDate IS NULL OR n.navDate >= :startDate) "
            + "AND (:endDate IS NULL OR n.navDate <= :endDate) "
            + "ORDER BY n.navDate DESC")
    List<NavRecord> findNavRecordsDesc(
            @Param("schemeCode") Integer schemeCode,
            @Param("startDate") Integer startDate,
            @Param("endDate") Integer endDate
    );

    @Query("SELECT n FROM NavRecord n WHERE n.schemeCode = :schemeCode "
            + "AND (:startDate IS NULL OR n.navDate >= :startDate) "
            + "AND (:endDate IS NULL OR n.navDate <= :endDate) "
            + "ORDER BY n.navDate ASC")
    List<NavRecord> findNavRecordsAsc(
            @Param("schemeCode") Integer schemeCode,
            @Param("startDate") Integer startDate,
            @Param("endDate") Integer endDate
    );
}
