package com.ding.luna.ext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RegInput {

    private static RegInput instance = new RegInput();

    private String country;

    private int proxyIndex;

    private int naPerProxy;

    private String proxyUser;

    private String proxyPass;

    private List<Proxy> proxies;

    private List<Account> accounts;

    private List<String> directives;

    private Map<String, List<String>> addresses;

    private Map<String, List<String>> creditCards;

    public static RegInput instance() {
        return instance;
    }

    private RegInput() {
        naPerProxy = 10;
        addresses = new LinkedHashMap<>();
        creditCards = new LinkedHashMap<>();
        accounts = new ArrayList<>();
        proxies = new ArrayList<>();
    }

    public String getDomain() {
        switch (country) {
        case "日本":
            return "www.amazon.co.jp";
        case "德国":
            return "www.amazon.de";
        case "美国":
            return "www.amazon.com";
        case "英国":
            return "www.amazon.co.uk";
        case "法国":
            return "www.amazon.fr";
        case "加拿大":
            return "www.amazon.ca";
        case "意大利":
            return "www.amazon.it";
        case "西班牙":
            return "www.amazon.es";
        default:
            return null;
        }
    }

    public boolean hasProxy() {
        return !proxies.isEmpty();
    }

    public Proxy getProxy() {
        if (proxies.isEmpty()) {
            return null;
        }
        int i = proxyIndex++;
        if (proxyIndex >= proxies.size()) {
            proxyIndex = 0;
        }
        return proxies.get(i);
    }

    public String getProxyUserName() {
        return proxyUser;
    }

    public String getProxyPassword() {
        return proxyPass;
    }

    public int getNumAccountsPerProxy() {
        return naPerProxy;
    }

    public List<String> getDirectives() {
        if (directives == null) {
            return new ArrayList<>();
        }
        return directives;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public Map<String, List<String>> getAddresses() {
        return addresses;
    }

    public Map<String, List<String>> getCreditCards() {
        return creditCards;
    }

    public void clear() {
        country = null;
        directives = null;
        addresses.clear();
        creditCards.clear();
        accounts.clear();
    }

    public void read(File f) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(new UnicodeInputStream(new FileInputStream(f)), "UTF-8"));
        try {
            String line = null;
            while ((line = in.readLine()) != null) {
                if ((line = line.trim()).isEmpty()) {
                    continue;
                }

                if (directives == null && line.startsWith("@")) {
                    line = line.substring(1).replace("；", ";");
                    directives = Arrays.asList(line.split(";"));
                    continue;
                }

                if (country == null && !line.startsWith("#")) {
                    country = line;
                    continue;
                }

                if (line.contains("地址区开始")) {
                    readAddresses(in);
                } else if (line.contains("信用卡区开始")) {
                    readCreditCards(in);
                } else if (line.contains("账号区开始")) {
                    readAccounts(in);
                } else if (line.contains("代理区开始")) {
                    readProxies(in);
                }
            }
        } finally {
            in.close();
        }
    }

    private void readAddresses(BufferedReader in) throws IOException {
        int i = 1;
        while (true) {
            List<String> lines = readNextBlock(in);
            if (lines.isEmpty() || lines.contains("**地址区结束**")) {
                break;
            }
            String key = String.format("地址-%d", i++);
            addresses.put(key, lines);
        }
    }

    private void readCreditCards(BufferedReader in) throws IOException {
        int i = 1;
        while (true) {
            List<String> lines = readNextBlock(in);
            if (lines.isEmpty() || lines.contains("**信用卡区结束**")) {
                break;
            }
            String key = String.format("信用卡-%d", i++);
            creditCards.put(key, lines);
        }
    }

    private void readAccounts(BufferedReader in) throws IOException {
        int i = 1;
        while (true) {
            String line = in.readLine();
            if (line == null || line.contains("**账号区结束**")) {
                break;
            }
            if (line.isEmpty()) {
                continue;
            }
            String[] items = line.trim().split("\\s+", 3);
            Account account = new Account(i++, items[0], items[1]);
            account.setRealName(items.length > 2 ? items[2] : randomName());
            accounts.add(account);
        }
    }

    private void readProxies(BufferedReader in) throws IOException {
        while (true) {
            String line = in.readLine();
            if (line == null || line.contains("**代理区结束**")) {
                break;
            }
            if (line.isEmpty()) {
                continue;
            }

            line = line.trim();
            if (line.startsWith("Accounts/Proxy")) {
                naPerProxy = Integer.parseInt(line.split("=")[1]);
                continue;
            }

            String[] items = line.split(":");
            String host = items[0];
            int port = Integer.parseInt(items[1]);
            String userName = items.length > 2 ? items[2] : null;
            String password = items.length > 3 ? items[3] : null;
            String type = items.length > 4 ? items[4] : "HTTP";

            if (proxyUser == null) {
                proxyUser = userName;
            }
            if (proxyPass == null) {
                proxyPass = password;
            }
            InetSocketAddress address = new InetSocketAddress(host, port);
            proxies.add(new Proxy(Proxy.Type.valueOf(type), address));
        }
    }

    private List<String> readNextBlock(BufferedReader in) throws IOException {
        List<String> lines = new ArrayList<>();
        String line = null;
        while ((line = in.readLine()).isEmpty()) {
        }
        lines.add(line.trim());
        if (!line.endsWith("区结束**")) {
            while (!(line = in.readLine()).isEmpty()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private String randomName() {
        char[] chars = new char[] { 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q',
                'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' };
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < random.nextInt(5) + 3; i++) {
            char c = chars[random.nextInt(chars.length)];
            sb.append(i == 0 ? c : Character.toLowerCase(c));
        }
        sb.append(" ");
        for (int i = 0; i < random.nextInt(5) + 3; i++) {
            char c = chars[random.nextInt(chars.length)];
            sb.append(i == 0 ? c : Character.toLowerCase(c));
        }
        return sb.toString();
    }

}
