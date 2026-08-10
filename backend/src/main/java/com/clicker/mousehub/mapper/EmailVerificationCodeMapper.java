package com.clicker.mousehub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clicker.mousehub.entity.EmailVerificationCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmailVerificationCodeMapper extends BaseMapper<EmailVerificationCode> {
    @Select("""
            SELECT * FROM email_verification_codes
            WHERE email = #{email} AND purpose = #{purpose}
            ORDER BY created_at DESC
            LIMIT 1 FOR UPDATE
            """)
    EmailVerificationCode selectLatestForUpdate(String email, String purpose);
}
