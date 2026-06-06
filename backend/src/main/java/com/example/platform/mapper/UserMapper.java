package com.example.platform.mapper;

import com.example.platform.entity.User;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {
    @Select("SELECT * FROM `user` WHERE id = #{id}")
    User findById(Long id);

    @Select("SELECT * FROM `user` WHERE username = #{username}")
    User findByUsername(String username);

    @Select("SELECT * FROM `user` WHERE email = #{email}")
    User findByEmail(String email);

    @Select("SELECT COUNT(*) FROM `user` WHERE username = #{username}")
    int countByUsername(String username);

    @Select("SELECT COUNT(*) FROM `user` WHERE email = #{email}")
    int countByEmail(String email);

    @Select("SELECT * FROM `user` ORDER BY create_time DESC")
    List<User> findAll();

    @Insert("INSERT INTO `user` (username, password, email, nickname, avatar, role, status) VALUES (#{username}, #{password}, #{email}, #{nickname}, #{avatar}, #{role}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE `user` SET email=#{email}, nickname=#{nickname}, avatar=#{avatar}, update_time=NOW() WHERE id=#{id}")
    int updateProfile(User user);

    @Update("UPDATE `user` SET password=#{password}, update_time=NOW() WHERE id=#{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    @Update("UPDATE `user` SET status=#{status}, update_time=NOW() WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Delete("DELETE FROM `user` WHERE id=#{id}")
    int delete(Long id);
}
