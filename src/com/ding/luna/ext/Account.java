package com.ding.luna.ext;

import java.util.HashMap;
import java.util.Map;

public class Account {

    private int index;

    private String email;

    private String password;

    private volatile String realName = "Luna{}";

    private volatile String address = "随机选择";

    private volatile String creditCard = "随机选择";

    private volatile String regProgress = "-";

    private Map<String, String> cookies = new HashMap<>();

    public Account(int i, String e, String p) {
        this.index = i;
        this.email = e;
        this.password = p;
    }

    public Map<String, String> cookies() {
        return cookies;
    }

    public int getIndex() {
        return index;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCreditCard() {
        return creditCard;
    }

    public void setCreditCard(String creditCard) {
        this.creditCard = creditCard;
    }

    public String getRegProgress() {
        return regProgress;
    }

    public void setRegProgress(String regProgress) {
        this.regProgress = regProgress;
    }

    public String toString() {
        return String.format("[%s, %s, %s, %s, %s, %s]", index, email, password, realName, address, creditCard);
    }

}
