package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface UserMapper {

    /**
     * 根据openId查询用户
     * @param openId
     * @return
     */
    @Select("select * from user where openid=#{openId}")
    User getByOpenId(String openId);

    /**
     * 插入用户
     * @param user
     */
    void insert(User user);

    /**
     * 根据id查询用户
     * @param userId
     * @return
     */
    @Select("select  * from user where  id = #{userId}")
    User getById(Long userId);

    /**
     * 根据条件动态查询user数量
     * @param totalMap
     * @return
     */
    Integer countByMap(Map totalMap);
}
