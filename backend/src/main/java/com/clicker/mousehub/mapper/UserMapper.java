package com.clicker.mousehub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clicker.mousehub.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

@Mapper
public interface UserMapper extends BaseMapper<UserAccount> {
    @Select("SELECT * FROM users WHERE id = #{id} FOR UPDATE")
    UserAccount selectForUpdate(UUID id);
}
