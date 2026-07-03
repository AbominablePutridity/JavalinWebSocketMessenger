package com.mycompany.messenger.dto;

import java.time.LocalDateTime;

/**
 *
 * @author User
 */
public class MessageDto {
    private long id; // уникальный первичный ключ
    private String text;
    private LocalDateTime dateSend;
    private long channelId; // внешний ключ на таблицу канала, которому принадлежит сообщение, и автор этого сообщения

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public LocalDateTime getDateSend() {
        return dateSend;
    }

    public void setDateSend(LocalDateTime dateSend) {
        this.dateSend = dateSend;
    }

    public long getChannelId() {
        return channelId;
    }

    public void setChannelId(long channelId) {
        this.channelId = channelId;
    }
}
