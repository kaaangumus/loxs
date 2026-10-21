package com.loxs.gui;

import com.loxs.AppInfo;
import com.loxs.config.AppSettings;
import com.loxs.core.HttpProbe;
import com.loxs.core.Payloads;
import com.loxs.core.ScanListener;
import com.loxs.core.ScannerEngine;
import com.loxs.model.Finding;
import com.loxs.model.ScanRequest;
import com.loxs.model.ScanType;
import com.loxs.report.ReportWriter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LoxsFxView {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Stage stage;
    private final AppSettings settings;
    private final BorderPane root = new BorderPane();
    private final Map<ScanType, ToggleButton> scannerButtons = new EnumMap<>(ScanType.class);
    private final ToggleGroup scannerGroup = new ToggleGroup();

    private final TextArea targetsArea = new TextArea();
    private final TextField payloadField = new TextField();
    private final TextField cookieField = new TextField();
    private final TextField criteriaField = new TextField("root:x:0:0:");
    private final ComboBox<String> profileBox = new ComboBox<>(
            FXCollections.observableArrayList("Hızlı", "Standart", "Derin"));
    private final Spinner<Integer> threadsSpinner = spinner(5, 1, 20, 1);
    private final Spinner<Integer> timeoutSpinner = spinner(20, 1, 120, 1);
    private final Spinner<Integer> delaySpinner = spinner(4, 1, 60, 1);
    private final Spinner<Integer> marginSpinner = spinner(3, 0, 60, 1);
    private final CheckBox verifyTlsBox = new CheckBox("TLS doğrulaması");
    private final CheckBox showCleanBox = new CheckBox("Temiz sonuçları göster");
    private VBox payloadSetting;
    private VBox criteriaSetting;
    private VBox sqlSetting;
    private Label settingsNote;
    private final Button browseButton = new Button("Gözat");
    private final Button loadUrlsButton = new Button("Dosyadan");
    private final Button startButton = new Button("Taramayı Başlat");
    private final Button stopButton = new Button("Durdur");
    private final Button testButton = new Button("Bağlantı Testi");
    private final Button reportButton = new Button("Rapor");
    private final Button resetButton = new Button("Sıfırla");

    private final ObservableList<Finding> tableFindings = FXCollections.observableArrayList();
    private final List<Finding> allFindings = new ArrayList<>();
    private final TableView<Finding> table = new TableView<>(tableFindings);
    private final TextArea detailArea = new TextArea();
    private final TextArea logArea = new TextArea();
    private final TabPane tabs = new TabPane();
    private final StackPane resultStack = new StackPane();
    private final VBox emptyState = new VBox();
    private final Label summaryVulnerable = new Label("0");
    private final Label summaryClean = new Label("0");
    private final Label summaryError = new Label("0");
    private final Label summaryTime = new Label("—");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label stageLabel = new Label("Hazır");
    private final Label countsLabel = new Label("0 bulgu • 0 temiz • 0 hata");

    private ScanType activeType = ScanType.SQLI;
    private Thread scanThread;
    private ScannerEngine currentEngine;
    private volatile boolean stopRequested;
    private boolean authorizationAccepted;
    private long scanStartedAt;
    private long lastProgressAt;
    private int vulnerableCount;
    private int cleanCount;
    private int errorCount;

    public LoxsFxView(Stage stage) {
        this.stage = stage;
        this.settings = AppSettings.load();
        root.getStyleClass().add("loxs-root");
        root.setStyle("-fx-background-color: #0a0f1c;");
        root.setTop(buildTop());
        root.setLeft(buildSidebar());
        root.setCenter(buildCenter());
        root.setRight(buildSettingsPanel());
        root.setBottom(buildStatus());
        installEvents();
        applySettings();
        selectType(ScanType.SQLI);
        stage.setOnCloseRequest(event -> {
            if (scanThread != null && scanThread.isAlive()) {
                boolean quit = Dialogs.confirm(stage, "Tarama sürüyor",
                        "Tarama devam ediyor. Durdurup çıkmak istiyor musunuz?");
                if (!quit) {
                    event.consume();
                    return;
                }
                stopScan();
            }
            saveSettings();
        });
    }

    public Parent root() {
        return root;
    }

    private Node buildTop() {
        Label logo = new Label("LOXS");
        logo.getStyleClass().add("brand");
        VBox brand = new VBox(logo);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label version = new Label("v" + AppInfo.VERSION);
        version.getStyleClass().add("version-badge");
        HBox header = new HBox(12, brand, spacer, version);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("top-bar");

        Label targetLabel = new Label("Hedef URL'ler");
        targetLabel.getStyleClass().add("muted");
        targetsArea.setPrefRowCount(3);
        targetsArea.getStyleClass().add("target-area");
        ScrollPane targetScroll = new ScrollPane(targetsArea);
        targetScroll.setFitToWidth(true);
        targetScroll.setPrefHeight(76);
        targetScroll.getStyleClass().add("target-scroll");

        loadUrlsButton.setText("Dosya Seç");
        loadUrlsButton.setGraphic(fileIcon());
        loadUrlsButton.setContentDisplay(ContentDisplay.TOP);
        loadUrlsButton.setGraphicTextGap(7);
        loadUrlsButton.getStyleClass().add("file-button");
        loadUrlsButton.setMaxHeight(Double.MAX_VALUE);
        loadUrlsButton.setPrefWidth(108);
        loadUrlsButton.setMinWidth(108);
        VBox targetButtons = new VBox(loadUrlsButton);
        targetButtons.setFillWidth(true);
        VBox.setVgrow(loadUrlsButton, Priority.ALWAYS);
        HBox targetRow = new HBox(8, targetScroll, targetButtons);
        HBox.setHgrow(targetScroll, Priority.ALWAYS);

        startButton.getStyleClass().add("primary-button");
        stopButton.getStyleClass().add("danger-button");
        stopButton.setDisable(true);
        HBox actions = new HBox(7, startButton, stopButton, testButton, reportButton,
                resetButton, new Region());
        HBox.setHgrow(actions.getChildren().get(5), Priority.ALWAYS);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox targetCard = new VBox(7, targetLabel, targetRow, actions);
        targetCard.getStyleClass().add("target-card");
        return new VBox(header, targetCard);
    }

    private Node buildSidebar() {
        Label section = new Label("TARAYICILAR");
        section.getStyleClass().add("section-title");
        VBox sidebar = new VBox(6, section);
        for (ScanType type : ScanType.values()) {
            ToggleButton button = new ToggleButton(type.label());
            button.setToggleGroup(scannerGroup);
            button.getStyleClass().add("scanner-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setOnAction(event -> selectType(type));
            scannerButtons.put(type, button);
            sidebar.getChildren().add(button);
        }
        sidebar.getChildren().addAll(new Separator(), new Region());
        VBox.setVgrow(sidebar.getChildren().get(sidebar.getChildren().size() - 1), Priority.ALWAYS);
        Label legal = new Label("Yalnızca izinli\nhedeflerde kullanın.");
        legal.getStyleClass().add("legal");
        sidebar.getChildren().add(legal);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(190);
        return sidebar;
    }

    private Node buildCenter() {
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("Sonuçlar", buildResults()));
        tabs.getTabs().add(new Tab("Günlük", buildLog()));
        return tabs;
    }

    private Node buildResults() {
        HBox summary = new HBox(8,
                summaryCard("BULGULAR", summaryVulnerable, "summary-danger"),
                summaryCard("TEMİZ", summaryClean, "summary-muted"),
                summaryCard("HATA", summaryError, "summary-error"),
                summaryCard("SÜRE", summaryTime, "summary-accent"));
        for (Node node : summary.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }

        table.getColumns().addAll(
                column("Durum", finding -> finding.vulnerable() ? "VULNERABLE" : "Temiz", 100),
                column("Tarayıcı", finding -> finding.type().label(), 100),
                column("URL", Finding::url, 380),
                column("Parametre", Finding::parameter, 100),
                column("Payload", Finding::payload, 220),
                column("Süre", finding -> finding.elapsedMs() + " ms", 80),
                column("Detay", Finding::details, 280));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Henüz bulgu yok."));
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showDetail(newValue));
        detailArea.setEditable(false);
        detailArea.setWrapText(true);
        detailArea.setPromptText("Bir bulgu seçin.");
        SplitPane split = new SplitPane(table, detailArea);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.72);
        VBox.setVgrow(split, Priority.ALWAYS);

        emptyState.getStyleClass().add("empty-state");
        emptyState.setAlignment(Pos.CENTER);
        Label emptyLogo = new Label("LOXS");
        emptyLogo.getStyleClass().add("empty-logo");
        Label emptyTitle = new Label("Taramaya hazır");
        emptyTitle.getStyleClass().add("empty-title");
        Label emptyText = new Label("Hedef URL girin ve taramayı başlatın.");
        emptyText.getStyleClass().add("muted");
        emptyState.getChildren().addAll(emptyLogo, emptyTitle, emptyText);

        resultStack.getChildren().addAll(emptyState, split);
        split.setVisible(false);
        VBox box = new VBox(8, summary, resultStack);
        VBox.setVgrow(resultStack, Priority.ALWAYS);
        box.getStyleClass().add("view");
        return box;
    }

    private HBox summaryCard(String labelText, Label value, String styleClass) {
        Label label = new Label(labelText);
        label.getStyleClass().add("summary-label");
        value.getStyleClass().addAll("summary-value", styleClass);
        VBox content = new VBox(2, label, value);
        content.setAlignment(Pos.CENTER_LEFT);
        HBox card = new HBox(content);
        card.getStyleClass().add("summary-card");
        return card;
    }

    private Node buildLog() {
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPromptText("Günlük henüz boş.");
        VBox box = new VBox(logArea);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        box.getStyleClass().add("view");
        return box;
    }

    private Node buildSettingsPanel() {
        Label section = new Label("TARAMA AYARLARI");
        section.getStyleClass().add("section-title");

        profileBox.setMaxWidth(Double.MAX_VALUE);
        payloadField.setMaxWidth(Double.MAX_VALUE);
        payloadField.setPromptText("Varsayılan payload seti");
        cookieField.setPromptText("Cookie (diske yazılmaz)");
        HBox payloadRow = new HBox(5, payloadField, browseButton);
        HBox.setHgrow(payloadField, Priority.ALWAYS);

        payloadSetting = settingBox("Payload dosyası", payloadRow);
        criteriaSetting = settingBox("LFI kriteri", criteriaField);
        sqlSetting = new VBox(9,
                settingBox("SQLi eşiği (sn)", delaySpinner),
                settingBox("Taban farkı (sn)", marginSpinner));

        verifyTlsBox.getStyleClass().add("check");
        showCleanBox.getStyleClass().add("check");

        settingsNote = new Label("Çerez diske yazılmaz.");
        settingsNote.getStyleClass().add("hint");
        settingsNote.setWrapText(true);

        VBox content = new VBox(9,
                section,
                settingBox("Profil", profileBox),
                settingBox("Thread", threadsSpinner),
                settingBox("Timeout (sn)", timeoutSpinner),
                sqlSetting,
                payloadSetting,
                criteriaSetting,
                settingBox("Çerez", cookieField),
                verifyTlsBox,
                showCleanBox,
                settingsNote);
        content.getStyleClass().add("settings-panel");
        content.setPrefWidth(300);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");
        scroll.setPrefWidth(300);
        scroll.setMinWidth(280);
        scroll.setMaxWidth(340);
        return scroll;
    }

    private VBox settingBox(String text, Node field) {
        VBox box = new VBox(3, new Label(text), field);
        box.getStyleClass().add("setting");
        return box;
    }

    private Node fileIcon() {
        SVGPath folder = new SVGPath();
        folder.setContent("M2 5 a2 2 0 0 1 2 -2 h4 l2 2 h8 a2 2 0 0 1 2 2 v9 a2 2 0 0 1 -2 2 h-14 a2 2 0 0 1 -2 -2 z");
        folder.setFill(Color.web("#38bdf8"));
        folder.setScaleX(1.45);
        folder.setScaleY(1.45);
        return folder;
    }

    private Node buildStatus() {
        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        stageLabel.getStyleClass().add("muted");
        countsLabel.getStyleClass().add("muted");
        HBox bar = new HBox(12, stageLabel, progressBar, countsLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void installEvents() {
        startButton.setOnAction(event -> startScan());
        stopButton.setOnAction(event -> stopScan());
        testButton.setOnAction(event -> testConnection());
        reportButton.setOnAction(event -> showReportMenu());
        resetButton.setOnAction(event -> resetForm());
        browseButton.setOnAction(event -> browsePayload());
        loadUrlsButton.setOnAction(event -> loadUrls());
        profileBox.valueProperty().addListener((observable, oldValue, newValue) -> applyProfile());
        showCleanBox.selectedProperty().addListener((observable, oldValue, newValue) -> rebuildTable());
    }

    private void selectType(ScanType type) {
        activeType = type;
        ToggleButton button = scannerButtons.get(type);
        if (button != null) {
            button.setSelected(true);
        }
        boolean payloadRequired = type.payloadRequired();
        boolean sql = type == ScanType.SQLI;
        payloadField.setDisable(!payloadRequired);
        browseButton.setDisable(!payloadRequired);
        payloadSetting.setVisible(payloadRequired);
        payloadSetting.setManaged(payloadRequired);
        sqlSetting.setVisible(sql);
        sqlSetting.setManaged(sql);
        criteriaSetting.setVisible(type.criteriaRelevant());
        criteriaSetting.setManaged(type.criteriaRelevant());
        if (settingsNote != null) {
            if (sql) {
                settingsNote.setText("SQLi eşiği ve taban farkı yalnızca SQLi tarayıcısında etkilidir. "
                        + "Çerez diske yazılmaz.");
            } else if (type == ScanType.LFI) {
                settingsNote.setText("LFI kriteri, yanıtta aranacak imzayı belirler. Çerez diske yazılmaz.");
            } else {
                settingsNote.setText("Çerez diske yazılmaz.");
            }
        }
        targetsArea.setPromptText("Her satıra bir hedef URL yazın: " + targetExample(type));
        payloadField.setText("");
        stageLabel.setText(type.label() + " • " + type.description());
    }

    private static String targetExample(ScanType type) {
        return switch (type) {
            case SQLI -> "https://site.com/urun.php?id=1";
            case LFI -> "https://site.com/sayfa.php?file=index";
            case XSS -> "https://site.com/arama?q=test";
            case CRLF -> "https://site.com/yonlendir?next=/anasayfa";
            case OPEN_REDIRECT -> "https://site.com/git?url=https://hedef.com";
        };
    }

    private void startScan() {
        if (scanThread != null && scanThread.isAlive()) {
            return;
        }
        if (!confirmAuthorization()) {
            return;
        }
        List<String> targets = targetsArea.getText().lines().map(String::strip)
                .filter(line -> !line.isEmpty()).toList();
        if (targets.isEmpty()) {
            showError("En az bir hedef URL girin.");
            return;
        }
        List<String> invalidTargets = targets.stream().filter(target -> !isValidTarget(target)).toList();
        if (!invalidTargets.isEmpty()) {
            showError("Geçersiz hedef: " + String.join(", ", invalidTargets)
                    + "\n\nIP, localhost veya en az iki karakterli uzantıya sahip bir domain girin.");
            return;
        }
        List<String> payloads;
        try {
            if (activeType == ScanType.CRLF) {
                payloads = List.of();
            } else if (payloadField.getText() != null && !payloadField.getText().isBlank()) {
                payloads = Payloads.loadFile(Path.of(payloadField.getText().strip()));
            } else {
                payloads = Payloads.require(activeType);
            }
        } catch (Exception exception) {
            showError("Payload yüklenemedi: " + exception.getMessage());
            return;
        }
        List<String> criteria = activeType == ScanType.LFI
                ? criteriaField.getText().lines().flatMap(line -> java.util.Arrays.stream(line.split(",")))
                .map(String::strip).filter(value -> !value.isEmpty()).toList()
                : List.of();
        if (activeType == ScanType.LFI && criteria.isEmpty()) {
            criteria = List.of("root:x:0:0:");
        }
        ScanRequest request;
        try {
            request = new ScanRequest(activeType, targets, payloads, cookieField.getText(),
                    threadsSpinner.getValue(), Duration.ofSeconds(timeoutSpinner.getValue()),
                    criteria, delaySpinner.getValue(), marginSpinner.getValue(), verifyTlsBox.isSelected());
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
            return;
        }

        saveSettings();
        clearResults();
        scanStartedAt = System.currentTimeMillis();
        showScanning();
        startButton.setDisable(true);
        stopButton.setDisable(false);
        stageLabel.setText("Taranıyor…");
        appendLog("—".repeat(60));
        appendLog("Tarama başladı: " + activeType.label() + " • " + targets.size() + " hedef");
        stopRequested = false;

        scanThread = new Thread(() -> {
            try {
                currentEngine = new ScannerEngine();
                currentEngine.scan(request, new UiListener());
            } catch (Exception exception) {
                String message = exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage();
                Platform.runLater(() -> appendLog("Tarama hatası: " + message));
            } finally {
                Platform.runLater(this::scanFinished);
            }
        }, "loxs-fx-scan");
        scanThread.setDaemon(true);
        scanThread.start();
    }

    private void stopScan() {
        stopRequested = true;
        if (currentEngine != null) {
            currentEngine.cancel();
        }
        stageLabel.setText("Durduruluyor…");
        appendLog("Durdurma isteği gönderildi.");
    }

    private void scanFinished() {
        startButton.setDisable(false);
        stopButton.setDisable(true);
        stageLabel.setText(stopRequested ? "Durduruldu" : "Tamamlandı");
        progressBar.setProgress(1);
        long seconds = Math.max(0, (System.currentTimeMillis() - scanStartedAt) / 1000);
        summaryTime.setText(seconds + " sn");
        if (allFindings.isEmpty()) {
            emptyState.setVisible(true);
            resultStack.getChildren().get(1).setVisible(false);
        }
        updateCounts();
    }

    private void showScanning() {
        emptyState.setVisible(true);
        resultStack.getChildren().get(1).setVisible(false);
        ((Label) emptyState.getChildren().get(1)).setText("Taranıyor…");
        ((Label) emptyState.getChildren().get(2)).setText("Sonuçlar geldikçe burada görünecek.");
    }

    private void clearResults() {
        allFindings.clear();
        tableFindings.clear();
        vulnerableCount = 0;
        cleanCount = 0;
        errorCount = 0;
        logArea.clear();
        detailArea.clear();
        summaryVulnerable.setText("0");
        summaryClean.setText("0");
        summaryError.setText("0");
        summaryTime.setText("—");
        progressBar.setProgress(0);
        emptyState.setVisible(true);
        resultStack.getChildren().get(1).setVisible(false);
        ((Label) emptyState.getChildren().get(1)).setText("Taramaya hazır");
        ((Label) emptyState.getChildren().get(2)).setText("Hedef URL girin ve taramayı başlatın.");
        updateCounts();
    }

    private void addFinding(Finding finding) {
        allFindings.add(finding);
        if (finding.vulnerable()) {
            vulnerableCount++;
            tableFindings.add(finding);
            showTable();
        } else if (finding.details().startsWith("Hata:")) {
            errorCount++;
            tableFindings.add(finding);
            showTable();
        } else {
            cleanCount++;
            if (showCleanBox.isSelected()) {
                tableFindings.add(finding);
                showTable();
            }
        }
        if (finding.vulnerable()) {
            appendLog("[BULGU] " + finding.url() + " • " + finding.parameter());
        }
        updateCounts();
    }

    private void rebuildTable() {
        tableFindings.clear();
        for (Finding finding : allFindings) {
            if (finding.vulnerable() || finding.details().startsWith("Hata:")) {
                tableFindings.add(finding);
            } else if (showCleanBox.isSelected()) {
                tableFindings.add(finding);
            }
        }
        if (!tableFindings.isEmpty()) {
            showTable();
        }
    }

    private void showTable() {
        emptyState.setVisible(false);
        resultStack.getChildren().get(1).setVisible(true);
    }

    private void showDetail(Finding finding) {
        if (finding == null) {
            detailArea.clear();
            return;
        }
        detailArea.setText("Durum     : " + (finding.vulnerable() ? "VULNERABLE" : "Temiz")
                + "\nTarayıcı  : " + finding.type().label()
                + "\nURL       : " + finding.url()
                + "\nParametre : " + finding.parameter()
                + "\nPayload   : " + finding.payload()
                + "\nHTTP      : " + finding.statusCode() + " • " + finding.elapsedMs() + " ms"
                + "\n\nDetay\n" + finding.details());
        detailArea.positionCaret(0);
    }

    private void updateCounts() {
        summaryVulnerable.setText(String.valueOf(vulnerableCount));
        summaryClean.setText(String.valueOf(cleanCount));
        summaryError.setText(String.valueOf(errorCount));
        countsLabel.setText(vulnerableCount + " bulgu • " + cleanCount + " temiz • " + errorCount + " hata");
    }

    private boolean confirmAuthorization() {
        if (authorizationAccepted) {
            return true;
        }
        if (Dialogs.authorize(stage)) {
            authorizationAccepted = true;
            return true;
        }
        showError("Yetki onayı olmadan tarama başlatılamaz.");
        return false;
    }

    private void testConnection() {
        String first = targetsArea.getText().lines().map(String::strip)
                .filter(line -> !line.isEmpty()).findFirst().orElse("");
        if (first.isBlank()) {
            showError("Önce bir hedef URL girin.");
            return;
        }
        if (!isValidTarget(first)) {
            showError("Geçersiz hedef: " + first
                    + "\n\nIP, localhost veya en az iki karakterli uzantıya sahip bir domain girin.");
            return;
        }
        String target = com.loxs.core.UrlInjector.normalize(first);
        testButton.setDisable(true);
        stageLabel.setText("Bağlantı test ediliyor…");
        Thread thread = new Thread(() -> {
            HttpProbe probe = new HttpProbe(Duration.ofSeconds(10), cookieField.getText(),
                    verifyTlsBox.isSelected());
            HttpProbe.Response response = probe.get(target);
            Platform.runLater(() -> {
                testButton.setDisable(false);
                if (response.timedOut()) {
                    showError("Hedefe ulaşılamadı: zaman aşımı.");
                } else if (response.error() != null) {
                    showError("Hedefe ulaşılamadı: " + response.error());
                } else {
                    stageLabel.setText("Bağlantı başarılı • HTTP " + response.statusCode());
                    showInfo("Hedef yanıt verdi.\n\nHTTP " + response.statusCode()
                            + "\nSüre: " + response.elapsedMs() + " ms");
                }
            });
        }, "loxs-fx-test");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean isValidTarget(String value) {
        try {
            String normalized = com.loxs.core.UrlInjector.normalize(value);
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank() || host.contains(" ")) {
                return false;
            }
            if (host.equalsIgnoreCase("localhost")
                    || host.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")
                    || host.matches("\\[?[0-9a-fA-F:]+]?")) {
                return true;
            }
            if (!host.contains(".") || host.startsWith(".") || host.endsWith(".")) {
                return false;
            }
            String tld = host.substring(host.lastIndexOf('.') + 1);
            return tld.length() >= 2 && tld.matches("[a-zA-Z]{2,}");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void loadUrls() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Hedef URL dosyası seç");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Metin dosyası", "*.txt"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            try {
                targetsArea.setText(java.nio.file.Files.readString(file.toPath(),
                        java.nio.charset.StandardCharsets.UTF_8));
                appendLog("URL dosyası yüklendi: " + file.getName());
            } catch (Exception exception) {
                showError("URL dosyası okunamadı: " + exception.getMessage());
            }
        }
    }

    private void browsePayload() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Payload dosyası seç");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Metin dosyası", "*.txt"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            payloadField.setText(file.getAbsolutePath());
        }
    }

    private void resetForm() {
        targetsArea.clear();
        cookieField.clear();
        payloadField.clear();
        criteriaField.setText("root:x:0:0:");
        threadsSpinner.getValueFactory().setValue(5);
        timeoutSpinner.getValueFactory().setValue(20);
        delaySpinner.getValueFactory().setValue(4);
        marginSpinner.getValueFactory().setValue(3);
        verifyTlsBox.setSelected(true);
        showCleanBox.setSelected(false);
        applyProfile();
        appendLog("Form varsayılan değerlere sıfırlandı.");
    }

    private void applyProfile() {
        switch (profileBox.getValue() == null ? "Standart" : profileBox.getValue()) {
            case "Hızlı" -> {
                threadsSpinner.getValueFactory().setValue(5);
                timeoutSpinner.getValueFactory().setValue(10);
                delaySpinner.getValueFactory().setValue(4);
                marginSpinner.getValueFactory().setValue(2);
            }
            case "Derin" -> {
                threadsSpinner.getValueFactory().setValue(15);
                timeoutSpinner.getValueFactory().setValue(30);
                delaySpinner.getValueFactory().setValue(6);
                marginSpinner.getValueFactory().setValue(3);
            }
            default -> {
                threadsSpinner.getValueFactory().setValue(8);
                timeoutSpinner.getValueFactory().setValue(20);
                delaySpinner.getValueFactory().setValue(4);
                marginSpinner.getValueFactory().setValue(3);
            }
        }
    }

    private void applySettings() {
        profileBox.setValue(settings.get("loxs.profile", "Standart"));
        threadsSpinner.getValueFactory().setValue(settings.getInt("loxs.threads", 8));
        timeoutSpinner.getValueFactory().setValue(settings.getInt("loxs.timeout", 20));
        delaySpinner.getValueFactory().setValue(settings.getInt("loxs.delay", 4));
        marginSpinner.getValueFactory().setValue(settings.getInt("loxs.margin", 3));
        verifyTlsBox.setSelected(settings.getBoolean("loxs.verifyTls", true));
        showCleanBox.setSelected(settings.getBoolean("loxs.showClean", false));
        ScanType type = ScanType.SQLI;
        try {
            type = ScanType.valueOf(settings.get("loxs.scanner", "SQLI"));
        } catch (Exception ignored) {
            // Varsayılan.
        }
        selectType(type);
    }

    private void saveSettings() {
        settings.set("loxs.scanner", activeType.name());
        settings.set("loxs.profile", profileBox.getValue());
        settings.setInt("loxs.threads", threadsSpinner.getValue());
        settings.setInt("loxs.timeout", timeoutSpinner.getValue());
        settings.setInt("loxs.delay", delaySpinner.getValue());
        settings.setInt("loxs.margin", marginSpinner.getValue());
        settings.setBoolean("loxs.verifyTls", verifyTlsBox.isSelected());
        settings.setBoolean("loxs.showClean", showCleanBox.isSelected());
        settings.save();
    }

    private void showReportMenu() {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        VBox menu = new VBox(2);
        menu.getStyleClass().add("report-menu");
        menu.setStyle("-fx-background-color: #111827; -fx-border-color: #2b3b52; "
                + "-fx-border-width: 1; -fx-padding: 4; -fx-background-radius: 6; -fx-border-radius: 6;");
        menu.setPrefWidth(180);
        for (String[] item : List.of(
                new String[]{"HTML raporu", "html"},
                new String[]{"CSV raporu", "csv"},
                new String[]{"JSON raporu", "json"})) {
            Button button = new Button(item[0]);
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("menu-button");
            button.setOnAction(event -> {
                popup.hide();
                saveReport(item[1]);
            });
            menu.getChildren().add(button);
        }
        popup.getContent().add(menu);
        Bounds bounds = reportButton.localToScreen(reportButton.getBoundsInLocal());
        if (bounds != null) {
            popup.show(reportButton, bounds.getMinX(), bounds.getMaxY() + 2);
        }
    }

    private void saveReport(String format) {
        if (allFindings.isEmpty()) {
            showError("Henüz kaydedilecek sonuç yok.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("LOXS raporu kaydet");
        String ext = format.equals("csv") ? "*.csv" : format.equals("json") ? "*.json" : "*.html";
        chooser.setInitialFileName("loxs_report_" + FILE_DATE.format(LocalDateTime.now()) + ext.substring(1));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(format.toUpperCase(Locale.ROOT), ext));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            switch (format) {
                case "csv" -> ReportWriter.writeCsv(file.toPath(), new ArrayList<>(allFindings));
                case "json" -> ReportWriter.writeJson(file.toPath(), activeType.label(), allFindings.size(),
                        new ArrayList<>(allFindings), allFindings.size());
                default -> ReportWriter.writeHtml(file.toPath(), activeType, allFindings.size(),
                        new ArrayList<>(allFindings), allFindings.size());
            }
            appendLog("Rapor kaydedildi: " + file.getAbsolutePath());
        } catch (Exception exception) {
            showError("Rapor kaydedilemedi: " + exception.getMessage());
        }
    }

    private void appendLog(String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> appendLog(message));
            return;
        }
        logArea.appendText(message + "\n");
        if (logArea.getLength() > 120_000) {
            logArea.deleteText(0, 40_000);
        }
        logArea.positionCaret(logArea.getLength());
    }

    private void showError(String message) {
        Dialogs.error(stage, "LOXS", message);
    }

    private void showInfo(String message) {
        Dialogs.info(stage, "LOXS", message);
    }

    private static Spinner<Integer> spinner(int value, int min, int max, int step) {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, value, step));
        spinner.setEditable(true);
        spinner.setPrefWidth(120);
        return spinner;
    }

    private static TableColumn<Finding, String> column(
            String title, java.util.function.Function<Finding, String> value, double width) {
        TableColumn<Finding, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private final class UiListener implements ScanListener {
        @Override
        public void log(String message) {
            Platform.runLater(() -> appendLog(message));
        }

        @Override
        public void finding(Finding finding) {
            Platform.runLater(() -> addFinding(finding));
        }

        @Override
        public void progress(int completed, int total) {
            long now = System.currentTimeMillis();
            if (completed < total && now - lastProgressAt < 150) {
                return;
            }
            lastProgressAt = now;
            Platform.runLater(() -> {
                progressBar.setProgress(total <= 0 ? 0 : Math.min(1.0, (double) completed / total));
                stageLabel.setText("Tarama " + completed + " / " + total);
            });
        }

        @Override
        public boolean cancelled() {
            return stopRequested;
        }
    }
}
