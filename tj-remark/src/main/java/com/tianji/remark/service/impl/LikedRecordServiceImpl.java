package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.SPELUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author kaii
 * @since 2026-05-14
 */
//@Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
    private final RabbitMqHelper rabbitMqHelper;

    /*
    * 点赞或者取消点赞
    * */
    @Override
    public void addLikeRecord(LikeRecordFormDTO recordDTO) {
        // 1.基于前端的参数，判断是执行点赞还是取消点赞
        boolean success = recordDTO.getLiked() ? like(recordDTO) : unlike(recordDTO);
        // 2.判断是否执行成功，如果失败，则直接结束
        if(!success){
            return;
        }
        // 3.如果执行成功，统计点赞总数
        Integer likedTimes = this.lambdaQuery()
                .eq(LikedRecord::getBizId, recordDTO.getBizId())
                .count();
        // 4.发送MQ通知
        rabbitMqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE,recordDTO.getBizType()),
                LikedTimesDTO.of(recordDTO.getBizId(), likedTimes)
        );
    }

    /*
    * 查询指定业务id的点赞状态
    * */
    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        Long userId = UserContext.getUser();
        /*for (Long bizId : bizIds) {
            Integer count = this.lambdaQuery()
                    .eq(LikedRecord::getBizId, bizId)
                    .eq(LikedRecord::getUserId, userId)
                    .count();
            if(count>0){
                //说明用户在该业务有点赞
                BizLikedIds.add(bizId);
            }
        }*/
        List<LikedRecord> list = this.lambdaQuery()
                .in(LikedRecord::getBizId, bizIds)
                .eq(LikedRecord::getUserId, userId)
                .list();
        Set<Long> BizLikedIds=list.stream()
                .map(LikedRecord::getBizId)
                .collect(Collectors.toSet());
        return BizLikedIds;
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {


    }

    /**
     * Redis→MySQL脏数据同步（纯MySQL实现无需此方法）
     */
    @Override
    public void syncDirtyRecordsToMysql(String bizType, int maxBizSize) {
        // 纯MySQL模式不需要同步，空实现
    }

    /*
    * 取消点赞 删除点赞记录
    * */
    private boolean unlike(LikeRecordFormDTO recordDTO) {
        Long userId = UserContext.getUser();
        return this.remove(new QueryWrapper<LikedRecord>()
                .lambda()
                .eq(LikedRecord::getUserId,userId)
                .eq(LikedRecord::getBizId,recordDTO.getBizId()));
    }
    /*
    * 点赞操作
    * */
    private boolean like(LikeRecordFormDTO recordDTO) {
        Long userId = UserContext.getUser();
        // 1.查询点赞记录
        Integer likedTimes = this.lambdaQuery()
                .eq(LikedRecord::getBizId, recordDTO.getBizId())
                .eq(LikedRecord::getUserId, userId)
                .count();
        // 2.判断是否存在，如果已经存在，直接结束
        if(likedTimes>0){
            return false;
        }
        // 3.如果不存在，直接新增
        LikedRecord r = new LikedRecord();
        r.setUserId(userId);
        r.setBizId(recordDTO.getBizId());
        r.setBizType(recordDTO.getBizType());
        this.save(r);
        return true;
    }
}
