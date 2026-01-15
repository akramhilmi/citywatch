package com.gitgud.citywatch.data.cache.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.gitgud.citywatch.data.cache.entity.CachedComment;

import java.util.List;

/**
 * Data Access Object for cached comments
 */
@Dao
public interface CommentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CachedComment> comments);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CachedComment comment);

    @Query("SELECT * FROM comments WHERE reportId = :reportId ORDER BY datetime DESC")
    List<CachedComment> getCommentsForReport(String reportId);

    @Query("SELECT * FROM comments WHERE commentId = :commentId")
    CachedComment getCommentById(String commentId);

    @Query("DELETE FROM comments WHERE reportId = :reportId")
    void deleteByReportId(String reportId);

    @Query("DELETE FROM comments")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM comments WHERE reportId = :reportId")
    int getCommentCountForReport(String reportId);

    @Query("UPDATE comments SET score = :score WHERE commentId = :commentId")
    void updateScore(String commentId, long score);

    @Query("UPDATE comments SET content = :content WHERE commentId = :commentId")
    void updateComment(String commentId, String content);

    @Query("DELETE FROM comments WHERE commentId = :commentId")
    void deleteByCommentId(String commentId);
}
