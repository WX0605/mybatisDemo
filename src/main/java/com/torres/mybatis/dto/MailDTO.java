package com.torres.mybatis.dto;

public class MailDTO {
    private String userFor;

    public String getUserFor() {
        return userFor;
    }

    public void setUserFor(String userFor) {
        this.userFor = userFor;
    }

    @Override
    public String toString() {
        return "MailDTO{" +
                "userFor='" + userFor + '\'' +
                '}';
    }
}
