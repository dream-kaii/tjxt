package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author kaii
 * @since 2026-05-05
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final UserClient userClient;
    private final InteractionReplyMapper replyMapper;
    private final InteractionReplyServiceImpl replyService;
    private final SearchClient searchClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;
    /*
    * 保存提问
    * */
    @Override
    public void saveQuestion(QuestionFormDTO dto) {
        InteractionQuestion interactionQuestion = BeanUtils.copyBean(dto, InteractionQuestion.class);
        Long userId = UserContext.getUser();
        interactionQuestion.setUserId(userId);
        //保存到数据库中
        this.save(interactionQuestion);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO dto) {
        if(StringUtils.isBlank(dto.getTitle())||StringUtils.isBlank(dto.getDescription())||dto.getAnonymity() == null){
            throw new BizIllegalException("非法参数!");
        }
        InteractionQuestion question = this.getById(id);
        if(question==null){
            //查不到数据
            throw new BizIllegalException("非法参数!");
        }
        Long userId = UserContext.getUser();
        if(!userId.equals(question.getUserId())){
            //说明不是自己的问题
            throw new BadRequestException("不能修改他人数据!");
        }
        question = BeanUtils.copyBean(dto, InteractionQuestion.class);
        question.setId(id);
        //通过plus根据id修改数据库
        this.updateById(question);
    }

    /*
    * 分页查询提问数据
    * */
    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery pageQuery) {
        PageDTO<QuestionVO> pageDTO = new PageDTO<QuestionVO>();
        // 1.参数校验，课程id和小节id不能都为空
        Long courseId = pageQuery.getCourseId();
        Long sectionId = pageQuery.getSectionId();
        if(courseId == null && sectionId == null){
            throw new BadRequestException("章节和小节不能都为空!");
        }
        // 2.分页查询
        Long userId = UserContext.getUser();
        Page<InteractionQuestion> page = lambdaQuery()
                .select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))
                .eq(pageQuery.getOnlyMine(), InteractionQuestion::getUserId, userId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                .eq(InteractionQuestion::getHidden, false)
                .page(pageQuery.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        // 3.根据id查询提问者和最近一次回答的信息
        Set<Long> userIds = new HashSet<Long>();
        Set<Long> answerIds = new HashSet<>();
        // 3.1.得到问题当中的提问者id和最近一次回答的id
        for (InteractionQuestion q : records) {
            if(!q.getAnonymity()){   //只查询非匿名的问题
                userIds.add(q.getUserId());
            }
            answerIds.add(q.getLatestAnswerId());
        }
        // 3.2.根据id查询最近一次回答
        answerIds.remove(null);
        Map<Long,InteractionReply> replyMap=new HashMap<>(answerIds.size());
        if(CollUtils.isNotEmpty(answerIds)){
            List<InteractionReply> replies = replyMapper.selectBatchIds(answerIds);
            for (InteractionReply reply : replies) {
                replyMap.put(reply.getId(),reply);
                if(!reply.getAnonymity()){
                    userIds.add(reply.getUserId());
                }
            }
        }

        // 3.3.根据id查询用户信息（提问者）
        userIds.remove(null);
        Map<Long, UserDTO> userMap=new HashMap<>(userIds.size());
        if(CollUtils.isNotEmpty(userIds)){
            List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
            userMap = userDTOS
                    .stream()
                    .collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        // 4.封装VO
        List<QuestionVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion r : records) {
            // 4.1.将PO转为VO
            QuestionVO vo = BeanUtils.copyBean(r, QuestionVO.class);
            vo.setUserId(null);
            voList.add(vo);
            // 4.2.封装提问者信息
            if(!r.getAnonymity()){
                UserDTO userDTO = userMap.get(r.getUserId());
                if (userDTO != null) {
                    vo.setUserId(userDTO.getId());
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }

            // 4.3.封装最近一次回答的信息
            InteractionReply reply = replyMap.get(r.getLatestAnswerId());
            if (reply != null) {
                vo.setLatestReplyContent(reply.getContent());
                // 匿名用户直接忽略
                if(!reply.getAnonymity()){
                    UserDTO user = userMap.get(reply.getUserId());
                    vo.setLatestReplyUser(user.getName());
                }

            }
        }
        return PageDTO.of(page,voList);
    }

    /*
    * 根据提问ID查询具体内容
    * */
    @Override
    public QuestionVO queryQuestionById(Long id) {
        // 1.根据id查询数据
        InteractionQuestion question = getById(id);
        // 2.数据校验
        if(question == null || question.getHidden()){
            // 没有数据或者是被隐藏了
            return null;
        }
        // 3.查询提问者信息
        QuestionVO questionVO = BeanUtils.copyBean(question, QuestionVO.class);
        UserDTO userDTO = null;
        if(!question.getAnonymity()){
            userDTO = userClient.queryUserById(question.getUserId());
        }
        if(userDTO != null){
            questionVO.setUserName(userDTO.getName());
            questionVO.setUserIcon(userDTO.getIcon());
        }
        return questionVO;
    }

    /*
    * 根据id删除用户的提问
    * */
    @Override
    public void deleteQuestion(Long id) {
        //判断问题是否存在
        InteractionQuestion question = this.getById(id);
        if(question == null){
            throw new BizIllegalException("提问不存在！");
        }
        //判断提问是否为当前用户的
        Long QuserId = question.getUserId();
        Long userId = UserContext.getUser();
        if (!userId.equals(QuserId)){
            throw new BizIllegalException("非当前用户的提问 不可删除！");
        }
        //判断提问下是否存在二级提问或回答
        Long latestAnswerId = question.getLatestAnswerId();
        if(latestAnswerId == null){
            //说明没有二级提问和评论
            this.removeById(id);
        }
        //有二级 和评论
        LambdaQueryWrapper<InteractionReply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InteractionReply::getQuestionId,id);
        replyService.remove(wrapper);
        this.removeById(id);

    }

    /*
    * 管理端 查询提问
    * */
    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        // 1.处理课程名称，得到课程id
        List<Long> courseIds = null;
        if(StringUtils.isNotBlank(query.getCourseName())){
            courseIds = searchClient.queryCoursesIdByName(query.getCourseName());
            if(CollUtils.isEmpty(courseIds)){
                return PageDTO.empty(0L,0L);
            }
        }
        // 2.分页查询
        Integer status = query.getStatus();
        LocalDateTime beginTime = query.getBeginTime();
        LocalDateTime endTime = query.getEndTime();
        Page<InteractionQuestion> page = lambdaQuery()
                .in(courseIds != null, InteractionQuestion::getCourseId, courseIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .gt(beginTime != null, InteractionQuestion::getCreateTime, beginTime)
                .lt(endTime != null, InteractionQuestion::getCreateTime, endTime)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        // 3.准备VO需要的数据：用户数据、课程数据、章节数据
        Set<Long> userIds = new HashSet<>();
        Set<Long> cIds = new HashSet<>();
        Set<Long> cataIds = new HashSet<>();
        // 3.1.获取各种数据的id集合
        for (InteractionQuestion question : records) {
            userIds.add(question.getUserId());
            cIds.add(question.getCourseId());
            cataIds.add(question.getChapterId());
            cataIds.add(question.getSectionId());
        }
        // 3.2.根据id查询用户
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        Map<Long,UserDTO> userMap = new HashMap<>(userDTOS.size());
        if(CollUtils.isNotEmpty(userDTOS)){
            userMap = userDTOS
                    .stream()
                    .collect(Collectors.toMap(UserDTO::getId,u->u));
        }
        // 3.3.根据id查询课程
        List<CourseSimpleInfoDTO> courseDTOS = courseClient.getSimpleInfoList(cIds);
        Map<Long,CourseSimpleInfoDTO> cMap = new HashMap<>(courseDTOS.size());
        if(CollUtils.isNotEmpty(courseDTOS)){
            cMap = courseDTOS
                    .stream()
                    .collect(Collectors.toMap(CourseSimpleInfoDTO::getId,c->c));
        }
        // 3.4.根据id查询章节
        List<CataSimpleInfoDTO> cataDtos = catalogueClient.batchQueryCatalogue(cataIds);
        Map<Long,String> cataMap = new HashMap<>(cataDtos.size());
        if (CollUtils.isNotEmpty(cataDtos)){
            cataMap = cataDtos
                    .stream()
                    .collect(Collectors.toMap(CataSimpleInfoDTO::getId,CataSimpleInfoDTO::getName));
        }
        // 4.封装VO
         List<QuestionAdminVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion q : records) {
            // 4.1.将PO转VO，属性拷贝
            QuestionAdminVO vo = BeanUtils.copyBean(q, QuestionAdminVO.class);
            // 4.2.用户信息
            UserDTO userDTO = userMap.get(q.getUserId());
            if(userDTO != null){
                vo.setUserName(userDTO.getName());
            }
            // 4.3.课程信息以及分类信息
            CourseSimpleInfoDTO courseSimpleInfoDTO = cMap.get(q.getCourseId());
            if(courseSimpleInfoDTO != null){
                vo.setCourseName(courseSimpleInfoDTO.getName());
                vo.setCategoryName(categoryCache.getCategoryNames(courseSimpleInfoDTO.getCategoryIds()));
            }

            vo.setChapterName(cataMap.getOrDefault(q.getChapterId(), ""));
            vo.setSectionName(cataMap.getOrDefault(q.getSectionId(), ""));
            voList.add(vo);
        }



        // 4.4.章节信息
        return PageDTO.of(page,voList);
    }


    /*
    * 管理端根据提问ID查询详情信息并返回
    * */
    @Override
    public QuestionAdminVO queryQuestionAdmin(Long id) {
        InteractionQuestion question = getById(id);
        if(question == null){
            throw new BizIllegalException("数据库无此数据!");
        }
        //转成vo类
        QuestionAdminVO qVO = BeanUtils.copyBean(question, QuestionAdminVO.class);
        //查询用户详细信息 填充用户姓名 头像
        Long userId = question.getUserId();
        UserDTO userDTO = userClient.queryUserById(userId);
        if(userDTO == null){
            throw new BizIllegalException("无此用户数据！");
        }
        qVO.setUserName(userDTO.getName());
        qVO.setUserIcon(userDTO.getIcon());
        //查询课程分类
        CourseFullInfoDTO courseDTO = courseClient.getCourseInfoById(question.getCourseId(),false,true);
        if(courseDTO == null){
            throw new BizIllegalException("无此课程数据");
        }
        qVO.setCourseName(courseDTO.getName());
        qVO.setCategoryName(categoryCache.getCategoryNames(courseDTO.getCategoryIds()));
        List<Long> teacherIds = courseDTO.getTeacherIds();
        if(CollUtils.isNotEmpty(teacherIds)){
            List<UserDTO> teachers = userClient.queryUserByIds(teacherIds);
            String teacherName = teachers
                    .stream()
                    .map(UserDTO::getName)
                    .collect(Collectors.joining("/"));
            qVO.setTeacherName(teacherName);
        }
        //查询章节
        Set<Long> cataIds = new HashSet<>();
        cataIds.add(question.getSectionId());
        cataIds.add(question.getChapterId());
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(cataIds);
        if(CollUtils.isEmpty(cataSimpleInfoDTOS)){
            throw new BizIllegalException("章节和小节数据不能为空！");
        }
        Map<Long,String> cataMap = cataSimpleInfoDTOS.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        qVO.setSectionName(cataMap.getOrDefault(question.getSectionId(),""));
        qVO.setChapterName(cataMap.getOrDefault(question.getChapterId(),""));

        //完成查看
        question.setStatus(QuestionStatus.CHECKED);
        this.updateById(question);

        //返回vo
        return qVO;
    }

    /*
    * 管理端 修改问题是否隐藏
    * */
    @Override
    public void updateQuestionHidden(Long id, Boolean hidden) {
        InteractionQuestion question = this.getById(id);
        if(question == null){
            throw new BizIllegalException("无该提问数据");
        }
        question.setHidden(hidden);
        this.updateById(question);

    }

}
