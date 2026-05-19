package com.tianji.remark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.remark.domain.po.LikedRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 点赞记录表 Mapper
 * <p>
 * 在 Redis + MySQL 混合架构中，该 Mapper 负责：
 * <ul>
 *   <li>为 Cache-Aside 查询提供 MySQL 回查能力</li>
 *   <li>为定时同步任务提供批量插入/更新能力</li>
 * </ul>
 * </p>
 *
 * @author kaii
 * @since 2026-05-14
 */
public interface LikedRecordMapper extends BaseMapper<LikedRecord> {

    /**
     * 批量插入或更新点赞记录（UPSERT）
     * <p>
     * 利用 MySQL 的 ON DUPLICATE KEY UPDATE 特性：
     * 若 (biz_id, user_id) 唯一键冲突，则更新 biz_type 和 update_time；
     * 否则插入新记录。
     * 用于定时任务将 Redis 脏数据批量同步到 MySQL。
     * </p>
     *
     * @param records 待同步的点赞记录列表
     * @return 影响的记录数
     */
    int batchUpsert(@Param("records") List<LikedRecord> records);
}
