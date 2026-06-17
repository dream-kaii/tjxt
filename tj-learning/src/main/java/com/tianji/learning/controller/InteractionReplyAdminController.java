package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/replies")
@RequiredArgsConstructor
public class InteractionReplyAdminController {

    private final IInteractionReplyService replyService;

    @ApiOperation("管理端查询回答或评论")
    @GetMapping("/page")
    public PageDTO<ReplyVO> queryAdminReplyPage(ReplyPageQuery pageQuery){
        return replyService.queryAdminReplyPage(pageQuery);
    }

    @ApiOperation("管理端显示或隐藏评论")
    @PutMapping("/{id}/hidden/{hidden}")
    public void isHiddenReply(@PathVariable("id") Long id,@PathVariable("hidden") Boolean hidden){
        replyService.isHiddenReply(id,hidden);
    }
}
