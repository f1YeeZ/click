package com.clicker.mousehub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clicker.mousehub.entity.AdminLoginChallenge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

@Mapper
public interface AdminLoginChallengeMapper extends BaseMapper<AdminLoginChallenge> {
    @Select("SELECT * FROM admin_login_challenges WHERE id = #{id} FOR UPDATE")
    AdminLoginChallenge selectForUpdate(UUID id);
}
