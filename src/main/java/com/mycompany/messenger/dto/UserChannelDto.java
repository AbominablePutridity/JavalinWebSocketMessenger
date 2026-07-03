package com.mycompany.messenger.dto;

/**
 *
 * @author User
 */
public class UserChannelDto {
    private long id; // уникальное поле, первичный ключ
    private String userCode; // уникальный код пользователя (внешний ключ пользователя)
    private String channelCode; // уникальный код канала (внешний ключ канала)

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }
}
