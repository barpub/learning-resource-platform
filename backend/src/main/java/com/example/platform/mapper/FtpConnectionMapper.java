package com.example.platform.mapper;

import com.example.platform.entity.FtpConnection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FtpConnectionMapper {

    @Select("SELECT * FROM ftp_connection ORDER BY status DESC, id ASC")
    List<FtpConnection> findAll();

    @Select("SELECT * FROM ftp_connection WHERE status = 1 ORDER BY id ASC")
    List<FtpConnection> findEnabled();

    @Select("SELECT * FROM ftp_connection WHERE id = #{id}")
    FtpConnection findById(Long id);

    @Insert("INSERT INTO ftp_connection (name, host, port, username, password_cipher, passive_mode, encoding, home_path, description, status) "
            + "VALUES (#{name}, #{host}, #{port}, #{username}, #{passwordCipher}, #{passiveMode}, #{encoding}, #{homePath}, #{description}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FtpConnection connection);

    @Update("UPDATE ftp_connection SET name=#{name}, host=#{host}, port=#{port}, username=#{username}, "
            + "password_cipher=#{passwordCipher}, passive_mode=#{passiveMode}, encoding=#{encoding}, "
            + "home_path=#{homePath}, description=#{description}, status=#{status}, update_time=NOW() WHERE id=#{id}")
    int update(FtpConnection connection);

    @Delete("DELETE FROM ftp_connection WHERE id=#{id}")
    int delete(Long id);
}
