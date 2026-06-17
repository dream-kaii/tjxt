package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import com.tianji.learning.service.impl.InteractionReplyServiceImpl;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author kaii
 * @since 2026-05-05
 */
@RestController
@RequestMapping("/replies")
@RequiredArgsConstructor
public class InteractionReplyController {

    private final IInteractionReplyService replyService;
    @ApiOperation("新增回答或评论")
    @PostMapping
    public void savaReply(@RequestBody ReplyDTO replyDTO){
        replyService.savaReply(replyDTO);
    }

    @ApiOperation("分页查询回答或评论")
    @GetMapping("/page")
    public PageDTO<ReplyVO> queryReplyPage(@RequestBody ReplyPageQuery pageQuery){
        return replyService.queryReplyPage(pageQuery);
    }


}
