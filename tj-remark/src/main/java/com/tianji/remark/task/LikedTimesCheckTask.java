package com.tianji.remark.task;

import com.tianji.remark.config.RemarkBizTypeProperties;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 点赞数据定时任务
 *
 * <h3>核心职责</h3>
 * <ol>
 *   <li><b>点赞总数通知</b>：每20秒将 Redis ZSet 中缓存的点赞总数变更通过 MQ 发送给下游</li>
 *   <li><b>Redis→MySQL 持久化</b>：每分钟将 Redis 脏数据（有变更的点赞记录）同步到 MySQL</li>
 *   <li><b>Redis 空标记清理</b>：每30分钟清理过期的空数据标记（虽然标记本身有 TTL，但主动清理更可控）</li>
 * </ol>
 *
 * <h3>冷数据淘汰策略（预留）</h3>
 * <p>
 * 当业务方（课程服务）通知某课程已下架时，对应 bizId 的点赞记录可从 Redis 删除，
 * 仅保留在 MySQL 中。后续查询走 Cache-Aside 模式回种。
 * 可通过监听课程下架 MQ 消息实现，届时新增一个 Listener 方法调用 Redis DEL 即可。
 * </p>
 *
 * @author kaii
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikedTimesCheckTask {

    private final ILikedRecordService recordService;
    private final RemarkBizTypeProperties bizTypeProperties;

    /** 每次从 Redis ZSet 中取出的最大业务记录数（点赞总数同步用） */
    private static final int MAX_BIZ_SIZE = 30;

    /** 每次从脏标记集合中取出的最大业务记录数（持久化用） */
    private static final int MAX_DIRTY_SIZE = 50;

    // =====================================================
    //  任务1：点赞总数通知（每20秒）
    //  将 Redis ZSet 中的点赞总数变更通过 MQ 发送
    // =====================================================

    /**
     * 点赞总数定时检查并发送 MQ
     * <p>
     * 遍历所有配置的业务类型（由 Nacos 动态管理），
     * 从 Redis ZSet 中取出缓存的点赞总数变更，通过 MQ 发送给下游消费。
     * bizTypes 从配置文件动态读取，支持 Nacos 热更新。
     * </p>
     */
    @Scheduled(fixedDelay = 20000)
    public void checkLikedTimes() {
        List<String> bizTypes = bizTypeProperties.getBizTypes();
        if (bizTypes == null || bizTypes.isEmpty()) {
            log.warn("点赞业务类型配置为空，跳过本轮总数检查");
            return;
        }
        log.debug("开始处理点赞总数，bizTypes={}", bizTypes);
        for (String bizType : bizTypes) {
            recordService.readLikedTimesAndSendMessage(bizType, MAX_BIZ_SIZE);
        }
    }

    // =====================================================
    //  任务2：Redis → MySQL 持久化（每60秒）
    //  将 Redis 中有变更的点赞记录同步到 MySQL
    // =====================================================

    /**
     * 将 Redis 脏数据定期同步到 MySQL
     * <p>
     * 这是 Redis+MySQL 混合架构的核心定时任务：
     * 从脏标记集合 {@code likes:dirty:bizIds} 中取出有变更的业务ID，
     * 将 Redis Hash 中的点赞记录与 MySQL 做增量对比后批量持久化。
     * </p>
     *
     * <h4>为什么用脏标记而不是全量遍历？</h4>
     * <p>
     * 全量遍历 Redis 中所有 bizId 成本太高（可能有成千上万个业务）。
     * 脏标记机制只需处理有变更的 bizId，极大减少无效扫描。
     * 即使脏标记因 Redis 故障丢失，下次用户点赞时会重新标记，最终一致性有保障。
     * </p>
     */
    @Scheduled(fixedDelay = 60000)
    public void syncDirtyRecords() {
        List<String> bizTypes = bizTypeProperties.getBizTypes();
        if (bizTypes == null || bizTypes.isEmpty()) {
            return;
        }
        for (String bizType : bizTypes) {
            try {
                recordService.syncDirtyRecordsToMysql(bizType, MAX_DIRTY_SIZE);
            } catch (Exception e) {
                log.error("[持久化] bizType={} 同步Redis→MySQL失败", bizType, e);
            }
        }
    }

    // =====================================================
    //  任务3：Redis 空标记清理（每30分钟）
    //  清理已过期的空数据标记，释放 Redis 内存
    // =====================================================

    /**
     * 清理过期的空数据标记（安全兜底）
     * <p>
     * 空标记 {@code likes:empty:biz:{bizId}} 在写入时就设置了 TTL（{@link RedisConstants#EMPTY_MARK_TTL_MINUTES}），
     * Redis 到期会自动删除。本任务只是一个安全兜底：当 Redis 内存压力大时主动扫描并清理。
     * 如果 Redis 实例内存充足，这个任务不会产生实质影响。
     * </p>
     */
    @Scheduled(fixedDelay = 1800000) // 30分钟
    public void cleanExpiredEmptyMarks() {
        // 空标记自带 TTL，Redis 到期自动清除，这里仅做兜底日志
        log.debug("[清理] 空标记过期检查（标记自带TTL，Redis自动清理）");
    }
}
