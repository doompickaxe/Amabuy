package ui;

import crawler.Spider;
import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.prefs.Preferences;

public class amabuy extends Application {

    private Preferences prefs;
    private Thread th;
    private Stage primeStage;
    private BorderPane root;
    @FXML
    private TextField email;
    @FXML
    private PasswordField passwo;
    @FXML
    private TextField moneten;
    @FXML
    private TextArea log;
    @FXML
    private CheckBox check;
    @FXML
    private CheckBox testrun;

    public static void main(String[] args) {
        //launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primeStage = primaryStage;
        primeStage.setTitle("Amabuy");
        initLayout();
    }

    private void initLayout() {
        try {
            // Load root layout from fxml file.
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(amabuy.class.getResource("amabuy.fxml"));
            root = (BorderPane) loader.load();

            // Show the scene containing the root layout.
            Scene scene = new Scene(root);
            primeStage.setScene(scene);
            primeStage.show();

            amabuy a = loader.getController();
            a.setEmailText();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void setEmailText(){
        //Load username if it was saved
        prefs = Preferences.userNodeForPackage(this.getClass());
        String savedEmail = prefs.get("GET_USER_EMAIL", "");
        if(!savedEmail.isEmpty()) {
            email.setText(savedEmail);
            check.setSelected(true);
            log.setText("Welcome back! Do you want another present?");
        } else {
            log.setText("Welcome! Ready for getting a present?");
        }
    }

    @Override
    public void stop(){
        if(th!=null && th.isAlive())
            th.stop();
    }

    @FXML
    private void go(){
        checkAction();
        log.setText("");
        double preis = -1;
        try {
            preis = Double.valueOf(moneten.getText());
        } catch (Exception e) {
            log.setText("Money is not an appropriate number");
            return;
        }
        if(email.getText().isEmpty() || passwo.getText().isEmpty()){
            log.setText("HEY! Not all information is set");
            return;
        }

        log.appendText("Let's begin\n");
        th = new Thread(new Run(preis));
        th.start();
    }

    public void checkAction(){
        prefs = Preferences.userNodeForPackage(this.getClass());
        if(check.isSelected())
            prefs.put("GET_USER_EMAIL", email.getText());
        else
            prefs.remove("GET_USER_EMAIL");
    }

    private class Run implements Runnable {

        double preis;

        public Run(double preis){
            this.preis = preis;
        }

        @Override
        public void run() {
            Spider spider = new Spider(log);
            spider.crawl(preis, email.getText(), passwo.getText(), testrun.isSelected());
            log.appendText("\n\nFinished");
        }
    }
}
