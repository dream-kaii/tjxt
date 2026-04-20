package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LessonChangeListener {
    private final ILearningLessonService lessonService;

    /*
    * 监听订单支付和课程报名的消息
    * @Param order 订单信息
    * */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void listenLessonPay(OrderBasicDTO order){
        //1.健壮性处理
        if(order == null || order.getOrderId() == null || CollUtils.isEmpty(order.getCourseIds())){
            log.error("接受到MQ消息有误，订单数据为空！");
            return;
        }
        //2.添加课程
        log.debug("监听到用户{}的订单{}，需要添加课程{}到课表中", order.getUserId(), order.getOrderId(), order.getCourseIds());
        lessonService.addUserLessons(order.getUserId(), order.getCourseIds());

    }

    /*
     * 监听订单退款和课程取消的消息
     * @Param order 订单信息
     * */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void listenLessonRefund(OrderBasicDTO order){
        // 健壮性处理
        if(order == null || order.getUserId() == null || CollUtils.isEmpty(order.getCourseIds())){
            log.error("接收到MQ消息有误，订单数据为空");
            return;//不能抛出异常 否则开启了重试
        }
        // 删除课程表中删除该课程
        log.debug("监听到用户{}的订单{}，需要在课表删除课程{}中"
                , order.getUserId(), order.getOrderId(), order.getCourseIds());
        lessonService.deleteMyLesson(order.getCourseIds());
    }
}
