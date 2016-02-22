package com.ding.luna.ext;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
            RootView rv = new RootView();
            Scene scene = new Scene(rv.getRoot());
            scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
            primaryStage.setScene(scene);
            primaryStage.sizeToScene();
            primaryStage.setTitle("亚马逊账号自动注册工具");
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (System.getProperty("luna") == null) {
            System.exit(1);
        }
        launch(args);
    }
}
