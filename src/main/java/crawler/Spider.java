package crawler;

import javafx.scene.control.TextArea;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.HashMap;
import java.util.Optional;
import java.util.Random;

public class Spider {
    // We'll use a fake USER_AGENT so the web server thinks the robot is a normal web browser.
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.1 (KHTML, like Gecko) Chrome/13.0.782.112 Safari/535.1";

    private HashMap<String, String> cookies = new HashMap<>();
    private TextArea log;
    private double maxPrice;
    private String username;
    private String pwd;
    private boolean isTestrun;

    public Spider(TextArea log) {
        this.log = log;
    }

    private String randomWord() {
        StringBuilder rand = new StringBuilder();
        String characters = "abcdefghijklmnopqrstuvwxyz äüöß";
        Random random = new Random();
        int lengthOfWord = random.nextInt(30) + 1;

        for (int i = 0; i < lengthOfWord; i++)
            rand.append(characters.charAt(random.nextInt(characters.length())));

        if (rand.toString().trim().isEmpty())
            return randomWord();

        return rand.toString();
    }

    /**
     * This method makes a HTTP request, checks the response and then gather all the links on the page.
     */
    public boolean crawl(double maxPrice, String username, String pwd, boolean isTestrun) {
        this.maxPrice = maxPrice;
        this.username = username;
        this.pwd = pwd;
        this.isTestrun = isTestrun;

        try {
            Document searchSite;
            Connection conn = Jsoup.connect("http://www.amazon.de/").userAgent(USER_AGENT).timeout(5000);
            Document htmlDocument = conn.get();
            if (!conn.response().contentType().contains("text/html")) {
                log.appendText("**Failure** Retrieved something other than HTML");
                return false;
            }
            do {
                Elements linksOnPage = htmlDocument.select("input[name=field-keywords]");
                Elements forms = htmlDocument.select("form[name=site-search]");

                Element searchBox = linksOnPage.first();
                String word = randomWord();
                log.appendText("searching ...\n");
                System.out.println("Search word " + word);
                searchBox.val(word);

                FormElement form = (FormElement) forms.first();
                conn = form.submit();
                searchSite = conn.userAgent(USER_AGENT).timeout(5000).post();
            } while (searchSite.toString().contains("keine Produkttreffer"));
            if (conn.response().statusCode() == 200) {
                log.appendText("search successful\n");
                Elements results = searchSite.select("li.s-result-item");
                while (!loopThroughResults(results)) {
                    searchSite = conn.userAgent(USER_AGENT)
                            .timeout(5000)
                            .url("http://www.amazon.de" + searchSite.select("a[id=pagnNextLink]").attr("href"))
                            .get();
                    results = searchSite.select("li.s-result-item");
                    if (results.isEmpty())
                        return crawl(maxPrice, username, pwd, isTestrun);
                }
                return true;
            }
            return crawl(maxPrice, username, pwd, isTestrun);
        } catch (IOException ioe) {
            log.appendText("Timeout, sorry please try again");
            ioe.printStackTrace();
            // We were not successful in our HTTP request
            return false;
        }
    }

    private boolean loopThroughResults(Elements results) {
        for (Element result : results) {
            Element infoField = getInfoField(result);
            String title = getTitle(infoField);
            String priceAsString;

            try {
                priceAsString = getPrice(infoField);
            } catch (Exception e) {
                continue;
            }
            priceAsString = priceAsString.substring(4);

            if (priceAsString.contains("EUR"))
                priceAsString = priceAsString.substring(0, priceAsString.indexOf('-') - 1);
            priceAsString = priceAsString.replace(',', '.');

            double price = 0;
            if (priceAsString.matches("\\d+\\.\\d{1,2}"))
                price = Double.valueOf(priceAsString);
            else { //more than one number
                String[] priceRange = priceAsString.split("\\d+\\.\\d{1,2}");
                price = Double.valueOf(priceAsString.substring(0, priceAsString.length() - priceRange[1].length()));
            }

            if (price <= maxPrice && !containWord(title)) {
                String href = getHref(infoField);
                log.appendText("It would cost " + price + " EUR\n");

                if (buyThing(href, title))
                    return true;
            }
        }
        return false;
    }

    private String getHref(Element infoField) {
        try {
            return infoField.child(3).child(0).child(0).attr("href");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getTitle(Element infoField) {
        try {
            return infoField.child(2).child(0).child(0).attr("title");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private Element getInfoField(Element element) {
        Element infoField;
        try {
            if(element.child(0).child(0).hasClass("a-spacing-top-micro"))
                return element.child(0);
            else
                infoField = element.child(0).child(0).child(0).child(1);
            try { // could be a bestseller
                if(infoField.child(0).child(0).child(0).child(0).text().equals("Bestseller"))
                    infoField.child(0).remove(); // does not work sadly TODO find working solution
            } catch (Exception e){
                return infoField;
            }
        } catch (Exception e) {
            infoField = element.child(0);
        }
        return infoField;
    }

    private String getPrice(Element infoField) throws Exception {
        String price = "";
        try {
            price = infoField.child(1).child(0).child(0).child(0).child(1).text();
            if (!price.startsWith("EUR")) {
                price = infoField.child(2).child(0).child(1).child(0).child(0).text();
                if (price.isEmpty())
                    price = infoField.child(2).child(0).child(1).child(0).child(1).text();
            }
        } catch (Exception e) {
            if (infoField.toString().contains("Derzeit nicht verfügbar") || infoField.toString().contains("Amazon Video"))
                throw new RuntimeException("Product not available!");
            try {
                price = infoField.child(3).child(0).child(0).child(1).text();
            } catch (Exception ex) {
                // sale
                try {
                    price = infoField.child(1).child(0).child(1).child(1).child(0).child(0).child(0).child(0).text();
                } catch (Exception except) {
                    try {
                        price = infoField.child(3).child(0).child(0).child(1).text();
                    } catch (Exception e1) {
                        try {
                            price = infoField.child(3).child(1).child(0).child(1).text();
                        } catch (Exception e2) {
                            except.printStackTrace();
                            e1.printStackTrace();
                            e2.printStackTrace();
                            throw except;
                        }
                    }
                }
            }
        }
        return price;
    }

    private boolean buyThing(String url, String title) {
        Document doc = null;
        try {
            Optional<Document> optional = getDocument(url, false);
            if (!optional.isPresent())
                return false;

            Document htmlDocument = optional.get();
            Elements forms = htmlDocument.select("form[id=addToCart]");
            FormElement form = (FormElement) forms.first();
            Connection conn = form.submit();

            doc = conn.userAgent(USER_AGENT).cookies(cookies).post();
            loadCookies(conn);
            log.appendText("Added to Cart\n");
            forms = doc.select("a[id=hlb-ptc-btn-native]");
            url = forms.first().attr("href");

            doc = getDocument(url, true).orElseThrow(RuntimeException::new);
            forms = doc.select("form[name=signIn]");
            form = (FormElement) forms.first();

            if (form != null)
                signingIn(form);

            forms = doc.select("form[id=spc-form]");
            if (forms.isEmpty()) {
                forms = doc.select("div#address-book-entry-0");
                url = forms.first().child(1).child(0).child(0).attr("href");
                doc = getDocument("http://www.amazon.de" + url, true)
                        .orElseThrow(ReflectiveOperationException::new);
            }
            form = (FormElement) forms.first();

            Elements elements = form.select("td.grand-total-price");
            String price = elements.first().child(0).text();

            if (Double.valueOf((price.substring(4)).replace(',', '.')) <= maxPrice) {
                log.appendText("with shipping: " + price + " EUR\n");
                Elements dates = form.select("span[data-field=promiseday]");
                String promiseDateFrom = dates.first().text();
                String promiseDateTo = dates.get(1).text();
                dates = form.select("span[data-field=promisemonth]");
                promiseDateFrom += ". " + dates.first().text();
                promiseDateTo += ". " + dates.get(1).text();
                dates = form.select("span[data-field=promiseyear]");
                promiseDateFrom += " " + dates.first().text();
                promiseDateTo += " " + dates.get(1).text();

                writeWord(title);
                log.appendText("Arrival: " + promiseDateFrom + " to " + promiseDateTo + "\n");
                if (isTestrun)
                    log.appendText("Would have buyed: " + url);
                else // Buy thing
                    doc = form.submit().userAgent(USER_AGENT).cookies(cookies).post();

                return true;
            }
            log.appendText("price too high with shipping!\nDelete item from Cart\n");

            forms = doc.select("form[id=activeCartViewForm]");
            if (forms.first() == null) {
                Elements deletes = doc.select("a.quantity-delete-button");
                Element del = deletes.first();
                doc = conn.userAgent(USER_AGENT).cookies(cookies).url("https://www.amazon.de" + del.attr("href")).get();
                loadCookies(conn);
                form = (FormElement) doc.select("form[id=changeQuantityFormId]").first();
                doc.select("input[id=quantity.1-1]").val("Delete");
                form.attr("action", "https://www.amazon.de" + doc.select("input[id=quantity.1-1]").first().attr("formaction"));
                doc.select("input[name=continue-button]").remove();
                conn = form.submit();
                doc = conn.userAgent(USER_AGENT).cookies(cookies).post();
                log.appendText("Deleted\n");
            } else {
                FormElement fo = (FormElement) forms.first();
                System.out.println(fo);
                Elements inputs = fo.select("input[value=Löschen]");
                String data = inputs.first().attr("name");
                conn = fo.submit();
                conn.userAgent(USER_AGENT).cookies(cookies).data(data, "Löschen").post();
            }
            return false;
        } catch (IOException ioe) {
            log.appendText("Timeout, sorry I will try it again\n");
            return false;
        } catch (NullPointerException ne) {
            if (doc != null && doc.toString().contains("Es gab ein Problem")) {
                log.appendText("Does not ship to your address");
                return false;
            }
            ne.printStackTrace();
            return false;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().equals("Nope"))
                throw new RuntimeException("hehe");
            if (doc != null && doc.toString().contains("Kindle Edition")) {
                log.appendText("It was a kindle edition\n");
                return false;
            }
            if (doc != null && doc.toString().contains("Song kaufen")) {
                log.appendText("It was an online song\n");
                return false;
            }
            e.printStackTrace();
            return false;
        }
    }

    private void signingIn(FormElement form) {
        Document doc;
        Connection conn;

        log.appendText("I am signing in\n");
        Elements usernames = form.select("input[id=ap_email]");
        Element username = usernames.first();
        username.val(this.username);
        Elements pwds = form.select("input[id=ap_password]");
        Element pwd = pwds.first();
        pwd.val(this.pwd);
        conn = form.submit();
        doc = postDocument(conn).orElseThrow(RuntimeException::new);
        loadCookies(conn);
        doc.select("input[id=ap_password]").first().val(this.pwd);
        doc = postDocument(conn).orElseThrow(RuntimeException::new);
        loadCookies(conn);

        if (doc.toString().contains("Ein Problem ist aufgetreten")) {
            System.out.println("Wrong credentials");
            log.appendText("_________________________________\nWrong credentials!!!!");
            throw new RuntimeException("Nope");
        }
    }

    private void writeWord(String word) {
        try {
            BufferedWriter wr = new BufferedWriter(new FileWriter("buyed.txt", true));
            wr.write(word);
            wr.newLine();
            wr.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean containWord(String word) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("buyed.txt"));
            String line;
            while (!(line = reader.readLine()).equals("")) {
                if (line.equals(word)) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private Optional<Document> getDocument(String url, boolean withCookies) {
        try {
            Connection connection = getConnection(url, withCookies);
            Document document = connection.get();
            loadCookies(connection);
            return Optional.of(document);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private Optional<Document> postDocument(String url, boolean withCookies) {
        try {
            Connection connection = getConnection(url, withCookies);
            Document document = connection.post();
            loadCookies(connection);
            return Optional.of(document);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private Optional<Document> postDocument(Connection connection) {
        try {
            connection.userAgent(USER_AGENT).cookies(cookies).timeout(5000);
            Document document = connection.post();
            loadCookies(connection);
            return Optional.of(document);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private Connection getConnection(String url, boolean withCookies) {
        if (withCookies)
            return Jsoup.connect(url).cookies(cookies).userAgent(USER_AGENT).timeout(5000);

        return Jsoup.connect(url).userAgent(USER_AGENT);
    }

    private void loadCookies(Connection con) {
        cookies.putAll(con.response().cookies());
    }
}
