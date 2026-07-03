package com.mycompany.messenger.dto;

import java.time.LocalDateTime;

/**
 *
 * @author User
 */
public class UserDto {
    private String code; // для поиска человека по этому уникальному id
    private String name;
    private String surname;
    private LocalDateTime registrationDate;
    private String login;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String Name) {
        this.name = Name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String Surname) {
        this.surname = Surname;
    }

    public LocalDateTime getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDateTime registrationDate) {
        this.registrationDate = registrationDate;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }
}
