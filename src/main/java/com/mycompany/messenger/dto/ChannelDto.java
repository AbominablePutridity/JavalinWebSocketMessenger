package com.mycompany.messenger.dto;

import java.time.LocalDateTime;

/**
 *
 * @author User
 */
public class ChannelDto {
    private String code; // уникальный идентификатор канала
    private String name;
    private String description;
    private LocalDateTime creationDate;

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
}
