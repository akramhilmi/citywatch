package com.gitgud.citywatch.data.cache.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.gitgud.citywatch.data.cache.entity.CachedReport;

import java.util.List;

/**
 * Data Access Object for cached reports
 */
@Dao
public interface ReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CachedReport> reports);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CachedReport report);

    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    List<CachedReport> getAllReports();

    @Query("SELECT * FROM reports WHERE documentId = :documentId")
    CachedReport getReportById(String documentId);

    @Query("DELETE FROM reports")
    void deleteAll();

    @Query("DELETE FROM reports WHERE documentId = :documentId")
    void deleteById(String documentId);

    @Query("SELECT COUNT(*) FROM reports")
    int getReportCount();

    @Query("UPDATE reports SET score = :score WHERE documentId = :documentId")
    void updateScore(String documentId, long score);

    @Query("UPDATE reports SET comments = :commentCount WHERE documentId = :documentId")
    void updateCommentCount(String documentId, long commentCount);

    @Query("UPDATE reports SET description = :description, hazardType = :hazardType, " +
           "localGov = :localGov, locationDetails = :locationDetails, " +
           "latitude = :latitude, longitude = :longitude, status = :status " +
           "WHERE documentId = :documentId")
    void updateReport(String documentId, String description, String hazardType,
                     String localGov, String locationDetails, double latitude,
                     double longitude, String status);

    @Query("DELETE FROM reports WHERE documentId = :documentId")
    void deleteByDocumentId(String documentId);
}

