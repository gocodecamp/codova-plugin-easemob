package com.bjzjns.hxplugin;

import android.content.Context;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import com.bjzjns.hxplugin.manager.HXManager;
import com.bjzjns.hxplugin.model.ConversationItemModel;
import com.bjzjns.hxplugin.model.ConversationListModel;
import com.bjzjns.hxplugin.model.ObjectModel;
import com.bjzjns.hxplugin.tools.GsonUtils;
import com.bjzjns.hxplugin.tools.LogUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hyphenate.EMCallBack;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMConversation;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.chat.EMTextMessageBody;
import com.hyphenate.easeui.EaseConstant;
import com.hyphenate.easeui.model.MessageData;
import com.hyphenate.easeui.model.MessageExtModel;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * This class echoes a string called from JavaScript.
 */
public class ZJNSHXPlugin extends CordovaPlugin {
    // 初始化环信
    private static final String INIT_HX = "initEaseMobile ";
    // 登录环信
    private static final String LOGIN_HX = "login";
    // 退出环信
    private static final String LOGOUT_HX = "logout";
    // 获取所有会话
    private static final String LOAD_ALL_CONVERSATION = "getAllConversations";
    // 删除会话
    private static final String DEL_CONVERSATION_ITEM = "delConversationItem";
    // 进入聊天
    private static final String GOTO_CHAT = "gotoChat";
    // 订阅会话列表变化消息
    private static final String REGISTER_SUBSCRIBERS_MESSAGE = "registerSubscribersMessage";
    private static CallbackContext mCallbackContext;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        LogUtils.d("ZJNSHXPlugin", "action =" + action + "args =" + args.toString());
        if (action.equals(INIT_HX)) {
            initHX();
            callbackContext.success();
            return true;
        } else if (action.equals(LOGIN_HX)) {
            loginHX(args.getString(0), args.getString(1), callbackContext);
            return true;
        } else if (action.equals(LOGOUT_HX)) {
            logout(true, callbackContext);
            return true;
        } else if (action.equals(LOAD_ALL_CONVERSATION)) {
            loadAllConversation(callbackContext);
            return true;
        } else if (action.equals(DEL_CONVERSATION_ITEM)) {
            delConversationItem(args.getString(0), callbackContext);
            return true;
        } else if (action.equals(GOTO_CHAT)) {
            gotoChat(args.getString(0));
            return true;
        } else if (action.equals(REGISTER_SUBSCRIBERS_MESSAGE)) {
            mCallbackContext = callbackContext;
            return true;
        }
        return false;
    }

    private Context getContext() {
        return this.cordova.getActivity().getBaseContext();
    }

    /**
     * 初始化环信
     */
    private void initHX() {
        HXManager.getInstance().init(this.cordova.getActivity().getApplicationContext());
    }

    /**
     * 进入聊天
     *
     * @param sendVal
     */
    private void gotoChat(String sendVal) {
        HXManager.getInstance().startChatActivity(getContext(), sendVal);
    }

    /**
     * 登录环信
     *
     * @param userName
     * @param password
     */
    private void loginHX(String userName, String password, final CallbackContext callbackContext) {
        HXManager.getInstance().loginHX(userName, password, new EMCallBack() {
            @Override
            public void onSuccess() {
                callbackContext.success();
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        EMClient.getInstance().groupManager().loadAllGroups();
                        EMClient.getInstance().chatManager().loadAllConversations();
                    }
                });
            }

            @Override
            public void onError(int i, String s) {
                callbackContext.error(i + ":" + s);
            }

            @Override
            public void onProgress(int i, String s) {
            }
        });
    }

    /**
     * 退出环信
     *
     * @param unbindDeviceToken
     * @param callbackContext
     */
    private void logout(boolean unbindDeviceToken, final CallbackContext callbackContext) {
        HXManager.getInstance().logout(unbindDeviceToken, new EMCallBack() {

            @Override
            public void onSuccess() {
                callbackContext.success();
            }

            @Override
            public void onProgress(int progress, String status) {
            }

            @Override
            public void onError(int code, String error) {
                callbackContext.error(code + ":" + error);
            }
        });
    }

    /**
     * 获取所有会话
     *
     * @param callbackContext
     */
    private void loadAllConversation(final CallbackContext callbackContext) {
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    ConversationListModel conversationListModel = new ConversationListModel();
                    List<ConversationItemModel> conversationItemList = new ArrayList<ConversationItemModel>();
                    ConversationItemModel conversationItemModel;
                    EMMessage message;
                    Type typetoken = new TypeToken<MessageExtModel<MessageData>>() {
                    }.getType();
                    for (EMConversation emConversation : HXManager.getInstance().loadConversationList()) {
                        conversationItemModel = new ConversationItemModel();
                        message = emConversation.getLastMessage();
                        conversationItemModel.conversationId = emConversation.conversationId();
                        conversationItemModel.unreadMessagesCount = emConversation.getUnreadMsgCount() + "";
                        conversationItemModel.timestamp = message.getMsgTime() + "";
                        String content = "";
                        if (EMMessage.Type.TXT == message.getType()) {
                            ((EMTextMessageBody) message.getBody()).getMessage();
                        }
                        conversationItemModel.messageBodyContent = content;
                        conversationItemModel.messageBodyType = message.getType().ordinal() + "";
                        String extContent = message.getStringAttribute(EaseConstant.MESSAGE_ATTR_EXT, "");
                        MessageExtModel extModel = new Gson().fromJson(extContent, typetoken);
                        conversationItemModel.ext = extModel;
                        conversationItemList.add(conversationItemModel);
                    }
                    conversationListModel.conversationList = conversationItemList;
                    callbackContext.success(GsonUtils.toJson(conversationListModel));
                } catch (Exception e) {
                    callbackContext.error("load error");
                }
            }
        });
    }

    /**
     * 删除会话
     *
     * @param sendVal
     * @param callbackContext
     */
    private void delConversationItem(String sendVal, CallbackContext callbackContext) {
        ConversationItemModel conversation = GsonUtils.fromJson(sendVal, ConversationItemModel.class);
        if (null != conversation) {
            try {
                // 删除此会话
                HXManager.getInstance().delConversation(conversation.conversationId);
                callbackContext.success();
            } catch (Exception e) {
                e.printStackTrace();
                callbackContext.error("del conversationitem fail");
            }
        } else {
            callbackContext.error("you not send data");
        }
    }

    /**
     * 发送订阅会话列表变化的消息
     *
     * @param messageContent
     */
    public static void updateSubscribersMessage(String messageContent) {
        if (null != mCallbackContext) {
            PluginResult dataResult = new PluginResult(PluginResult.Status.OK, messageContent);
            // 非常重要
            dataResult.setKeepCallback(true);
            mCallbackContext.sendPluginResult(dataResult);
        }
    }

    /**
     * 进入设计师详情
     *
     * @param sendVal
     */
    public void gotoDesignerDeatil(String sendVal) {
        ObjectModel objectModel = GsonUtils.fromJson(sendVal, ObjectModel.class);
        this.webView.loadUrl("javascript:goToDesignerDetial(" + objectModel.id + ")");
    }

    /**
     * 进入用户详情
     *
     * @param sendVal
     */
    public void gotoUserDetail(String sendVal) {
        ObjectModel objectModel = GsonUtils.fromJson(sendVal, ObjectModel.class);
        this.webView.loadUrl("javascript:goToUserDetail(" + objectModel.id + ")");
    }

    /**
     * 进入商品详情
     *
     * @param sendVal
     */
    public void gotoProductDetail(String sendVal) {
        ObjectModel objectModel = GsonUtils.fromJson(sendVal, ObjectModel.class);
        this.webView.loadUrl("javascript:goToProductDetail(" + objectModel.id + ")");
    }
}