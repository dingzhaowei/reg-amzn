package com.ding.luna.ext;

public class RegConf {

    private static RegConf instance = new RegConf();

    private String range;

    private int numPerGroup;

    private long intervalPerGroup;

    private long intervalPerAccount;

    public static RegConf instance() {
        return instance;
    }

    private RegConf() {

    }

    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range.replace("－", "-");
    }

    public int getNumPerGroup() {
        return numPerGroup;
    }

    public void setNumPerGroup(int numPerGroup) {
        this.numPerGroup = numPerGroup;
    }

    public long getIntervalPerGroup() {
        return intervalPerGroup;
    }

    public void setIntervalPerGroup(long intervalPerGroup) {
        this.intervalPerGroup = intervalPerGroup;
    }

    public long getIntervalPerAccount() {
        return intervalPerAccount;
    }

    public void setIntervalPerAccount(long intervalPerAccount) {
        this.intervalPerAccount = intervalPerAccount;
    }

}
