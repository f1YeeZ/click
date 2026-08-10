package com.clicker.mousehub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clicker.mousehub.entity.AuthSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSession> {
    @Select("SELECT * FROM auth_sessions WHERE refresh_token_hash = #{tokenHash} FOR UPDATE")
    AuthSession selectByRefreshTokenHashForUpdate(String tokenHash);
}
