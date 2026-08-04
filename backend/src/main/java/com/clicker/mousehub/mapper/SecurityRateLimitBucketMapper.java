package com.clicker.mousehub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clicker.mousehub.entity.SecurityRateLimitBucket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SecurityRateLimitBucketMapper extends BaseMapper<SecurityRateLimitBucket> {
    @Select("SELECT * FROM security_rate_limit_buckets WHERE bucket_key = #{bucketKey} FOR UPDATE")
    SecurityRateLimitBucket selectForUpdate(String bucketKey);
}
