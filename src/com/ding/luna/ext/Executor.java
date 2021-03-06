package com.ding.luna.ext;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final Random RAND = new Random();

    private static final Logger LOG = Logger.getLogger(Executor.class.getName());

    private RootView rv;

    private boolean asApp;

    private Proxy currProxy;

    private Document currPage;

    private String captchaText;

    private volatile int ns, nf;

    private volatile boolean stop;

    public Executor(RootView rv) {
        this.rv = rv;
        LOG.setLevel(Level.INFO);
    }

    public void run() {
        if (RegInput.instance().hasProxy()) {
            setProxyAuthenticator();
        }

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

            int n = -1, m = -1;
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

                int numPerGroup = RegConf.instance().getNumPerGroup();
                if (++n == numPerGroup && numPerGroup > 0) {
                    updateAccountProgress(a, "组间休息");
                    waitForTime(RegConf.instance().getIntervalPerGroup());
                    n = 0;
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

                if (++m >= RegInput.instance().getNumAccountsPerProxy()) {
                    m = 0;
                    currProxy = RegInput.instance().getProxy();
                    if (currProxy != null) {
                        LOG.info("当前代理服务器: " + currProxy.toString());
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

        asApp = false;
        int bp = a.getLastBreakPoint();

        if (bp <= 1) {
            try {
                updateAccountProgress(a, "建立账号");
                stage1(a, domain);
            } catch (Exception e) {
                saveCurrentPage();
                e.printStackTrace();
                throw new RuntimeException("1");
            }
        }

        if (bp <= 2) {
            try {
                updateAccountProgress(a, "添加地址");
                stage2(a, domain);
            } catch (Exception e) {
                saveCurrentPage();
                e.printStackTrace();
                throw new RuntimeException("2");
            }
        }

        if (bp <= 3) {
            try {
                updateAccountProgress(a, "设置支付");
                stage3(a, domain);
                stage4(a, domain);
            } catch (Exception e) {
                saveCurrentPage();
                e.printStackTrace();
                throw new RuntimeException("3");
            }
        }

        if (bp <= 4) {
            try {
                updateAccountProgress(a, "开启一键");
                asApp = true;
                enableOneClick(a, domain);
            } catch (Exception e) {
                saveCurrentPage();
                e.printStackTrace();
                throw new RuntimeException("4");
            }
        }
    }

    private String getLoginUrl1(String domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("https://").append(domain);
        sb.append("/gp/product/utility/edit-one-click-pref.html");
        sb.append("/ref=dp_oc_signin?ie=UTF8&query=&returnPath=");
        sb.append("%2Fa%2Faddresses%2Fref=ab_redirect");
        return sb.toString();
    }

    private String getLoginUrl2(String domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("https://").append(domain);
        sb.append("/gp/product/utility/edit-one-click-pref.html");
        sb.append("/ref=dp_oc_signin?ie=UTF8&query=&returnPath=");
        sb.append("%2Fgp%2Fcss%2Faccount%2Faddress%2Fview.html");
        sb.append("%3FOneClickSettingsQuery=1");
        sb.append("%2Fref=ya_address_book_one_click_link");
        return sb.toString();
    }

    private String getOneClickEditUrl(String domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("https://").append(domain);
        sb.append("/gp/product/utility/edit-one-click-pref.html");
        sb.append("/ref=dp_oc_signin?ie=UTF8&query=&returnPath=%2F");
        return sb.toString();
    }

    private String getAddrViewUrl(String domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("https://").append(domain);
        return sb.append("/a/addresses/ref=ab_redirect").toString();
    }

    private String getAddrEditUrl(String domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("https://").append(domain);
        sb.append("/a/addresses/add?ref=ya_address_book_add_button");
        return sb.toString();
    }

    private void stage1(Account a, String domain) throws Exception {
        currPage = getPage(getLoginUrl1(domain), a, null);
        if (currPage.location().contains("/a/addresses")) {
            LOG.info(a.getEmail() + "已经是登录状态，跳过阶段1");
            return;
        }

        LOG.info(a.getEmail() + "准备跳转到账号注册页面");

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
            LOG.info(a.getEmail() + "遇到验证码，要求用户输入");
            processCaptcha(currPage, a);
            if (captchaText == null) {
                throw new RuntimeException("Captcha fail1-1");
            }
        }

        Element form = currPage.getElementById("ap_register_form");
        if (form == null) {
            if (isThereHintForExistedAccount()) {
                LOG.info(a.getEmail() + "可能已经被注册过，跳过阶段1");
                return; // 已经存在账号，直接登录
            }
            throw new RuntimeException("页面上找不到注册表单");
        }

        LOG.info(a.getEmail() + "开始填写注册信息并提交");
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
                LOG.info(a.getEmail() + "遇到验证码，要求用户输入");
                processCaptcha(currPage, a);
                if (captchaText == null) {
                    throw new RuntimeException("Captcha fail1-2");
                }
                LOG.info(a.getEmail() + "重新带验证码提交注册");
                form = currPage.getElementById("ap_register_form");
                data = extractFormData(form);
                data.put("password", a.getPassword());
                data.put("passwordCheck", a.getPassword());
                data.put("guess", captchaText);
                currPage = getPage(form.absUrl("action"), a, data);
            }
        } catch (Exception e) {
            LOG.info(a.getEmail() + "提交注册时遇到问题，检查是否已成功");
            currPage = getPage(getLoginUrl1(domain), a, null);
        }
        link = currPage.getElementById("nav-link-accountList");
        if (link == null) {
            link = currPage.getElementById("nav-link-yourAccount");
        }
        if (link == null || !link.absUrl("href").contains("/homepage.html")) {
            throw new RuntimeException("Not landed on successful page");
        }
        LOG.info(a.getEmail() + "的账号已经成功建立，准备添加地址和支付");
    }

    private boolean isThereHintForExistedAccount() {
        if (currPage.getElementById("ap_signin_form") != null) {
            return true;
        }
        return currPage.getElementById("ap_email_verify_warn_register_pagelet") != null;
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

        Connection conn = Jsoup.connect(img.absUrl("src")).proxy(currProxy);
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
        });
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
        if (currPage == null || !currPage.location().contains("/a/addresses")) {
            LOG.info(a.getEmail() + "通过登录跳到地址/一键管理页面");
            siginToAddrView(a, domain);
        }
        if (currPage.getElementById("ya-myab-display-address-block-0") != null) {
            LOG.info(a.getEmail() + "已经有之前添加的地址，跳过阶段2");
            return; // 已添加过地址
        }

        Map<String, List<String>> addresses = RegInput.instance().getAddresses();
        List<String> address = null;
        String addrName = a.getAddress();
        if (addrName.equals("随机选择")) {
            int i = RAND.nextInt(addresses.size());
            for (Map.Entry<String, List<String>> entry : addresses.entrySet()) {
                if (--i < 0) {
                    address = entry.getValue();
                    break;
                }
            }
        } else {
            address = addresses.get(addrName);
        }

        LOG.info(a.getEmail() + "准备进入地址添加页面");
        currPage = getPage(getAddrEditUrl(domain), a, null);

        LOG.info(a.getEmail() + "准备填写并提交新地址");
        Element form = currPage.select("form[action*=/a/addresses/add]").first();
        Map<String, String> data = populateAddress(form, address, domain);
        currPage = getPage(form.absUrl("action"), a, data);
        if (currPage.getElementById("ya-myab-display-address-block-0") == null) {
            throw new RuntimeException("Address failed to be added");
        }
        LOG.info(a.getEmail() + "新地址添加成功，准备添加支付");
    }

    private Map<String, String> populateAddress(Element form, List<String> address, String domain) {
        Map<String, String> data = extractFormData(form);
        String prefix = "address-ui-widgets-";
        String countryCode = domain.substring(domain.lastIndexOf(".") + 1);

        data.put(prefix + "countryCode", countryCode.toUpperCase());
        data.put(prefix + "enterAddressFullName", replacePlaceHolder(address.get(0)));

        if (domain.contains("amazon.co.jp")) {
            String[] postCode = replacePlaceHolder(address.get(1)).split("-");
            data.put(prefix + "enterAddressPostalCodeOne", postCode[0]);
            data.put(prefix + "enterAddressPostalCodeTwo", postCode[1]);
            data.put(prefix + "enterAddressStateOrRegion", replacePlaceHolder(address.get(2)));
            data.put(prefix + "enterAddressLine1", replacePlaceHolder(address.get(3)));
            data.put(prefix + "enterAddressLine2", replacePlaceHolder(address.get(4)));
            data.put(prefix + "enterAddressLine3", replacePlaceHolder(address.get(5)));
            data.put(prefix + "enterAddressPhoneNumber", replacePlaceHolder(address.get(6)));
        } else {
            data.put(prefix + "enterAddressLine1", replacePlaceHolder(address.get(1)));
            data.put(prefix + "enterAddressLine2", replacePlaceHolder(address.get(2)));
            data.put(prefix + "enterAddressCity", replacePlaceHolder(address.get(3)));
            data.put(prefix + "enterAddressStateOrRegion", replacePlaceHolder(address.get(4)));
            data.put(prefix + "enterAddressPostalCode", replacePlaceHolder(address.get(5)));
            data.put(prefix + "enterAddressPhoneNumber", replacePlaceHolder(address.get(6)));
        }
        return data;
    }

    private void stage3(Account a, String domain) throws Exception {
        if (currPage == null || !currPage.location().contains("OneClickSettingsQuery")) {
            LOG.info(a.getEmail() + "通过登录跳到地址/一键管理页面");
            siginToOneClickSettings(a, domain);
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

        LOG.info(a.getEmail() + "准备进入支付设置页面");
        Element form = addr.select("form[action*=editPaymentMethod]").first();
        Map<String, String> data = extractFormData(form);
        data.put("editPaymentMethod", "Submit");
        currPage = getPage(form.absUrl("action"), a, data);
        if (!currPage.select("input[id^=paymentMethod.]").isEmpty()) {
            LOG.info(a.getEmail() + "之前已经配置过信用卡，跳过阶段3");
            currPage = getPage(getAddrViewUrl(domain), a, null);
            return; // 已配置信用卡
        }

        LOG.info(a.getEmail() + "准备填写并提交支付信息");
        Map<String, List<String>> creditCards = RegInput.instance().getCreditCards();
        if (RegInput.instance().getDomain().endsWith("jp") && creditCards.isEmpty()) {
            Elements forms = currPage.select("form[action*=/account/address]");
            List<String> directives = RegInput.instance().getDirectives();
            String pm = directives.contains("无信用卡时设置货到付款") ? "COD" : "CVS";
            for (int i = 0; i < forms.size(); i++) {
                form = forms.get(i);
                if (!form.select("input[value=" + pm + "]").isEmpty()) {
                    break;
                }
            }
            data = extractFormData(form);
            data.put("paymentMethod", pm);
            data.put("editPaymentMethod", "次に進む");
            data.remove("ue_back");
            data.remove("newAddress");
            data.remove("addressID_" + data.get("addressID"));
            currPage = getPage(form.absUrl("action"), a, data);
        } else {
            List<String> creditCard = null;
            String creditCardName = a.getCreditCard();
            if (creditCardName.equals("随机选择")) {
                int i = RAND.nextInt(creditCards.size());
                for (Map.Entry<String, List<String>> entry : creditCards.entrySet()) {
                    if (--i < 0) {
                        creditCard = entry.getValue();
                        break;
                    }
                }
            } else {
                creditCard = creditCards.get(creditCardName);
            }

            LOG.info(a.getEmail() + "准备填写并提交支付信息");
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
        LOG.info(a.getEmail() + "的信用卡信息应已设置成功");
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
            LOG.info(a.getEmail() + "的默认一键地址&支付已存在，注册成功");
            return; // 已有默认地址和支付
        }
        LOG.info(a.getEmail() + "将追加的地址&支付设置为默认");
        Element link = currPage.getElementById("myab-make-1click-link-1").select("a").first();
        currPage = getPage(link.absUrl("href"), a, null);
        if (currPage.getElementById("one-click-address-exists") == null) {
            throw new RuntimeException("Failed to set default address");
        }
        LOG.info(a.getEmail() + "注册成功");
    }

    private void siginToAddrView(Account a, String domain) throws Exception {
        currPage = getPage(getLoginUrl1(domain), a, null);
        if (currPage.location().contains("/ap/signin")) {
            processSigninForm(a);
        }
        if (!currPage.location().contains("/a/addresses")) {
            currPage = getPage(getAddrViewUrl(domain), a, null);
        }
    }

    private void siginToOneClickSettings(Account a, String domain) throws Exception {
        currPage = getPage(getLoginUrl2(domain), a, null);
        if (currPage.location().contains("/ap/signin")) {
            processSigninForm(a);
        }
        if (!currPage.location().contains("OneClickSettingsQuery")) {
            currPage = getPage(getAddrViewUrl(domain), a, null);
        }
    }

    private void enableOneClick(Account a, String domain) throws Exception {
        currPage = getPage(getOneClickEditUrl(domain), a, null);
        if (currPage.location().contains("/ap/signin")) {
            processSigninForm(a);
        }
        if (!currPage.location().endsWith(domain)) {
            System.out.println(currPage.location());
            throw new RuntimeException("Not landed on successful page");
        }
    }

    private void processSigninForm(Account a) throws Exception {
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

    private void updateProgressWithCount(String msg) {
        Platform.runLater(() -> {
            rv.setProgress(String.format("%s，成功：%d，失败：%d", msg, ns, nf));
        });
    }

    private void updateProgressWithoutCount(String msg) {
        Platform.runLater(() -> {
            rv.setProgress(msg);
        });
    }

    private void updateAccountProgress(Account a, String msg) {
        Platform.runLater(() -> {
            a.setRegProgress(msg);
            rv.refreshAccountPrgress();
        });
    }

    private void waitForTime(long time) {
        if (time == 0) {
            return;
        }

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
        s = s.replace("{A}", randomString());
        return s.replace("{}", String.format("%04d", (int) (Math.random() * 10000)));
    }

    private String randomString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3 + (int) (Math.random() * 5); i++) {
            char c = (char) ('a' + (int) (Math.random() * 26));
            sb.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }

    private Document getPage(String url, Account a, Map<String, String> data) throws Exception {
        Connection conn = Jsoup.connect(url).proxy(currProxy);
        conn.userAgent(USERAGENT).timeout(TIMEOUT).cookies(a.cookies());
        if (asApp) {
            conn.userAgent(AmznApp.appUA);
            conn.cookie("amzn-app-id", AmznApp.appID);
            conn.cookie("amzn-app-ctxt", AmznApp.appCtxt);
            conn.cookie("mobile-device-info", "scale:2|w:375|h:667");
            conn.header("X-Requested-With", "cn.amazon.mShop.android");
        }
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

    private void setProxyAuthenticator() {
        String userName = RegInput.instance().getProxyUserName();
        String password = RegInput.instance().getProxyPassword();

        if (userName == null) {
            return;
        }

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(userName, password.toCharArray());
            }
        });
    }

    void saveCurrentPage() {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(System.getProperty("user.home") + File.separator + "reg-amzn-fail.html");
            writer.print(currPage.outerHtml());
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
