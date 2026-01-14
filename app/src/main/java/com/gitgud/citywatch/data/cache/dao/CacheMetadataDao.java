package com.gitgud.citywatch.data.cache.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.gitgud.citywatch.data.cache.entity.CacheMetadata;

/**
 * Data Access Object for cache metadata
 */
@Dao
public interface CacheMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CacheMetadata metadata);

    @Query("SELECT * FROM cache_metadata WHERE cacheKey = :cacheKey")
    CacheMetadata getMetadata(String cacheKey);

    @Query("DELETE FROM cache_metadata WHERE cacheKey = :cacheKey")
    void delete(String cacheKey);

    @Query("DELETE FROM cache_metadata")
    void deleteAll();

    @Query("SELECT dataHash FROM cache_metadata WHERE cacheKey = :cacheKey")
    String getDataHash(String cacheKey);

    @Query("SELECT lastUpdated FROM cache_metadata WHERE cacheKey = :cacheKey")
    Long getLastUpdated(String cacheKey);
}

