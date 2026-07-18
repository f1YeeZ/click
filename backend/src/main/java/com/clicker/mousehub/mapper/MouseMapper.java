package com.clicker.mousehub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clicker.mousehub.entity.MouseDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MouseMapper extends BaseMapper<MouseDevice> {
    @Select("SELECT DISTINCT brand FROM mice WHERE status = 'PUBLISHED' ORDER BY brand")
    List<String> selectPublishedBrands();

    @Select("SELECT brand FROM (SELECT DISTINCT btrim(brand) AS brand FROM mice WHERE brand IS NOT NULL AND btrim(brand) <> '') distinct_brands ORDER BY LOWER(brand), brand")
    List<String> selectAllBrands();
}
