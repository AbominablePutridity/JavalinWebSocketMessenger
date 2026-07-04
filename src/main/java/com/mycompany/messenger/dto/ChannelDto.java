package com.mycompany.messenger.dto;

import java.time.LocalDateTime;

/**
 *
 * @author User
 */
public class ChannelDto {
    private String code;          // уникальный идентификатор канала
    private String name;          // название канала
    private String description;   // описание канала
    private LocalDateTime creationDate; // дата и время создания канала
    private String ownerCode;     // код пользователя-владельца канала

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public String getOwnerCode() {
        return ownerCode;
    }

    public void setOwnerCode(String ownerCode) {
        this.ownerCode = ownerCode;
    }
}
