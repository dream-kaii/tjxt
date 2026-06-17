package com.tianji.learning.mq;


import com.tianji.common.constants.MqConstants;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.message.SignMessage;
import com.tianji.learning.service.IPointsRecordService;
import com.tianji.learning.service.impl.PointsRecordServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LearningPointsListener {
    private final IPointsRecordService pointsRecordService;

    //监听新增问答事件
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.points.queue",durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_REPLY
    ))
    public void listenWriteReplyMessage(Long userId){
        pointsRecordService.addPointsRecord(userId,5, PointsRecordType.QA);
    }


    // 监听签到事件
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "sign.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.SIGN_IN
    ))
    public void listenSignInMessage(SignMessage message){
        pointsRecordService.addPointsRecord(message.getUserId(), message.getPoints(), PointsRecordType.SIGN);
    }


    //监听课程学习事件
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "learning.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.LEARN_SECTION
    ))
    public void listenLessonInMessage(Long userId){
        pointsRecordService.addPointsRecord(userId,10,PointsRecordType.LEARNING);
    }
}
