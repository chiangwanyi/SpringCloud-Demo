package com.jwy.springcloud_demo.ruoyi.system.domain;

import java.util.Date;

/**
 * 公告已读记录表 sys_notice_read
 *
 * @author ruoyi
 */
public class SysNoticeRead
{
    /** 主键 */
    private Long readId;

    /** 公告ID */
    private Long noticeId;

    /** 用户ID */
    private Long userId;

    /** 阅读时间 */
    private Date readTime;

    public Long getReadId()
    {
        return readId;
    }

    public void setReadId(Long readId)
    {
        this.readId = readId;
    }

    public Long getNoticeId()
    {
        return noticeId;
    }

    public void setNoticeId(Long noticeId)
    {
        this.noticeId = noticeId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Date getReadTime()
    {
        return readTime;
    }

    public void setReadTime(Date readTime)
    {
        this.readTime = readTime;
    }
}
