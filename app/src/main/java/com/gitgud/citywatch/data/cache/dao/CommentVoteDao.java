package com.gitgud.citywatch.data.cache.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.gitgud.citywatch.data.cache.entity.CachedCommentVote;

import java.util.List;

/**
 * Data Access Object for cached comment votes
 */
@Dao
public interface CommentVoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CachedCommentVote> votes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CachedCommentVote vote);

    @Query("SELECT * FROM comment_votes WHERE commentId = :commentId AND userId = :userId")
    CachedCommentVote getVote(String commentId, String userId);

    @Query("SELECT * FROM comment_votes WHERE userId = :userId")
    List<CachedCommentVote> getAllVotesForUser(String userId);

    @Query("DELETE FROM comment_votes WHERE commentId = :commentId AND userId = :userId")
    void delete(String commentId, String userId);

    @Query("DELETE FROM comment_votes WHERE userId = :userId")
    void deleteAllForUser(String userId);

    @Query("DELETE FROM comment_votes")
    void deleteAll();
}

