package com.gitgud.citywatch.data.cache.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.gitgud.citywatch.data.cache.entity.CachedUserProfile;

/**
 * Data Access Object for cached user profiles
 */
@Dao
public interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CachedUserProfile userProfile);

    @Query("SELECT * FROM user_profiles WHERE userId = :userId")
    CachedUserProfile getUserProfile(String userId);

    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    void deleteById(String userId);

    @Query("DELETE FROM user_profiles")
    void deleteAll();

    @Query("UPDATE user_profiles SET name = :name, cachedAt = :cachedAt WHERE userId = :userId")
    void updateName(String userId, String name, long cachedAt);

    @Query("UPDATE user_profiles SET phone = :phone, cachedAt = :cachedAt WHERE userId = :userId")
    void updatePhone(String userId, String phone, long cachedAt);

    @Query("UPDATE user_profiles SET profilePictureUrl = :url, cachedAt = :cachedAt WHERE userId = :userId")
    void updateProfilePictureUrl(String userId, String url, long cachedAt);
}

