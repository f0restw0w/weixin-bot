package com.maweiming.wechat.bot.service.impl;

import com.alibaba.fastjson.JSON;
import com.maweiming.wechat.bot.dao.WechatDao;
import com.maweiming.wechat.bot.model.contact.ContactList;
import com.maweiming.wechat.bot.model.contact.ContactMemberModel;
import com.maweiming.wechat.bot.model.contact.ContactModel;
import com.maweiming.wechat.bot.model.group.GroupInfo;
import com.maweiming.wechat.bot.model.group.GroupModel;
import com.maweiming.wechat.bot.model.login.LoginModel;
import com.maweiming.wechat.bot.model.scan.ScanCode;
import com.maweiming.wechat.bot.model.initialization.InitModel;
import com.maweiming.wechat.bot.model.initialization.UserModel;
import com.maweiming.wechat.bot.model.statusnotify.StatusNotify;
import com.maweiming.wechat.bot.service.WechatService;
import com.maweiming.wechat.bot.utils.HttpUtils;
import com.maweiming.wechat.bot.utils.WechatCode;
import com.maweiming.wechat.bot.utils.XMLUtils;
import com.xiaoleilu.hutool.util.ReUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * maweiming.com
 * Copyright (C) 1994-2018 All Rights Reserved.
 *
 * @author CoderMa
 * @version WechatServiceImpl.java, v 0.1 2018-10-31 00:48
 */
@Service
public class WechatServiceImpl implements WechatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WechatServiceImpl.class);

    @Value("#{'${wechat.special.usernames}'.split(',')}")
    private List<String> specialUserName;

    @Autowired
    private WechatDao wechatDao;

    @Override
    public String getUUID() {
        LOGGER.info("正在获取uuid...");
        //window.QRLogin.code = 200; window.QRLogin.uuid = "4ZPS4lfkMA==";
        String uuidContent = wechatDao.getUUID();
        String regex = "window.QRLogin.code = (\\d+); window.QRLogin.uuid = \"(\\S+?)\"";
        String code = ReUtil.get(regex, uuidContent,1);
        String uuid = ReUtil.get(regex, uuidContent,2);
        if(!HttpUtils.SUCCESS_CODE.equals(code)){
            LOGGER.error("getUUID error,uuidContent={}",uuidContent);
        }
        return uuid;
    }

    @Override
    public ScanCode waitForLogin(String uuid, Integer tip) {
        try {
            Thread.sleep(tip*1000);
            String content = wechatDao.waitForLogin(uuid, tip);
            String codeRegex = "window.code=(\\d+);";
            String code = ReUtil.get(codeRegex, content, 1);
            if(StringUtils.isBlank(code)){
                LOGGER.error("code is null,content={}",content);
                return null;
            }
            switch (code){
                case WechatCode.SUCCESS:
                    String redirectUriRegex = "window.redirect_uri=\"(\\S+?)\";";
                    String redirectUri = ReUtil.get(redirectUriRegex, content, 1)+"&fun=new";
                    String baseUri = redirectUri.substring(0,redirectUri.lastIndexOf("/"));
                    return new ScanCode(redirectUri,baseUri);
                case WechatCode.WAITING_VERIFY:
                    LOGGER.info("请在手机上点击确认以登录 ...");
                    break;
                case WechatCode.TIMEOUT:
                    LOGGER.error("登陆超时,content={}",content);
                    break;
                default:
                    LOGGER.error("登陆异常,content={}",content);
                    break;
            }
        } catch (InterruptedException e) {
            LOGGER.error("waitForLogin error,",e);
        }
        return null;
    }

    @Override
    public LoginModel login(ScanCode scanCode) {
        LOGGER.info("正在登录...");
        String loginXML = wechatDao.login(scanCode);
        try {
            LoginModel loginModel = XMLUtils.toObject(loginXML, LoginModel.class);
            if(loginModel.isVerify()){
                return loginModel;
            }else{
                LOGGER.error("LoginModel Verify error,loginModel={}",loginModel);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public InitModel initialization(ScanCode scanCode, LoginModel loginModel) {
        LOGGER.info("正在初始化数据...");
        String content = wechatDao.initialization(scanCode, loginModel);
        InitModel initModel = JSON.parseObject(content,InitModel.class);
        return initModel;
    }

    @Override
    public String statusNotify(ScanCode scanCode, LoginModel loginModel, UserModel toUser) {
        LOGGER.info("正在开启状态通知...");
        String content = wechatDao.statusNotify(scanCode, loginModel, toUser);
        StatusNotify statusNotify = JSON.parseObject(content,StatusNotify.class);
        if(!statusNotify.getBaseResponse().verify()){
            LOGGER.error("开启状态通知失败...content={}",content);
        }
        return statusNotify.getMsgID();
    }

    @Override
    public ContactList getContactList(ScanCode scanCode, LoginModel loginModel) {
        LOGGER.info("正在获取通讯录...");
        //分享公众号
        List<ContactMemberModel> publicList = new ArrayList<>();
        //群聊
        List<ContactMemberModel> groupList = new ArrayList<>();
        //朋友
        List<ContactMemberModel> friendList = new ArrayList<>();
        //特殊账号（文件助手之类的）
        List<ContactMemberModel> specialList = new ArrayList<>();
        //获取通讯录
        String content = wechatDao.getContactList(scanCode, loginModel);
        ContactModel contactModel = JSON.parseObject(content,ContactModel.class);
        if(!contactModel.getBaseResponse().verify()){
            LOGGER.error("获取联系人列表失败...content={}",content);
        }
        List<ContactMemberModel> memberList = contactModel.getMemberList();
        for (ContactMemberModel memberModel : memberList) {
            Long verifyFlag = memberModel.getVerifyFlag();
            String userName = memberModel.getUserName();

            if((verifyFlag & 8) != 0){
                //分享公众号
                publicList.add(memberModel);
            }else if(specialUserName.contains(userName)){
                //特殊账号
                specialList.add(memberModel);
            }else if(userName.startsWith("@@")){
                //群聊
                groupList.add(memberModel);
            }else{
                //朋友
                friendList.add(memberModel);
            }
        }
        return new ContactList(publicList,groupList,friendList,specialList);
    }

    @Override
    public GroupModel getGroupInfo(ScanCode scanCode, LoginModel loginModel, ContactList contactList) {
        List<ContactMemberModel> groupList = contactList.getGroupList();
        //提取出群名称
        List<GroupInfo> groupInfoList = groupList.stream().map(group -> new GroupInfo(group.getUserName())).collect(Collectors.toList());
        //获取群信息
        String content = wechatDao.getGroupInfo(scanCode, loginModel, groupInfoList);
        GroupModel groupModel = JSON.parseObject(content, GroupModel.class);
        return groupModel;
    }

    @Override
    public GroupModel getGroupInfo(ScanCode scanCode, LoginModel loginModel, String groupId) {
        //提取出群名称
        List<GroupInfo> groupInfoList = new ArrayList<>();
        groupInfoList.add(new GroupInfo(groupId));
        //获取群信息
        String content = wechatDao.getGroupInfo(scanCode, loginModel, groupInfoList);
        GroupModel groupModel = JSON.parseObject(content, GroupModel.class);
        return groupModel;
    }

}
