package com.ding.luna.ext;

import java.util.List;

import javafx.application.Platform;

public class Executor {

    private static final String USERAGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; rv:11.0) like Gecko";

    private RootView rv;

    private volatile int ns, nf;

    private volatile boolean stop;

    public Executor(RootView rv) {
        this.rv = rv;
    }

    public void run() {
        try {
            List<Account> accounts = RegInput.instance().getAccounts();

            int startIndex = 0, endIndex = 0;
            String range = RegConf.instance().getRange();
            if (range == null || range.isEmpty()) {
                startIndex = 0;
                endIndex = accounts.size() - 1;
            } else {
                String[] pair = range.split("-");
                startIndex = Integer.parseInt(pair[0].trim()) - 1;
                endIndex = Integer.parseInt(pair[1].trim()) - 1;
            }
            startIndex = startIndex < 0 ? 0 : startIndex;
            endIndex = endIndex >= accounts.size() ? accounts.size() - 1 : endIndex;

            int n = -1;
            for (int i = startIndex; i <= endIndex; i++) {
                synchronized (this) {
                    if (stop) {
                        break;
                    }
                    updateProgressWithCount("注册进行中");
                }

                Account a = accounts.get(i);
                if (a.getRegProgress().contains("注册成功")) {
                    continue;
                }

                if (++n == RegConf.instance().getNumPerGroup()) {
                    updateAccountProgress(a, "组间休息");
                    waitForTime(RegConf.instance().getIntervalPerGroup());
                    n = -1;
                } else if (n > 0) {
                    updateAccountProgress(a, "号间休息");
                    waitForTime(RegConf.instance().getIntervalPerAccount());
                }

                synchronized (this) {
                    if (stop) {
                        updateAccountProgress(a, "-");
                        break;
                    }
                }

                try {
                    updateAccountProgress(a, "正在注册");
                    register(a);
                    ns++;
                    updateAccountProgress(a, "注册成功");
                } catch (Exception e) {
                    updateAccountProgress(a, "注册失败[" + e.getMessage() + "]");
                    nf++;
                }
            }
            if (stop) {
                updateProgressWithCount("注册已停止");
            } else {
                updateProgressWithCount("注册已完成");
            }
        } catch (Exception ex) {
            updateProgressWithoutCount("注册遇到问题已中断，请您反馈");
        }
    }

    public synchronized void stop() {
        updateProgressWithCount("正在停止..");
        stop = true;
    }

    private void register(Account a) throws Exception {
        int timeout = 10000;

    }

    private void updateProgressWithCount(String msg) {
        Platform.runLater(() -> {
            rv.setProgress(String.format("%s，成功：%d，失败：%d", msg, ns, nf));
        } );
    }

    private void updateProgressWithoutCount(String msg) {
        Platform.runLater(() -> {
            rv.setProgress(msg);
        } );
    }

    private void updateAccountProgress(Account a, String msg) {
        Platform.runLater(() -> {
            a.setRegProgress(msg);
            rv.refreshAccountPrgress();
        } );
    }

    private void waitForTime(long time) {
        long start = System.currentTimeMillis();
        while (!stop) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // continue
            }
            if (System.currentTimeMillis() - start >= time) {
                break;
            }
        }
    }

}
