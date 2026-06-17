package com.tianji.remark.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 点赞业务类型配置属性
 * <p>
 * 从 Nacos 配置文件动态读取点赞业务类型列表，实现业务类型的动态配置。
 * 当 Nacos 中配置变更时，{@link RefreshScope} 会确保该 bean 被重新创建，
 * 从而使定时任务 {@link com.tianji.remark.task.LikedTimesCheckTask} 能感知到新增或移除的业务类型。
 * </p>
 *
 * <pre>
 * 配置示例（bootstrap.yml 或 Nacos）：
 * tj:
 *   remark:
 *     biz-types:
 *       - QA
 *       - NOTE
 * </pre>
 *
 * @author kaii
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "tj.remark")
public class RemarkBizTypeProperties {

    /**
     * 需要处理的点赞业务类型列表
     * 每个元素对应一种业务场景，如：
     * QA   - 问答互动回复
     * NOTE - 笔记
     */
    private List<String> bizTypes;
}
