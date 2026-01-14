package com.gitgud.citywatch.data.cache;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.gitgud.citywatch.data.cache.dao.CacheMetadataDao;
import com.gitgud.citywatch.data.cache.dao.CommentDao;
import com.gitgud.citywatch.data.cache.dao.CommentVoteDao;
import com.gitgud.citywatch.data.cache.dao.ReportDao;
import com.gitgud.citywatch.data.cache.dao.ReportVoteDao;
import com.gitgud.citywatch.data.cache.dao.UserProfileDao;
import com.gitgud.citywatch.data.cache.entity.CacheMetadata;
import com.gitgud.citywatch.data.cache.entity.CachedComment;
import com.gitgud.citywatch.data.cache.entity.CachedCommentVote;
import com.gitgud.citywatch.data.cache.entity.CachedReport;
import com.gitgud.citywatch.data.cache.entity.CachedReportVote;
import com.gitgud.citywatch.data.cache.entity.CachedUserProfile;

/**
 * Room database for local caching of Firebase data
 */
@Database(
    entities = {
        CachedReport.class,
        CachedComment.class,
        CachedUserProfile.class,
        CachedReportVote.class,
        CachedCommentVote.class,
        CacheMetadata.class
    },
    version = 1,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final String DATABASE_NAME = "citywatch_cache";

    // DAOs
    public abstract ReportDao reportDao();
    public abstract CommentDao commentDao();
    public abstract UserProfileDao userProfileDao();
    public abstract ReportVoteDao reportVoteDao();
    public abstract CommentVoteDao commentVoteDao();
    public abstract CacheMetadataDao cacheMetadataDao();

    /**
     * Get singleton database instance
     */
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Clear the singleton instance (for testing purposes)
     */
    public static void clearInstance() {
        INSTANCE = null;
    }
}

