package com.maweiming.wechat.bot.service.message.core;

import com.maweiming.wechat.bot.model.core.WechatCore;
import com.maweiming.wechat.bot.model.group.GroupModel;
import com.maweiming.wechat.bot.model.listen.AddMessage;
import com.maweiming.wechat.bot.service.WechatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * maweiming.com
 * Copyright (C) 1994-2018 All Rights Reserved.
 *
 * @author CoderMa
 * @version BaseMessage.java, v 0.1 2018-11-06 00:06
 */
@Service
public class BaseMessage {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseMessage.class);

    @Autowired
    private WechatService wechatService;

    @Value("${wechat.cache.path}")
    public String wechatCachePath;

    /**
     * 消息接收者
     */
    private String toUserNameId;

    /**
     * 消息发送者
     */
    private String fromUserNameId;

    /**
     * 消息发送至
     */
    public String msgToUserName;

    /**
     * 消息来自
     */
    public String msgFromUserName;

    /**
     * 是否是群聊
     * @default false
     */
    public boolean group;

    /**
     * 发送者是不是本人
     */
    public boolean senderIsMe;

    /**
     * 消息
     */
    public AddMessage message;

    /**
     * 初始化数据
     * @param message
     */
    public void initData(AddMessage message){
        this.message = message;
        //我的用户id
        String myUserNameId = WechatCore.getToUser().getUserName();
        //消息接收者
        this.toUserNameId = message.getToUserName();
        this.msgToUserName = this.getUserName(toUserNameId);
        //消息发送者
        this.fromUserNameId = message.getFromUserName();
        this.msgFromUserName = this.getUserName(fromUserNameId);
        //是否是群聊消息
        group = (fromUserNameId.startsWith("@@") || toUserNameId.startsWith("@@"));
        //发送者不是本人
        senderIsMe = (myUserNameId.equals(fromUserNameId));
        //解析群聊消息发送者的昵称(content 前缀是@表示是群消息)
        if (fromUserNameId.startsWith("@@") && message.getContent().startsWith("@")) {
            //来自群里的消息
            String[] contentArray = message.getContent().split(":<br/>");
            String groupUserNameId = contentArray[0];
            //群友昵称
            msgToUserName = getUserName(groupUserNameId);
        }
    }

    /**
     * 打印消息
     * @param content 消息内容
     * @type type 消息类型
     */
    public void printMessage(String content,MessageType type){
        MessageCacheUtils.put(new MessageCache(message.getMsgId(),content,type));
        switch (type){
            case RECALL:
                //撤回
                LOGGER.info("{}:({})->({})->{}",(group ? "群聊":"普通消息"),msgFromUserName,msgToUserName, content);
                break;
            case TEXT:
                //文本内容
                LOGGER.info("{}:({})->({})->{}",(group ? "群聊":"普通消息"),msgFromUserName,msgToUserName, content);
                break;
            case OPEN:
                //打开窗口
                LOGGER.info("你打开了->{}{}",msgToUserName,(group ? "的群聊":"的聊天窗口"));
                break;
            case CLOSE:
                //关闭窗口
                LOGGER.info("你关闭了->{}{}",msgToUserName,(group ? "的群聊":"的聊天窗口"));
                break;
            default:
                //除了文本内容以外的消息
                LOGGER.info("{}:({})->({})->{}->{}",(group ? "群聊":"普通消息"),msgFromUserName,msgToUserName,type.getName(), content);
                break;
        }
    }

    /**
     * 获取会员名称
     * @param userNameId
     * @return
     */
    private String getUserName(String userNameId){
        String userName = WechatCore.getUserName(userNameId);
        if(null == userName){
            //未加入通讯录的群聊才会为空
            GroupModel groupInfo = wechatService.getGroupInfo(WechatCore.getScanCode(), WechatCore.getLoginModel(), userNameId);
            WechatCore.setContactData(groupInfo.getContactList());
            userName = WechatCore.getUserName(userNameId);
        }
        return userName;
    }

}
