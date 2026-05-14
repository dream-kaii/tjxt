package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jodd.bean.BeanWalker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_CREATE_TIME;
import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_LIKED_TIME;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author kaii
 * @since 2026-05-05
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {
    private final IInteractionQuestionService questionService;
    private final UserClient userClient;
    /*
    * 保存评论或回答
    * */
    @Override
    public void savaReply(ReplyDTO replyDTO) {
        Long userId = UserContext.getUser();
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);
        //保存到数据库中
        this.save(reply);
        InteractionQuestion question = questionService.getById(replyDTO.getQuestionId());
        if(replyDTO.getAnswerId() == null){
            //说明为回答问题的
            //累加回答下的评论次数
            question.setAnswerTimes(question.getAnswerTimes()+1);
            question.setLatestAnswerId(reply.getId());
            questionService.updateById(question);
        }
        else {
            //说明为评论问题的
            //累加回答下的评论次数
            InteractionReply Lreply = this.getById(replyDTO.getAnswerId());
            Lreply.setReplyTimes(Lreply.getReplyTimes()+1);
            this.updateById(Lreply);
        }
        if(replyDTO.getIsStudent()){
            //为学生提交的
            question.setStatus(QuestionStatus.UN_CHECK);
            questionService.updateById(question);
        }



    }

    /*
    * 分页查询回答或评论
    * 回答列表：返回回答id、内容、是否匿名、回答人信息（匿名则不返回）、回答时间、评论数量、点赞数量
    * 评论列表：返回评论id、内容、是否匿名、评论人信息（匿名则不返回）、回答时间、点赞数量、目标用户昵称
    * */
    @Override
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery pageQuery) {
        // 1.校验questionId和answerId是否都为空
        if (pageQuery.getQuestionId() == null && pageQuery.getAnswerId() == null) {
            throw new BadRequestException("问题id和回答id不能都为空");
        }
        Long answerId = pageQuery.getAnswerId();
        Long questionId = pageQuery.getQuestionId();
        // 2.分页查询interaction_reply表
        // 如果传问题id则拼接问题id条件
        // 如果回答id没传，则查询answer_id为0的数据，也就是一级回答
        Page<InteractionReply> page = this.lambdaQuery()
                .eq(questionId != null, InteractionReply::getQuestionId, questionId)
                .eq(InteractionReply::getAnswerId, answerId == null ? 0L : answerId)
                .eq(InteractionReply::getHidden, false)
                .page(pageQuery.toMpPage(// 先根据点赞数降序排序，点赞数相同，再按照创建时间升序排序
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true)));
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.收集需要查询的用户id和目标回复id
        Set<Long> userIds = new HashSet<>();          // 需要查询用户信息的用户id集合
        Set<Long> targetReplyIds = new HashSet<>();    // 需要查询的目标回复id集合（用于获取目标用户昵称）
        for (InteractionReply reply : records) {
            // 非匿名的回答/评论，需要查询回复人的用户信息
            if (!reply.getAnonymity()) {
                userIds.add(reply.getUserId());
            }
            // 收集目标回复id（评论才有，用于后续查询目标用户昵称）
            if (reply.getTargetReplyId() != null) {
                targetReplyIds.add(reply.getTargetReplyId());
            }
        }
        // 4.批量查询目标回复，并收集目标回复中非匿名者的用户id
        Map<Long, InteractionReply> targetReplyMap = new HashMap<>();
        if (CollUtils.isNotEmpty(targetReplyIds)) {
            List<InteractionReply> targetReplies = this.listByIds(targetReplyIds);
            for (InteractionReply tr : targetReplies) {
                targetReplyMap.put(tr.getId(), tr);
                // 目标回复非匿名，才需要展示目标用户昵称
                if (!tr.getAnonymity()) {
                    userIds.add(tr.getUserId());
                }
            }
        }
        // 5.批量查询用户信息，避免N+1问题
        Map<Long, UserDTO> userMap = new HashMap<>();
        if (CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
            userMap = userDTOS.stream()
                    .collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 6.封装VO返回
        List<ReplyVO> voList = new ArrayList<>(records.size());
        for (InteractionReply reply : records) {
            ReplyVO vo = BeanUtils.copyBean(reply, ReplyVO.class);
            // 6.1.如果是匿名，清空userId，不暴露回答/评论人身份
            if (reply.getAnonymity()) {
                vo.setUserId(null);
            } else {
                // 非匿名时填充回答/评论人的用户信息（昵称、头像、用户类型）
                UserDTO userDTO = userMap.get(reply.getUserId());
                if (userDTO != null) {
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                    vo.setUserType(userDTO.getType());
                }
            }
            // 6.2.填充目标用户昵称（仅评论有targetReplyId，回答的targetReplyId为空）
            Long targetReplyId = reply.getTargetReplyId();
            if (targetReplyId != null) {
                InteractionReply targetReply = targetReplyMap.get(targetReplyId);
                if (targetReply != null && !targetReply.getAnonymity()) {
                    // 目标回复非匿名，展示目标用户的昵称
                    UserDTO targetUser = userMap.get(targetReply.getUserId());
                    if (targetUser != null) {
                        vo.setTargetUserName(targetUser.getName());
                    }
                }
            }
            voList.add(vo);
        }
        // 7.返回分页结果
        return PageDTO.of(page, voList);
    }

    /*
    * 管理端 分页查询回答或评论
    * */
    @Override
    public PageDTO<ReplyVO> queryAdminReplyPage(ReplyPageQuery pageQuery) {
        // 1.校验questionId和answerId是否都为空
        if (pageQuery.getQuestionId() == null && pageQuery.getAnswerId() == null) {
            throw new BadRequestException("问题id和回答id不能都为空");
        }
        Long answerId = pageQuery.getAnswerId();
        Long questionId = pageQuery.getQuestionId();
        // 2.分页查询interaction_reply表
        // 如果传问题id则拼接问题id条件
        // 如果回答id没传，则查询answer_id为0的数据，也就是一级回答
        Page<InteractionReply> page = this.lambdaQuery()
                .eq(questionId != null, InteractionReply::getQuestionId, questionId)
                .eq(InteractionReply::getAnswerId, answerId == null ? 0L : answerId)
                .page(pageQuery.toMpPage(// 先根据点赞数降序排序，点赞数相同，再按照创建时间升序排序
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true)));
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        //3.收集所有回答或评论的用户id
        Set<Long> userIds = new HashSet<>();
        for (InteractionReply record : records) {
            Long targetReplyId = record.getTargetReplyId();
            if(targetReplyId != null){
                InteractionReply reply = this.getById(targetReplyId);
                userIds.add(reply.getUserId());
            }
            userIds.add(record.getUserId());
        }
        // 5.批量查询用户信息，避免N+1问题
        Map<Long, UserDTO> userMap = new HashMap<>();
        if (CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
            userMap = userDTOS.stream()
                    .collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 6.封装VO返回
        List<ReplyVO> voList = new ArrayList<>(records.size());
        for (InteractionReply reply : records) {
            ReplyVO vo = BeanUtils.copyBean(reply, ReplyVO.class);
            //填充用户信息
            UserDTO userDTO = userMap.get(reply.getUserId());
            if (userDTO != null) {
                vo.setUserName(userDTO.getName());
                vo.setUserIcon(userDTO.getIcon());
                vo.setUserType(userDTO.getType());
            }
            // 6.2.填充目标用户昵称（仅评论有targetReplyId，回答的targetReplyId为空）
            Long targetReplyId = reply.getTargetReplyId();
            if (targetReplyId != null) {
                InteractionReply targetReply = this.getById(targetReplyId);
                if (targetReply != null) {
                    // 目标回复无论是否匿名，展示目标用户的昵称
                    UserDTO targetUser = userMap.get(targetReply.getUserId());
                    if (targetUser != null) {
                        vo.setTargetUserName(targetUser.getName());
                    }
                }
            }
            voList.add(vo);
        }
        return PageDTO.of(page,voList);
    }


    /*
    * 管理端显示或隐藏回答/评论
    * 评论：直接修改其hidden值 并对其父回答修改其评论数量
    * 回答：修改hidden值的同时，需要同步隐藏/显示其下所有评论，并调整回答的评论数量（replyTimes）
    * */
    @Override
    public void isHiddenReply(Long id, Boolean hidden) {
        // 1.查询回复数据是否存在
        InteractionReply reply = this.getById(id);
        if (reply == null) {
            throw new BadRequestException("该回复不存在！");
        }
        // 2.修改当前回复的隐藏状态
        reply.setHidden(hidden);
        this.updateById(reply);
        // 3.判断当前是回答还是评论：answerId为0或null表示一级回答，非0表示评论
        if (reply.getAnswerId() == null || reply.getAnswerId() == 0) {
            // 是一级回答，需要同步修改其下所有评论的隐藏状态
            // 3.1 统计状态会发生变化的评论数（当前状态与目标状态相反的数量，用于后续调整replyTimes）
            long changedCount = this.lambdaQuery()
                    .eq(InteractionReply::getAnswerId, id)
                    .eq(InteractionReply::getHidden, !hidden) // 找出隐藏状态与目标相反的评论
                    .count();
            // 3.2 批量更新该回答下所有评论的隐藏状态
            this.lambdaUpdate()
                    .eq(InteractionReply::getAnswerId, id)
                    .set(InteractionReply::getHidden, hidden)
                    .update();
            // 3.3 根据隐藏或显示，调整回答自身的评论数量（replyTimes），只按实际变化的数量调整
            if (changedCount > 0) {
                if (hidden) {
                    // 隐藏评论：显示数减少
                    reply.setReplyTimes(reply.getReplyTimes() - (int) changedCount);
                } else {
                    // 显示评论：显示数增加
                    reply.setReplyTimes(reply.getReplyTimes() + (int) changedCount);
                }
                // 保证评论数不为负数
                if (reply.getReplyTimes() < 0) {
                    reply.setReplyTimes(0);
                }
                this.updateById(reply);
            }
        } else {
            // 是评论，需要调整其所属父回答的评论数量（replyTimes）
            InteractionReply parentAnswer = this.getById(reply.getAnswerId());
            if (parentAnswer != null) {
                if (hidden) {
                    // 隐藏该评论，父回答的可见评论数减1
                    parentAnswer.setReplyTimes(parentAnswer.getReplyTimes() - 1);
                } else {
                    // 显示该评论，父回答的可见评论数加1
                    parentAnswer.setReplyTimes(parentAnswer.getReplyTimes() + 1);
                }
                // 保证评论数不为负数
                if (parentAnswer.getReplyTimes() < 0) {
                    parentAnswer.setReplyTimes(0);
                }
                this.updateById(parentAnswer);
            }
        }
    }
}
