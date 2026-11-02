package cn.edu.academic.support.notification.mapper;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface NotificationRecipientMapper {
    @Insert("INSERT INTO notification_recipient(notification_id,user_id,read_at) VALUES(#{notificationId},#{userId},NULL)")
    void insert(@Param("notificationId") Long notificationId, @Param("userId") Long userId);

    @Select("""
        SELECT n.id,n.period_id AS periodId,n.title,n.content,n.audience_label AS audienceLabel,
               n.created_at AS createdAt,nr.read_at AS readAt,u.display_name AS createdByName
        FROM notification_recipient nr JOIN notification n ON n.id=nr.notification_id
        JOIN sys_user u ON u.id=n.created_by WHERE nr.user_id=#{userId}
        ORDER BY n.created_at DESC
        """)
    List<Map<String,Object>> selectMine(Long userId);

    @Update("UPDATE notification_recipient SET read_at=#{now} WHERE notification_id=#{notificationId} AND user_id=#{userId} AND read_at IS NULL")
    int markRead(@Param("notificationId") Long notificationId, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM notification_recipient WHERE user_id=#{userId} AND read_at IS NULL")
    long unreadCount(Long userId);
}
