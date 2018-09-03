package com.clsaa.maat.service;

import com.clsaa.maat.config.BizCodes;
import com.clsaa.maat.constant.state.MessageState;
import com.clsaa.maat.constant.state.StateContext;
import com.clsaa.maat.dao.MessageDao;
import com.clsaa.maat.model.po.Message;
import com.clsaa.maat.model.vo.MessageV1;
import com.clsaa.maat.result.BizAssert;
import com.clsaa.maat.result.exception.InvalidParameterException;
import com.clsaa.maat.result.exception.StandardBusinessException;
import com.clsaa.maat.utils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;


@Service
public class MessageService {

    @Autowired
    private MessageDao messageDao;

    /**
     * <p>
     * 添加消息,创建后消息的状态为 {@link MessageState#待确认}
     * </p>
     *
     * @param version      平台版本
     * @param messageId    消息id(用于唯一标志业务)
     * @param messageBody  消息内容
     * @param messageQueue 队列名称
     * @param queryURL     查询URL(用于状态确认)
     * @param remark       备注
     * @return {@link Mono<String>}
     * @summary 添加消息
     * @author 任贵杰 812022339@qq.com
     * @since 2018/9/2
     */
    public Mono<Message> addMessage(String version, String messageId, String messageBody, String messageQueue, String queryURL, String remark) {
        Message message = new Message();
        message.setVersion(version);
        message.setMessageId(messageId);
        message.setMessageBody(messageBody);
        message.setMessageTryTimes(Message.DEFAULT_TRY_TIMES);
        message.setMessageQueue(messageQueue);
        message.setMessageDead(Boolean.FALSE);
        message.setQueryURL(queryURL);
        message.setRemark(remark);
        message.setCtime(LocalDateTime.now());
        message.setMtime(LocalDateTime.now());
        message.setCuser(Message.DEFAULT_CUSER);
        message.setMuser(Message.DEFAULT_MUSER);
        message.setStatus(MessageState.待确认.getStateCode());
        return this.messageDao
                .save(message)
                .onErrorMap(e -> new StandardBusinessException(BizCodes.ERROR_INSERT.getCode(), "添加失败"));
    }

    /**
     * <p>
     * 根据实体id删除消息
     * </p>
     *
     * @param id 实体id
     * @return {@link Mono<Void>}
     * @summary 根据实体id删除消息
     * @author 任贵杰 812022339@qq.com
     * @since 2018/9/3
     */
    public Mono<Void> deleteMessageById(String id) {
        return this.messageDao.deleteById(id)
                .onErrorMap(e -> new StandardBusinessException(BizCodes.ERROR_DELETE.getCode(), "删除失败"));
    }

    /**
     * @param messageId 消息id
     * @param statusTo  目标状态
     * @return {@link Mono<Void>}
     * @summary 根据实体id将消息修改到目标状态
     * @author 任贵杰 812022339@qq.com
     * @since 2018/9/3
     */
    private Mono<Message> updateStatusByMessageId(String messageId, String statusTo) {
        return this.messageDao.findMessageByMessageId(messageId)
                .flatMap(existMessage -> {
                    //幂等,当前状态和目标状态相同
                    if (existMessage.getStatus().equals(statusTo)) {
                        return Mono.just(existMessage);
                    }
                    //判断当前状态是否能改为目标状态
                    BizAssert.allowed(StateContext.validateState(existMessage.getStatus(), statusTo),
                            String.format("当前状态编码为%s,不能修改为%s", existMessage.getStatus(), statusTo));
                    existMessage.setStatus(MessageState.已取消.getStateCode());
                    existMessage.setMuser(Message.DEFAULT_MUSER);
                    existMessage.setMtime(LocalDateTime.now());
                    return this.messageDao.save(existMessage);
                });
    }

    /**
     * <p>
     * 根据消息id将消息状态更新为已取消
     * </p>
     *
     * @param messageId 消息id(唯一标识业务)
     * @return {@link Mono<MessageV1>}
     * @summary 更新消息状态为已取消
     * @author 任贵杰 812022339@qq.com
     * @since 2018-09-03
     */
    public Mono<MessageV1> updateStatusToCanceledByMessageId(String messageId) {
        return this.updateStatusByMessageId(messageId, MessageState.已取消.getStateCode())
                .map(m -> BeanUtils.convertType(m, MessageV1.class));
    }

    /**
     * <p>
     * 根据消息id将消息状态更新为发送中
     * </p>
     *
     * @param messageId 消息id(唯一标识业务)
     * @return {@link Mono<MessageV1>}
     * @summary 更新消息状态为发送中
     * @author 任贵杰 812022339@qq.com
     * @since 2018-09-03
     */
    public Mono<MessageV1> updateStatusToSendingByMessageId(String messageId) {
        return this.updateStatusByMessageId(messageId, MessageState.发送中.getStateCode())
                .map(m -> BeanUtils.convertType(m, MessageV1.class));
    }

    /**
     * <p>
     * 根据消息id将消息状态更新为已完成
     * </p>
     *
     * @param messageId 消息id(唯一标识业务)
     * @return {@link Mono<MessageV1>}
     * @summary 更新消息状态为已完成
     * @author 任贵杰 812022339@qq.com
     * @since 2018-09-03
     */
    public Mono<MessageV1> updateStatusToFinishedByMessageId(String messageId) {
        return this.updateStatusByMessageId(messageId, MessageState.已完成.getStateCode())
                .map(m -> BeanUtils.convertType(m, MessageV1.class));
    }

    /**
     * <p>
     * 根据消息id将消息状态更新为已死亡
     * </p>
     *
     * @param messageId 消息id(唯一标识业务)
     * @return {@link Mono<MessageV1>}
     * @summary 更新消息状态为已死亡
     * @author 任贵杰 812022339@qq.com
     * @since 2018-09-03
     */
    public Mono<MessageV1> updateStatusToDeadByMessageId(String messageId) {
        return this.updateStatusByMessageId(messageId, MessageState.死亡.getStateCode())
                .map(m -> BeanUtils.convertType(m, MessageV1.class));
    }

    /**
     * <p>
     * 根据消息id查询消息
     * </p>
     *
     * @param messageId 消息id(唯一标识业务)
     * @return {@link Mono<MessageV1>}
     * @summary 根据消息id查询消息
     * @author 任贵杰 812022339@qq.com
     * @since 2018-09-03
     */
    public Mono<MessageV1> findMessageV1ByMessageId(String messageId) {
        return this.messageDao.findMessageByMessageId(messageId)
                .map(m -> BeanUtils.convertType(m, MessageV1.class));
    }


}
