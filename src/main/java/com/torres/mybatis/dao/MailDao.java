package com.torres.mybatis.dao;

import com.torres.mybatis.dto.MailDTO;
import com.torres.mybatis.entity.Mail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface MailDao {

    /**
     * 插入一条邮箱信息
     */
    public long insertMail(Mail mail);

    /**
     * 删除一条邮箱信息
     */
    public int deleteMail(long id);

    /**
     * 更新一条邮箱信息
     */
    public int updateMail(Mail mail);

    /**
     * 查询邮箱列表
     */
    public List<Mail> selectMailList();

    /**
     * 根据主键id查询一条邮箱信息
     */
    public Mail selectMailById(long id);

    List<Mail> selectMailByMap(Map map);

}