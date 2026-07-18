package com.clicker.mousehub.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ReviewTagMapper {
    @Delete("DELETE FROM review_pro_tags WHERE review_id = #{reviewId}")
    void deletePros(UUID reviewId);

    @Delete("DELETE FROM review_con_tags WHERE review_id = #{reviewId}")
    void deleteCons(UUID reviewId);

    @Insert("INSERT INTO review_pro_tags(review_id, tag_code) VALUES(#{reviewId}, #{code})")
    void insertPro(@Param("reviewId") UUID reviewId, @Param("code") String code);

    @Insert("INSERT INTO review_con_tags(review_id, tag_code) VALUES(#{reviewId}, #{code})")
    void insertCon(@Param("reviewId") UUID reviewId, @Param("code") String code);

    @Select("SELECT tag_code FROM review_pro_tags WHERE review_id = #{reviewId} ORDER BY tag_code")
    List<String> selectPros(UUID reviewId);

    @Select("SELECT tag_code FROM review_con_tags WHERE review_id = #{reviewId} ORDER BY tag_code")
    List<String> selectCons(UUID reviewId);
}
