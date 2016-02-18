package com.ding.luna.ext;

import java.awt.Desktop;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

public class RootView {

    TableView<Account> accountTable;

    TableColumn<Account, String> addrNameCol;

    TableColumn<Account, String> creditCardCol;

    TableColumn<Account, String> regProgressCol;

    Account accountToCopy;

    MenuItem copyItem;

    MenuItem pasteItem;

    Button startBtn;

    Button stopBtn;

    Button importBtn;

    Button exportBtn;

    Button showExampleBtn;

    Label progressLabel;

    ImageView captchaView;

    TextField captchaText;

    TextField rangeField;

    ComboBox<String> numPerGroupField;

    ComboBox<String> intervalPerGroupField;

    ComboBox<String> intervalPerAccountField;

    BorderPane layout;

    Executor executor;

    public RootView() {
        Label header = new Label();
        header.setText("请先点击‘示例’阅读，并按照相同格式编写数据文件。‘导入数据’后，点击‘开始注册’。注册完成后点‘导出结果’。");
        header.setAlignment(Pos.CENTER);
        header.setMaxWidth(Double.MAX_VALUE);
        header.getStyleClass().add("dialog-header");
        header.setPadding(new Insets(10.0, 2.0, 10.0, 2.0));

        accountTable = new TableView<>();
        accountTable.setEditable(true);
        accountTable.setPrefSize(700, 450);
        accountTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<Account, Integer> indexCol = new TableColumn<>("序号");
        indexCol.setPrefWidth(50);
        indexCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<Integer>(c.getValue().getIndex()));

        TableColumn<Account, String> userNameCol = new TableColumn<>("账号");
        userNameCol.prefWidthProperty().bind(accountTable.widthProperty().subtract(150).multiply(0.5));
        userNameCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getEmail()));

        addrNameCol = new TableColumn<>("地址");
        addrNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAddress()));
        addrNameCol.prefWidthProperty().bind(accountTable.widthProperty().subtract(150).multiply(0.25));

        creditCardCol = new TableColumn<>("信用卡");
        creditCardCol.prefWidthProperty().bind(accountTable.widthProperty().subtract(150).multiply(0.25));
        creditCardCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCreditCard()));

        regProgressCol = new TableColumn<>("状态");
        regProgressCol.setPrefWidth(100);
        regProgressCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRegProgress()));

        accountTable.getColumns().add(indexCol);
        accountTable.getColumns().add(userNameCol);
        accountTable.getColumns().add(addrNameCol);
        accountTable.getColumns().add(creditCardCol);
        accountTable.getColumns().add(regProgressCol);

        copyItem = new MenuItem("复制地址&信用卡");
        pasteItem = new MenuItem("粘贴地址&信用卡");
        ContextMenu cMenu = new ContextMenu();
        cMenu.getItems().addAll(copyItem, pasteItem);
        accountTable.setContextMenu(cMenu);
        accountTable.setOnContextMenuRequested(e -> {
            List<Account> selectedAccounts = accountTable.getSelectionModel().getSelectedItems();
            if (selectedAccounts.isEmpty()) {
                copyItem.setDisable(true);
                pasteItem.setDisable(true);
            } else {
                copyItem.setDisable(selectedAccounts.size() > 1);
                pasteItem.setDisable(accountToCopy == null);
            }
        } );

        startBtn = new Button("开始注册");
        stopBtn = new Button("停止注册");
        importBtn = new Button("导入数据");
        exportBtn = new Button("导出结果");
        showExampleBtn = new Button("示例");

        HBox controlBar = new HBox();
        controlBar.setSpacing(10.0);
        controlBar.setPadding(new Insets(10.0));
        controlBar.setAlignment(Pos.CENTER);
        controlBar.getChildren().addAll(startBtn, stopBtn, importBtn, exportBtn, showExampleBtn);

        VBox centerBox = new VBox();
        centerBox.setSpacing(5);
        centerBox.setPadding(new Insets(10.0));
        centerBox.getChildren().addAll(accountTable, controlBar);
        VBox.setVgrow(accountTable, Priority.ALWAYS);

        progressLabel = new Label();
        progressLabel.setPrefHeight(30);
        progressLabel.setMaxWidth(Double.MAX_VALUE);
        progressLabel.setAlignment(Pos.CENTER);
        progressLabel.setPadding(new Insets(0.0, 2.5, 0.0, 2.5));
        progressLabel.setStyle("-fx-background-color: #DDDDDD; -fx-font-size: 10 px");

        VBox configureForm = new VBox();
        configureForm.setPrefWidth(200);
        configureForm.setSpacing(20);
        configureForm.setAlignment(Pos.TOP_CENTER);
        configureForm.setPadding(new Insets(10.0, 5.0, 2.0, 5.0));

        rangeField = new TextField();
        rangeField.setPromptText("例如:1-10");
        rangeField.setMaxWidth(Double.MAX_VALUE);
        configureForm.getChildren().add(createField("注册起止序号:", rangeField));

        numPerGroupField = new ComboBox<>();
        numPerGroupField.setMaxWidth(Double.MAX_VALUE);
        configureForm.getChildren().add(createField("分组注册账号:", numPerGroupField));

        intervalPerGroupField = new ComboBox<>();
        intervalPerGroupField.setMaxWidth(Double.MAX_VALUE);
        configureForm.getChildren().add(createField("每组间隔时间:", intervalPerGroupField));

        intervalPerAccountField = new ComboBox<>();
        intervalPerAccountField.setMaxWidth(Double.MAX_VALUE);
        configureForm.getChildren().add(createField("每号间隔时间:", intervalPerAccountField));

        numPerGroupField.getItems().add("不分组");
        for (int j = 1; j <= 20; j++) {
            numPerGroupField.getItems().add(String.format("%d个/组", j));
        }
        numPerGroupField.getSelectionModel().select(5);

        intervalPerGroupField.getItems().add("无间隔");
        for (int j = 1; j <= 6; j++) {
            intervalPerGroupField.getItems().add(String.format("%d分钟", j * 5));
        }
        intervalPerGroupField.getSelectionModel().select(2);

        intervalPerAccountField.getItems().add("无间隔");
        for (int j = 1; j <= 4; j++) {
            intervalPerAccountField.getItems().add(String.format("%.1f分钟", j * 0.5));
        }
        intervalPerAccountField.getSelectionModel().select(1);

        VBox captchaBox = new VBox();
        captchaBox.setSpacing(2.5);
        captchaView = new ImageView();
        captchaView.setPreserveRatio(true);
        captchaView.setFitWidth(200);
        captchaView.setVisible(false);
        captchaText = new TextField();
        captchaText.setPrefWidth(200);
        captchaText.setVisible(false);
        captchaText.setPromptText("请输入验证码并按回车键确认");
        captchaBox.getChildren().addAll(captchaView, captchaText);
        configureForm.getChildren().add(captchaBox);

        VBox rightBox = new VBox();
        rightBox.setSpacing(5.0);
        rightBox.setPadding(new Insets(10.0, 5.0, 5.0, 10.0));
        rightBox.getChildren().addAll(progressLabel, configureForm);
        VBox.setVgrow(configureForm, Priority.ALWAYS);

        layout = new BorderPane();
        layout.setTop(header);
        layout.setRight(rightBox);
        layout.setCenter(centerBox);
        bindEventActionsToRootView();
        Platform.runLater(() -> accountTable.requestFocus());
    }

    public Parent getRoot() {
        return layout;
    }

    public void setProgress(String value) {
        progressLabel.setText(value);
    }

    public void refreshAccountPrgress() {
        regProgressCol.setVisible(false);
        regProgressCol.setVisible(true);
    }

    public void showCaptcha(String url) {
        if (url == null) {
            captchaView.setVisible(false);
            captchaText.setVisible(false);
        } else {
            captchaView.setImage(new Image(url, true));
            captchaText.clear();
            captchaView.setVisible(true);
            captchaText.setVisible(true);
        }
    }

    private VBox createField(String name, Node field) {
        VBox box = new VBox();
        box.setSpacing(5.0);
        Label label = new Label(name);
        label.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().addAll(label, field);
        return box;
    }

    private void bindEventActionsToRootView() {
        copyItem.setOnAction(ae -> {
            accountToCopy = accountTable.getSelectionModel().getSelectedItem();
        } );

        pasteItem.setOnAction(ae -> {
            List<Account> selected = accountTable.getSelectionModel().getSelectedItems();
            for (Account account : selected) {
                account.setAddress(accountToCopy.getAddress());
                account.setCreditCard(accountToCopy.getCreditCard());
            }
            addrNameCol.setVisible(false);
            addrNameCol.setVisible(true);
            creditCardCol.setVisible(false);
            creditCardCol.setVisible(true);
        } );

        showExampleBtn.setOnAction(ae -> {
            Task<File> task = new Task<File>() {

                @Override
                protected File call() throws Exception {
                    File file = File.createTempFile("amzn", ".txt");
                    printExampleToTempFile(file);
                    return file;
                }

            };
            task.setOnSucceeded(e -> {
                File f = task.getValue();
                Platform.runLater(() -> openInDefaultEditor(f));
            } );
            task.setOnFailed(e -> {
                Throwable t = e.getSource().getException();
                if (t != null) {
                    t.printStackTrace();
                }
            } );
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        } );

        importBtn.setOnAction(ae -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("从文件导入");
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
            fileChooser.getExtensionFilters().add(new ExtensionFilter("全部文件 (*.*)", "*.*"));
            File f = fileChooser.showOpenDialog(layout.getScene().getWindow());
            if (f != null) {
                try {
                    RegInput.instance().clear();
                    RegInput.instance().read(f);

                    List<String> options = new ArrayList<>();
                    options.add("随机选择");
                    options.addAll(RegInput.instance().getAddresses().keySet());
                    addrNameCol.setCellFactory(
                            ComboBoxTableCell.<Account, String> forTableColumn(options.toArray(new String[0])));
                    addrNameCol.setOnEditCommit(e -> {
                        e.getTableView().getItems().get(e.getTablePosition().getRow()).setAddress(e.getNewValue());
                    } );

                    options = new ArrayList<>();
                    options.add("随机选择");
                    options.addAll(RegInput.instance().getCreditCards().keySet());
                    creditCardCol.setCellFactory(
                            ComboBoxTableCell.<Account, String> forTableColumn(options.toArray(new String[0])));
                    creditCardCol.setOnEditCommit(e -> {
                        e.getTableView().getItems().get(e.getTablePosition().getRow()).setCreditCard(e.getNewValue());
                    } );

                    accountTable.setItems(FXCollections.observableArrayList(RegInput.instance().getAccounts()));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } );

        exportBtn.setOnAction(ae -> {

        } );

        startBtn.setOnAction(ae -> {
            saveRegisterConfiguration();
            setConfigureFormDisable(true);
            Task<Void> task = new Task<Void>() {

                @Override
                protected Void call() throws Exception {
                    executor = new Executor(RootView.this);
                    executor.run();
                    return null;
                }

            };
            task.setOnSucceeded(e -> {
                Platform.runLater(() -> setConfigureFormDisable(false));
            } );
            task.setOnFailed(e -> {
                Platform.runLater(() -> setConfigureFormDisable(false));
            } );
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        } );

        stopBtn.setOnAction(ae -> {
            executor.stop();
        } );
    }

    private void setConfigureFormDisable(boolean b) {
        rangeField.setDisable(b);
        numPerGroupField.setDisable(b);
        intervalPerGroupField.setDisable(b);
        intervalPerAccountField.setDisable(b);
    }

    private void saveRegisterConfiguration() {
        RegConf.instance().setRange(rangeField.getText().trim());

        int i = numPerGroupField.getSelectionModel().getSelectedIndex();
        RegConf.instance().setNumPerGroup(i);

        i = intervalPerGroupField.getSelectionModel().getSelectedIndex();
        RegConf.instance().setIntervalPerGroup(i * 300000);

        i = intervalPerAccountField.getSelectionModel().getSelectedIndex();
        RegConf.instance().setIntervalPerAccount(i * 30000);
    }

    private void openInDefaultEditor(File file) {
        SwingUtilities.invokeLater(() -> {
            try {
                Desktop.getDesktop().edit(file);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        } );
    }

    private void printExampleToTempFile(File file) {
        PrintWriter output = null;
        try {
            output = new PrintWriter(file);
            output.println(Example.get());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (output != null) {
                output.flush();
                output.close();
            }
        }
    }

}
