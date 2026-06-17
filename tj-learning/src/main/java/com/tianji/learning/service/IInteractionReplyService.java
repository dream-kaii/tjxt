package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author kaii
 * @since 2026-05-05
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    void savaReply(ReplyDTO replyDTO);

    PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery pageQuery);

    PageDTO<ReplyVO> queryAdminReplyPage(ReplyPageQuery pageQuery);

    void isHiddenReply(Long id, Boolean hidden);
}
