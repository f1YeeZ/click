package com.clicker.mousehub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clicker.mousehub.entity.SecurityRateLimitBucket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SecurityRateLimitBucketMapper extends BaseMapper<SecurityRateLimitBucket> {
    @Select("SELECT * FROM security_rate_limit_buckets WHERE bucket_key = #{bucketKey} FOR UPDATE")
    SecurityRateLimitBucket selectForUpdate(String bucketKey);

    @Insert("""
            INSERT INTO security_rate_limit_buckets
                (bucket_key, request_count, window_started_at, expires_at, updated_at)
            VALUES
                (#{bucketKey}, #{requestCount}, #{windowStartedAt}, #{expiresAt}, #{updatedAt})
            ON CONFLICT (bucket_key) DO NOTHING
            """)
    int insertIfAbsentPostgres(SecurityRateLimitBucket bucket);

    @Insert("""
            INSERT INTO security_rate_limit_buckets
                (bucket_key, request_count, window_started_at, expires_at, updated_at)
            SELECT #{bucketKey}, #{requestCount}, #{windowStartedAt}, #{expiresAt}, #{updatedAt}
            WHERE NOT EXISTS (SELECT 1 FROM security_rate_limit_buckets WHERE bucket_key = #{bucketKey})
            """)
    int insertIfAbsentH2(SecurityRateLimitBucket bucket);
}
