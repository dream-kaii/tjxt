package com.tianji.remark.service;

import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Set;

/**
 * 点赞记录服务接口
 * <p>
 * 采用 Redis（热数据） + MySQL（冷数据全量持久化） 混合架构：
 * <ul>
 *   <li><b>写入</b>：先写 Redis，打脏标记，异步同步到 MySQL</li>
 *   <li><b>查询</b>：先查 Redis，Miss 则回查 MySQL 并回种到 Redis（Cache-Aside 模式）</li>
 *   <li><b>同步</b>：定时任务将脏数据从 Redis 批量持久化到 MySQL</li>
 *   <li><b>淘汰</b>：定时清理 Redis 中的低频/过期数据，控制内存占用</li>
 * </ul>
 * </p>
 *
 * @author kaii
 * @since 2026-05-14
 */
public interface ILikedRecordService extends IService<LikedRecord> {

    /** 点赞或取消点赞（入口方法） */
    void addLikeRecord(LikeRecordFormDTO recordDTO);

    /** 查询指定业务ID列表中，当前用户已点赞的业务ID集合（Cache-Aside） */
    Set<Long> isBizLiked(List<Long> bizIds);

    /** 读取点赞总数变更并通过MQ发送（统计功能，不改动） */
    void readLikedTimesAndSendMessage(String bizType, int maxBizSize);

    /**
     * 将 Redis 中的脏点赞记录同步到 MySQL
     * <p>
     * 从脏标记集合中取出待同步的业务ID，
     * 将 Redis Hash 中的点赞记录与 MySQL 做增量对比，
     * 执行批量插入/删除，实现最终一致性。
     * </p>
     *
     * @param bizType 业务类型（用于日志）
     * @param maxBizSize 单次处理的最大业务数量（控制每次同步的数据量）
     */
    void syncDirtyRecordsToMysql(String bizType, int maxBizSize);
}
