package com.ding.luna.ext;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javafx.application.Platform;

public class Executor {

    private static final String USERAGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; rv:11.0) like Gecko";

    private static final int TIMEOUT = 10000;

    private RootView rv;

    private String captchaText;

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

    public synchronized void recognizeCaptcha(String text) {
        captchaText = text;
        notifyAll();
    }

    private void register(Account a) throws Exception {
        String domain = RegInput.instance().getDomain();
        if (domain == null) {
            throw new RuntimeException("0");
        }

        try {
            stage1(a, domain);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("1");
        }
    }

    private void stage1(Account a, String domain) throws Exception {
        String email = a.getEmail();
        String password = a.getPassword();

        StringBuilder sb = new StringBuilder();
        sb.append("https://").append(domain);
        sb.append("/gp/css/order-history/ref=nav__gno_yam_yrdrs");
        Document doc = getPage(sb.toString(), a, null);

        Element link = doc.getElementById("createAccountSubmit");
        if (link != null) {
            doc = getPage(link.absUrl("href"), a, null);
        } else {
            Element form = doc.getElementById("ap_signin_form");
            Map<String, String> data = extractFormData(form);
            data.put("create", "1");
            data.put("email", email);
            doc = getPage(form.absUrl("action"), a, data);
        }

        captchaText = null;
        if (isCaptchaPresent(doc)) {
            processCaptcha(doc, a);
            if (captchaText == null) {
                throw new RuntimeException("Captcha fail1");
            }
        }

        Element form = doc.getElementById("ap_register_form");
        Map<String, String> data = extractFormData(form);
        String realName = replacePlaceHolder(a.getRealName());
        data.put("customerName", realName);
        data.put("email", email);
        data.put("emailCheck", email);
        data.put("password", password);
        data.put("passwordCheck", password);
        if (captchaText != null) {
            data.put("guess", captchaText);
        }
        if (data.containsKey("customerNamePronunciation")) {
            data.put("customerNamePronunciation", realName);
        }

        try {
            doc = getPage(form.absUrl("action"), a, data);
            captchaText = null;
            if (isCaptchaPresent(doc)) {
                processCaptcha(doc, a);
                if (captchaText == null) {
                    throw new RuntimeException("Captcha fail2");
                }
                form = doc.getElementById("ap_register_form");
                data = extractFormData(form);
                data.put("password", password);
                data.put("passwordCheck", password);
                data.put("guess", captchaText);
                doc = getPage(form.absUrl("action"), a, data);
            }
        } catch (Exception e) {
            System.err.print(e.getMessage());
            doc = getPage("http://" + domain, a, null);
        }
        link = doc.getElementById("nav-link-yourAccount");
        if (link == null || !link.absUrl("href").contains("/gp/css/homepage.html")) {
            savePageSource(doc, "/Users/dingzw/Desktop/debug.html");
            throw new RuntimeException("Not landed on successful page");
        }
    }

    private boolean isCaptchaPresent(Document doc) {
        if (doc.getElementById("ap_captcha_guess") != null) {
            return true;
        }
        return doc.getElementById("auth-captcha-image") != null;
    }

    private void processCaptcha(Document doc, Account a) throws Exception {
        Element img = null;
        if (doc.getElementById("auth-captcha-image") != null) {
            img = doc.getElementById("auth-captcha-image");
        } else {
            img = doc.getElementById("ap_captcha_img").select("img").first();
        }

        Connection conn = Jsoup.connect(img.absUrl("src"));
        conn.userAgent(USERAGENT).timeout(15000).cookies(a.cookies());
        Response resp = conn.ignoreContentType(true).execute();
        File captchaFile = File.createTempFile("captcha", ".jpg");
        ImageIO.write(byteArrayToImage(resp.bodyAsBytes()), "jpg", captchaFile);

        Platform.runLater(() -> {
            try {
                rv.showCaptcha(captchaFile.toURI().toURL().toExternalForm());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } );
        synchronized (this) {
            while (true) {
                try {
                    wait(60000);
                    break;
                } catch (Exception e) {
                    // continue
                }
            }
        }
        if (captchaText == null) {
            Platform.runLater(() -> rv.showCaptcha(null));
            throw new RuntimeException("Captcha is not resolved");
        }
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

    private String replacePlaceHolder(String s) {
        s = s.replace("｛｝", "{}");
        return s.replace("{}", String.format("%04d", (int) (Math.random() * 10000)));
    }

    private Document getPage(String url, Account a, Map<String, String> data) throws Exception {
        Connection conn = Jsoup.connect(url);
        conn.userAgent(USERAGENT).timeout(TIMEOUT).cookies(a.cookies());
        Document doc = data == null ? conn.get() : conn.data(data).post();
        a.cookies().putAll(conn.response().cookies());
        return doc;
    }

    private Map<String, String> extractFormData(Element form) {
        Map<String, String> data = new HashMap<String, String>();
        for (Element input : form.getElementsByTag("input")) {
            if (!input.attr("type").equals("submit")) {
                String name = input.attr("name");
                String value = input.attr("value");
                if (!name.isEmpty()) {
                    data.put(name, value);
                }
            }
        }
        return data;
    }

    private BufferedImage byteArrayToImage(byte[] bytes) throws Exception {
        InputStream inputStream = new ByteArrayInputStream(bytes);
        try {
            return ImageIO.read(inputStream);
        } finally {
            inputStream.close();
        }
    }

    void savePageSource(Document doc, String path) {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(path);
            writer.print(doc.outerHtml());
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

}
