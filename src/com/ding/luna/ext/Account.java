package com.ding.luna.ext;

public class Account {

    private int index;

    private String email;

    private String password;

    private String realName = "Luna{}";

    private String address = "随机选择";

    private String creditCard = "随机选择";

    private String regProgress = "尚未开始";

    public Account(int i, String e, String p) {
        this.index = i;
        this.email = e;
        this.password = p;
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
