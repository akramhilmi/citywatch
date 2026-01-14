package com.gitgud.citywatch.data.cache.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.gitgud.citywatch.data.cache.entity.CachedReportVote;

import java.util.List;

/**
 * Data Access Object for cached report votes
 */
@Dao
public interface ReportVoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CachedReportVote> votes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CachedReportVote vote);

    @Query("SELECT * FROM report_votes WHERE reportId = :reportId AND userId = :userId")
    CachedReportVote getVote(String reportId, String userId);

    @Query("SELECT * FROM report_votes WHERE userId = :userId")
    List<CachedReportVote> getAllVotesForUser(String userId);

    @Query("DELETE FROM report_votes WHERE reportId = :reportId AND userId = :userId")
    void delete(String reportId, String userId);

    @Query("DELETE FROM report_votes WHERE userId = :userId")
    void deleteAllForUser(String userId);

    @Query("DELETE FROM report_votes")
    void deleteAll();
}

