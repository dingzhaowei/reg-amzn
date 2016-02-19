package com.ding.luna.ext;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;

import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javafx.application.Platform;

public class Executor {

    private static final String USERAGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; rv:11.0) like Gecko";

    private static final int TIMEOUT = 10000;

    private static Random random = new Random();

    private RootView rv;

    private Document currPage;

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

        int bp = a.getLastBreakPoint();

        if (bp <= 1) {
            try {
                stage1(a, domain);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("1");
            }
        }

        if (bp <= 2) {
            try {
                stage2(a, domain);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("2");
            }
        }

        if (bp <= 3) {
            try {
                stage3(a, domain);
                stage4(a, domain);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("3");
            }
        }
    }

    private String getLoginUrl(String domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("https://").append(domain);
        sb.append("/gp/product/utility/edit-one-click-pref.html");
        sb.append("/ref=dp_oc_signin?ie=UTF8&query=&returnPath=");
        sb.append("%2Fgp%2Fcss%2Faccount%2Faddress%2Fview.html");
        return sb.toString();
    }

    private String getAddrViewUrl(String domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("https://").append(domain);
        sb.append("/gp/css/account/address/view.html");
        sb.append("?ie=UTF8&ref_=ya_change_1_click");
        return sb.toString();
    }

    private String getAddrEditUrl(String domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("https://").append(domain);
        sb.append("/gp/css/account/address/view.html");
        sb.append("?ie=UTF8&isDomestic=1&ref_=");
        sb.append("myab_view_domestic_address_form&viewID=newAddress");
        return sb.toString();
    }

    private void stage1(Account a, String domain) throws Exception {
        currPage = getPage(getLoginUrl(domain), a, null);
        if (currPage.location().contains("/account/address")) {
            return;
        }

        Element link = currPage.getElementById("createAccountSubmit");
        if (link != null) {
            currPage = getPage(link.absUrl("href"), a, null);
        } else {
            Element form = currPage.getElementById("ap_signin_form");
            Map<String, String> data = extractFormData(form);
            data.put("create", "1");
            data.put("email", a.getEmail());
            currPage = getPage(form.absUrl("action"), a, data);
        }

        captchaText = null;
        if (isCaptchaPresent(currPage)) {
            processCaptcha(currPage, a);
            if (captchaText == null) {
                throw new RuntimeException("Captcha fail1-1");
            }
        }

        Element form = currPage.getElementById("ap_register_form");
        if (form == null && currPage.getElementById("ap_signin_form") != null) {
            return; // 已经存在账号，直接登录
        }

        Map<String, String> data = extractFormData(form);
        String realName = replacePlaceHolder(a.getRealName());
        data.put("customerName", realName);
        data.put("email", a.getEmail());
        data.put("emailCheck", a.getEmail());
        data.put("password", a.getPassword());
        data.put("passwordCheck", a.getPassword());
        if (captchaText != null) {
            data.put("guess", captchaText);
        }
        if (data.containsKey("customerNamePronunciation")) {
            data.put("customerNamePronunciation", realName);
        }

        try {
            currPage = getPage(form.absUrl("action"), a, data);
            captchaText = null;
            if (isCaptchaPresent(currPage)) {
                processCaptcha(currPage, a);
                if (captchaText == null) {
                    throw new RuntimeException("Captcha fail1-2");
                }
                form = currPage.getElementById("ap_register_form");
                data = extractFormData(form);
                data.put("password", a.getPassword());
                data.put("passwordCheck", a.getPassword());
                data.put("guess", captchaText);
                currPage = getPage(form.absUrl("action"), a, data);
            }
        } catch (Exception e) {
            System.err.print(e.getMessage());
            currPage = getPage(getLoginUrl(domain), a, null);
        }
        link = currPage.getElementById("nav-link-yourAccount");
        if (link == null || !link.absUrl("href").contains("/gp/css/homepage.html")) {
            savePageSource(currPage, "/Users/dingzw/Desktop/debug.html");
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

    private void stage2(Account a, String domain) throws Exception {
        if (currPage == null || !currPage.location().contains("/account/address")) {
            siginToAddrView(a, domain);
        }
        if (!currPage.select("[id^=address-index]").isEmpty()) {
            return; // 已添加过地址
        }

        Map<String, List<String>> addresses = RegInput.instance().getAddresses();
        List<String> address = null;
        String addrName = a.getAddress();
        if (addrName.equals("随机选择")) {
            int i = random.nextInt(addresses.size());
            for (Map.Entry<String, List<String>> entry : addresses.entrySet()) {
                if (--i < 0) {
                    address = entry.getValue();
                    break;
                }
            }
        } else {
            address = addresses.get(addrName);
        }

        currPage = getPage(getAddrEditUrl(domain), a, null);
        Element form = currPage.select("form[action*=/account/address]").first();
        Map<String, String> data = populateAddress(form, address, domain);
        Element submitBtn = form.getElementById("myab_newAddressButton");
        data.put(submitBtn.attr("name"), submitBtn.attr("value"));

        currPage = getPage(form.absUrl("action"), a, data);
        if (currPage.select("[id^=address-index]").isEmpty()) {
            throw new RuntimeException("Address failed to be added");
        }
    }

    private Map<String, String> populateAddress(Element form, List<String> address, String domain) {
        Map<String, String> data = extractFormData(form);
        if (domain.contains("amazon.co.jp")) {
            data.put("enterAddressFullName", replacePlaceHolder(address.get(0)));

            String[] postCode = replacePlaceHolder(address.get(1)).split("-");
            data.put("enterAddressPostalCode", postCode[0]);
            data.put("enterAddressPostalCode2", postCode[1]);

            data.put("enterAddressStateOrRegion", replacePlaceHolder(address.get(2)));
            data.put("enterAddressAddressLine1", replacePlaceHolder(address.get(3)));
            data.put("enterAddressAddressLine2", replacePlaceHolder(address.get(4)));
            data.put("enterAddressAddressLine3", replacePlaceHolder(address.get(5)));
            data.put("enterAddressPhoneNumber", replacePlaceHolder(address.get(6)));
        } else {
            data.put("enterAddressFullName", replacePlaceHolder(address.get(0)));
            data.put("enterAddressAddressLine1", replacePlaceHolder(address.get(1)));
            data.put("enterAddressAddressLine2", replacePlaceHolder(address.get(2)));
            data.put("enterAddressCity", replacePlaceHolder(address.get(3)));
            data.put("enterAddressStateOrRegion", replacePlaceHolder(address.get(4)));
            data.put("enterAddressPostalCode", replacePlaceHolder(address.get(5)));

            Elements options = form.getElementById("enterAddressCountryCode").select("option");
            String country = replacePlaceHolder(address.get(6)), countryCode = "";
            for (Element option : options) {
                if (option.text().trim().equals(country)) {
                    countryCode = option.attr("value");
                    break;
                }
            }
            data.put("enterAddressCountryCode", countryCode);
            data.put("enterAddressPhoneNumber", replacePlaceHolder(address.get(7)));
        }
        return data;
    }

    private void stage3(Account a, String domain) throws Exception {
        if (currPage == null || !currPage.location().contains("/account/address")) {
            siginToAddrView(a, domain);
        }
        if (currPage.select("[id^=address-index]").isEmpty()) {
            throw new RuntimeException("There is no address");
        }

        Element addr = null;
        for (int i = 0; i < 2; i++) {
            if ((addr = currPage.getElementById("address-index-" + i)) != null) {
                break;
            }
        }

        Element form = addr.select("form[action*=editPaymentMethod]").first();
        Map<String, String> data = extractFormData(form);
        data.put("editPaymentMethod", "Submit");
        currPage = getPage(form.absUrl("action"), a, data);
        if (!currPage.select("input[id^=paymentMethod.]").isEmpty()) {
            currPage = getPage(getAddrViewUrl(domain), a, null);
            return; // 已配置信用卡
        }

        Map<String, List<String>> creditCards = RegInput.instance().getCreditCards();
        List<String> creditCard = null;
        String creditCardName = a.getCreditCard();
        if (creditCardName.equals("随机选择")) {
            int i = random.nextInt(creditCards.size());
            for (Map.Entry<String, List<String>> entry : creditCards.entrySet()) {
                if (--i < 0) {
                    creditCard = entry.getValue();
                    break;
                }
            }
        } else {
            creditCard = creditCards.get(creditCardName);
        }

        Elements forms = currPage.select("form[action*=/account/address]");
        for (int i = 0; i < forms.size(); i++) {
            form = forms.get(i);
            if (form.getElementById("creditCardIssuer") != null) {
                break;
            }
        }
        data = populateCreditCard(form, creditCard, domain);
        data.remove("ue_back");
        data.remove("newAddress");
        Element submitBtn = form.select("input[name^=addressID_]").first();
        data.put(submitBtn.attr("name") + ".x", "70");
        data.put(submitBtn.attr("name") + ".y", "10");
        currPage = getPage(form.absUrl("action"), a, data);
    }

    private Map<String, String> populateCreditCard(Element form, List<String> creditCard, String domain) {
        Map<String, String> data = extractFormData(form);

        Elements options = form.getElementById("creditCardIssuer").select("option");
        String cardIssuer = creditCard.get(0), cardIssuerCode = "V0";
        for (Element option : options) {
            if (option.text().trim().contains(cardIssuer)) {
                cardIssuerCode = option.attr("value");
                break;
            }
        }
        data.put("creditCardIssuer", cardIssuerCode);
        data.put("addCreditCardNumber", replacePlaceHolder(creditCard.get(1)));
        data.put("card-name", replacePlaceHolder(creditCard.get(2)));
        String[] pair = creditCard.get(3).split("/");
        data.put("newCreditCardMonth", pair[0].trim());
        data.put("newCreditCardYear", pair[1].trim());
        return data;
    }

    private void stage4(Account a, String domain) throws Exception {
        if (currPage.getElementById("one-click-address-exists") != null) {
            return; // 已有默认地址和支付
        }
        Element link = currPage.getElementById("myab-make-1click-link-1").select("a").first();
        currPage = getPage(link.absUrl("href"), a, null);
        if (currPage.getElementById("one-click-address-exists") == null) {
            throw new RuntimeException("Failed to set default address");
        }
    }

    private void siginToAddrView(Account a, String domain) throws Exception {
        currPage = getPage(getLoginUrl(domain), a, null);
        if (currPage.location().contains("/ap/signin")) {
            Element form = currPage.select("form[action*=/ap/signin]").first();
            Map<String, String> data = extractFormData(form);
            data.put("email", a.getEmail());
            data.put("password", a.getPassword());
            currPage = getPage(form.absUrl("action"), a, data);

            captchaText = null;
            if (isCaptchaPresent(currPage)) {
                processCaptcha(currPage, a);
                if (captchaText == null) {
                    throw new RuntimeException("Captcha fail2");
                }
                form = currPage.select("form[action*=/ap/signin]").first();
                data = extractFormData(form);
                data.put("password", a.getPassword());
                data.put("guess", captchaText);
                currPage = getPage(form.absUrl("action"), a, data);
            }
        }
        if (!currPage.location().contains("/account/address")) {
            currPage = getPage(getAddrViewUrl(domain), a, null);
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
        if (s.equals("-")) {
            return "";
        }
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
