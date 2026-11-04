package com.northstar.scheduler;

import com.northstar.scheduler.data.SchedulerRepository;
import com.northstar.scheduler.db.Database;
import com.northstar.scheduler.domain.Appointment;
import com.northstar.scheduler.domain.Contact;
import com.northstar.scheduler.domain.Customer;
import com.northstar.scheduler.service.SchedulingService;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SchedulerApp extends Application {
    private final DateTimeFormatter when = DateTimeFormatter.ofPattern("MMM d, yyyy  ·  h:mm a");
    private static final String BRAND = "NORTHSTAR";
    private static final String[] APPOINTMENT_TYPES = {"Consultation", "Planning", "Review", "Follow-up", "Delivery", "Other"};
    private Stage stage;
    private BorderPane shell;
    private VBox sidebar;
    private SchedulerRepository repo;
    private SchedulingService service;
    private String activePage = "Dashboard";
    private String loggedInUser = "";
    private Database database;

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setMinWidth(1040);
        stage.setMinHeight(680);
        try {
            database = new Database(Database.defaultPath());
            database.initialize();
            repo = new SchedulerRepository(database);
            service = new SchedulingService(repo);
            loginScreen();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Unable to initialize local storage:\n" + friendly(e), ButtonType.CLOSE);
            alert.setHeaderText("Startup failed"); alert.showAndWait();
        }
    }

    private void loginScreen() throws SQLException {
        boolean setup = !repo.hasUsers();
        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("auth-root");
        VBox left = new VBox(20);
        left.getStyleClass().add("auth-brand-panel");
        left.setPrefWidth(410);
        left.setAlignment(Pos.CENTER_LEFT);
        Label logo = text(BRAND, "auth-logo");
        Label title = text("Make every\nmeeting count.", "auth-headline");
        Label caption = text("A focused workspace for appointments, customers, and your team's schedule.", "auth-description");
        caption.setWrapText(true);
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        Label foot = text("PRIVATE · LOCAL-FIRST · DESKTOP", "auth-foot");
        left.getChildren().addAll(logo, title, caption, spacer, foot);
        VBox right = new VBox(17);
        right.getStyleClass().add("auth-form-panel");
        right.setAlignment(Pos.CENTER_LEFT);
        Label eyebrow = text(setup ? "GET STARTED" : "WELCOME BACK", "eyebrow");
        Label header = text(setup ? "Create your workspace" : "Sign in to your workspace", "auth-form-title");
        Label sub = text(setup ? "Create a local administrator account. No cloud service required."
                : "Your appointments and contacts are stored securely on this computer.", "muted");
        sub.setWrapText(true);
        TextField username = new TextField(); username.setPromptText("Username");
        PasswordField password = new PasswordField(); password.setPromptText("Password");
        Label status = text("", "error-text"); status.setWrapText(true);
        Button go = primary(setup ? "Create account" : "Sign in"); go.setMaxWidth(Double.MAX_VALUE);
        go.setDefaultButton(true);
        right.getChildren().addAll(eyebrow, header, sub, fieldLabel("USERNAME"), username,
                fieldLabel("PASSWORD"), password);
        PasswordField confirm = new PasswordField();
        if (setup) {
            confirm.setPromptText("Confirm password");
            right.getChildren().addAll(fieldLabel("CONFIRM PASSWORD"), confirm);
            right.getChildren().add(text("Use at least 10 characters. Credentials cannot be recovered without a database backup.", "hint"));
        }
        right.getChildren().addAll(status, go);
        go.setOnAction(ev -> {
            try {
                String name = username.getText().trim();
                char[] pass = password.getText().toCharArray();
                try {
                    if (setup) {
                        if (!password.getText().equals(confirm.getText())) throw new IllegalArgumentException("Passwords do not match.");
                        repo.createFirstAdmin(name, pass);
                    } else if (!repo.authenticate(name, pass)) {
                        throw new IllegalArgumentException("Incorrect username or password.");
                    }
                    loggedInUser = name;
                    showWorkspace();
                } finally { Arrays.fill(pass, '\0'); }
            } catch (Exception e) { status.setText(friendly(e)); }
        });
        layout.setLeft(left); layout.setCenter(right);
        Scene scene = scene(layout, 1050, 700);
        stage.setTitle("Northstar Scheduler · " + (setup ? "Setup" : "Sign in"));
        stage.setScene(scene); stage.show();
    }

    private void showWorkspace() {
        shell = new BorderPane();
        shell.getStyleClass().add("app-root");
        sidebar = new VBox(7); sidebar.getStyleClass().add("sidebar"); sidebar.setPrefWidth(232);
        Label name = text(BRAND, "sidebar-brand");
        Label subtitle = text("SCHEDULING STUDIO", "sidebar-subtitle");
        VBox brand = new VBox(7, name, subtitle); brand.getStyleClass().add("sidebar-brand-area");
        sidebar.getChildren().add(brand);
        sidebar.getChildren().add(text("WORKSPACE", "sidebar-section-label"));
        for (String page : List.of("Dashboard", "Appointments", "Customers", "Contacts", "Reports")) {
            Button link = new Button(symbol(page) + "    " + page);
            link.setMaxWidth(Double.MAX_VALUE); link.getStyleClass().add("sidebar-link");
            link.setOnAction(ev -> openPage(page)); sidebar.getChildren().add(link);
            link.setUserData(page);
        }
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        Label signed = text("SIGNED IN AS", "sidebar-section-label");
        Label user = text(loggedInUser, "sidebar-user");
        Label local = text("●  Local workspace", "sidebar-local");
        Button logout = new Button("↪    Sign out"); logout.getStyleClass().add("sidebar-link");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(e -> { try { loginScreen(); } catch (SQLException ex) { error(ex); } });
        sidebar.getChildren().addAll(spacer, local, signed, user, logout);
        shell.setLeft(sidebar);
        stage.setScene(scene(shell, 1280, 800));
        stage.setTitle("Northstar Scheduler");
        openPage("Dashboard");
    }

    private String symbol(String page) {
        return switch (page) {
            case "Dashboard" -> "▦"; case "Appointments" -> "◷"; case "Customers" -> "♙";
            case "Contacts" -> "◎"; default -> "▥";
        };
    }

    private void openPage(String page) {
        activePage = page;
        if (sidebar != null) for (Node node : sidebar.getChildren()) {
            if (node instanceof Button button && button.getUserData() instanceof String p) {
                button.getStyleClass().remove("sidebar-link-active");
                if (p.equals(page)) button.getStyleClass().add("sidebar-link-active");
            }
        }
        try {
            Node center = switch (page) {
                case "Dashboard" -> dashboard();
                case "Appointments" -> appointmentsView();
                case "Customers" -> customersView();
                case "Contacts" -> contactsView();
                case "Reports" -> reportsView();
                default -> dashboard();
            };
            shell.setCenter(center);
        } catch (Exception e) { error(e); }
    }

    private VBox page(String title, String subtitle, Node controls) {
        VBox body = new VBox(22); body.getStyleClass().add("page");
        HBox header = new HBox(12); header.setAlignment(Pos.CENTER_LEFT);
        VBox intro = new VBox(6, text(title, "page-title"), text(subtitle, "page-subtitle"));
        HBox.setHgrow(intro, Priority.ALWAYS); header.getChildren().add(intro);
        if (controls != null) header.getChildren().add(controls);
        body.getChildren().add(header); return body;
    }

    private Node dashboard() throws SQLException {
        List<Appointment> all = repo.appointments();
        List<Customer> customers = repo.customers();
        List<Contact> contacts = repo.contacts();
        Instant now = Instant.now();
        ZonedDateTime localNow = now.atZone(ZoneId.systemDefault());
        long today = all.stream().filter(a -> a.start().atZone(ZoneId.systemDefault()).toLocalDate().equals(localNow.toLocalDate())).count();
        long upcoming = all.stream().filter(a -> !a.end().isBefore(now)).count();
        Button add = primary("+  New appointment"); add.setOnAction(e -> openPage("Appointments"));
        VBox root = page("Overview", "Your schedule at a glance · " + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")), add);
        HBox metrics = new HBox(16, metric("TODAY'S APPOINTMENTS", Long.toString(today), "Scheduled for today", "metric-blue"),
                metric("UPCOMING", Long.toString(upcoming), "Active and future", "metric-teal"),
                metric("CUSTOMERS", Integer.toString(customers.size()), "Customer records", "metric-violet"),
                metric("TEAM CONTACTS", Integer.toString(contacts.size()), "Available schedulers", "metric-gold"));
        for (Node n : metrics.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        List<Appointment> dueSoon = all.stream()
                .filter(a -> !a.start().isBefore(now) && !a.start().isAfter(now.plus(Duration.ofMinutes(15))))
                .toList();
        if (!dueSoon.isEmpty()) {
            Appointment next = dueSoon.get(0);
            Label alert = text("UPCOMING IN THE NEXT 15 MINUTES    " + next.title() + " · " + format(next.start()), "reminder-banner");
            alert.setMaxWidth(Double.MAX_VALUE);
            root.getChildren().add(alert);
        }
        root.getChildren().add(metrics);
        HBox sections = new HBox(18);
        VBox agendaCard = card(); HBox.setHgrow(agendaCard, Priority.ALWAYS);
        agendaCard.getChildren().addAll(text("Upcoming appointments", "section-title"), text("Your next scheduled meetings", "muted"));
        TableView<Appointment> table = appointmentTable(all.stream().filter(a -> !a.end().isBefore(now)).limit(12).toList(), false);
        table.setPlaceholder(text("No upcoming appointments yet.", "muted"));
        VBox.setVgrow(table, Priority.ALWAYS); table.setMinHeight(350); agendaCard.getChildren().add(table);
        VBox aside = card(); aside.setPrefWidth(290);
        aside.getChildren().addAll(text("Quick start", "section-title"), text("Keep your scheduling organized with a few simple steps.", "muted"));
        quickStep(aside, "01", "Add a contact", "The person responsible for appointments.", "Contacts");
        quickStep(aside, "02", "Register a customer", "Capture the contact and location details.", "Customers");
        quickStep(aside, "03", "Book an appointment", "Choose a time, contact, and customer.", "Appointments");
        Label notice = text("LOCAL DATABASE\n" + database.path(), "database-hint"); notice.setWrapText(true);
        aside.getChildren().add(notice);
        sections.getChildren().addAll(agendaCard,aside);
        VBox.setVgrow(sections, Priority.ALWAYS); root.getChildren().add(sections);
        return root;
    }

    private VBox metric(String label, String value, String sub, String accent) {
        VBox c = new VBox(10); c.getStyleClass().addAll("metric-card",accent);
        c.getChildren().addAll(text(label,"metric-label"),text(value,"metric-value"),text(sub,"muted"));
        c.setMaxWidth(Double.MAX_VALUE); return c;
    }

    private void quickStep(VBox parent,String number,String title,String desc,String target) {
        HBox row = new HBox(13); row.getStyleClass().add("quick-step"); row.setAlignment(Pos.TOP_LEFT);
        Label num = text(number, "quick-number");
        VBox content = new VBox(4,text(title,"quick-title"),text(desc,"muted"));
        row.getChildren().addAll(num,content);
        row.setOnMouseClicked(e -> openPage(target)); parent.getChildren().add(row);
    }

    private Node appointmentsView() throws SQLException {
        List<Appointment> all = repo.appointments();
        Map<Long,String> customerNames = repo.customers().stream().collect(Collectors.toMap(Customer::id,Customer::name));
        Map<Long,String> contactNames = repo.contacts().stream().collect(Collectors.toMap(Contact::id,Contact::name));
        VBox root = page("Appointments", "Plan, manage, and track client meetings.", null);
        HBox bar = new HBox(12); bar.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField(); search.setPromptText("Search appointments..."); search.getStyleClass().add("search-field");
        search.setPrefWidth(250);
        ComboBox<String> filter = new ComboBox<>(FXCollections.observableArrayList("All appointments", "Today", "Next 7 days", "This month"));
        filter.setValue("All appointments"); filter.setPrefWidth(170);
        bar.getChildren().addAll(search,filter); root.getChildren().add(bar);
        HBox columns = new HBox(18); VBox.setVgrow(columns,Priority.ALWAYS);
        VBox tableCard = card(); HBox.setHgrow(tableCard,Priority.ALWAYS);
        Label countLabel = text("", "muted");
        tableCard.getChildren().addAll(text("Scheduled meetings", "section-title"),countLabel);
        TableView<Appointment> table = appointmentTable(all,true);
        table.getColumns().add(column("Customer",a -> customerNames.getOrDefault(a.customerId(),"—")));
        table.getColumns().add(column("Contact",a -> contactNames.getOrDefault(a.contactId(),"—")));
        VBox.setVgrow(table,Priority.ALWAYS); tableCard.getChildren().add(table);
        Runnable apply = () -> {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            LocalDate today = LocalDate.now();
            List<Appointment> filtered = all.stream().filter(a -> {
                LocalDate start = a.start().atZone(ZoneId.systemDefault()).toLocalDate();
                boolean dateOk = switch (filter.getValue()) {
                    case "Today" -> start.equals(today);
                    case "Next 7 days" -> !start.isBefore(today) && start.isBefore(today.plusDays(7));
                    case "This month" -> start.getYear() == today.getYear() && start.getMonth() == today.getMonth();
                    default -> true;
                };
                return dateOk && (a.title().toLowerCase(Locale.ROOT).contains(query)
                        || a.type().toLowerCase(Locale.ROOT).contains(query)
                        || customerNames.getOrDefault(a.customerId(),"").toLowerCase(Locale.ROOT).contains(query));
            }).toList();
            table.setItems(FXCollections.observableArrayList(filtered));
            countLabel.setText(filtered.size() + " appointments");
        };
        search.textProperty().addListener((obs,o,n) -> apply.run());
        filter.valueProperty().addListener((obs,o,n) -> apply.run()); apply.run();

        VBox form = card(); form.setPrefWidth(330); form.getStyleClass().add("form-panel");
        Label formHeading = text("New appointment", "section-title");
        TextField title = new TextField(); title.setPromptText("e.g. Planning session");
        TextField description = new TextField(); description.setPromptText("Optional notes");
        TextField location = new TextField(); location.setPromptText("e.g. Video call or office");
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(APPOINTMENT_TYPES)); type.setValue("Consultation");
        ComboBox<Customer> customer = new ComboBox<>(FXCollections.observableArrayList(repo.customers()));
        ComboBox<Contact> contact = new ComboBox<>(FXCollections.observableArrayList(repo.contacts()));
        DatePicker startDate = new DatePicker(LocalDate.now()); DatePicker endDate = new DatePicker(LocalDate.now());
        ComboBox<LocalTime> startTime = times(); ComboBox<LocalTime> endTime = times();
        startTime.setValue(LocalTime.of(9,0)); endTime.setValue(LocalTime.of(9,30));
        if (!customer.getItems().isEmpty()) customer.getSelectionModel().selectFirst();
        if (!contact.getItems().isEmpty()) contact.getSelectionModel().selectFirst();
        form.getChildren().add(formHeading);
        form.getChildren().addAll(fieldLabel("TITLE"),title,fieldLabel("TYPE"),type,fieldLabel("CUSTOMER"),customer,
                fieldLabel("TEAM CONTACT"),contact,fieldLabel("LOCATION"),location,fieldLabel("NOTES"),description,
                fieldLabel("START"),dateTimeRow(startDate,startTime),fieldLabel("END"),dateTimeRow(endDate,endTime));
        Label err = text("", "error-text"); err.setWrapText(true); form.getChildren().add(err);
        Button save = primary("Save appointment"); Button clear = secondary("Clear form"); Button delete = danger("Delete");
        HBox actions = new HBox(8,save,clear,delete); actions.setAlignment(Pos.CENTER_LEFT);
        form.getChildren().add(actions);
        ScrollPane scroll = new ScrollPane(form); scroll.setFitToWidth(true); scroll.getStyleClass().add("form-scroll");
        scroll.setPrefWidth(360);
        columns.getChildren().addAll(tableCard,scroll); root.getChildren().add(columns);
        final long[] selectedId = {0};
        Runnable reset = () -> {
            selectedId[0]=0; table.getSelectionModel().clearSelection(); formHeading.setText("New appointment");
            title.clear(); description.clear(); location.clear();type.setValue("Consultation");
            startDate.setValue(LocalDate.now()); endDate.setValue(LocalDate.now());
            startTime.setValue(LocalTime.of(9,0)); endTime.setValue(LocalTime.of(9,30)); err.setText("");
            if (!customer.getItems().isEmpty()) customer.getSelectionModel().selectFirst();
            if (!contact.getItems().isEmpty()) contact.getSelectionModel().selectFirst();
        };
        table.getSelectionModel().selectedItemProperty().addListener((obs,old,a) -> {
            if (a == null) return;
            selectedId[0]=a.id(); formHeading.setText("Edit appointment #"+a.id());
            title.setText(a.title()); description.setText(a.description()); location.setText(a.location()); type.setValue(a.type());
            customer.getItems().stream().filter(c -> c.id()==a.customerId()).findFirst().ifPresent(customer::setValue);
            contact.getItems().stream().filter(c -> c.id()==a.contactId()).findFirst().ifPresent(contact::setValue);
            ZonedDateTime start=a.start().atZone(ZoneId.systemDefault()),end=a.end().atZone(ZoneId.systemDefault());
            startDate.setValue(start.toLocalDate());startTime.setValue(start.toLocalTime());
            endDate.setValue(end.toLocalDate());endTime.setValue(end.toLocalTime()); err.setText("");
        });
        clear.setOnAction(e -> reset.run());
        save.setOnAction(e -> {
            try {
                Customer c=customer.getValue(); Contact k=contact.getValue();
                if (c==null||k==null) throw new IllegalArgumentException("Create a customer and contact before scheduling.");
                Appointment a=new Appointment(selectedId[0],title.getText(),description.getText(),location.getText(),type.getValue(),
                    SchedulingService.fromLocal(startDate.getValue(),startTime.getValue(),ZoneId.systemDefault()),
                    SchedulingService.fromLocal(endDate.getValue(),endTime.getValue(),ZoneId.systemDefault()),c.id(),k.id());
                service.save(a); openPage("Appointments");
            } catch (Exception ex) { err.setText(friendly(ex)); }
        });
        delete.setOnAction(e -> {
            if (selectedId[0]==0) {err.setText("Select an appointment to delete."); return;}
            if (confirm("Delete appointment?","This appointment will be permanently removed.")) {
                try {repo.deleteAppointment(selectedId[0]);openPage("Appointments");} catch(Exception ex){err.setText(friendly(ex));}
            }
        });
        return root;
    }

    private TableView<Appointment> appointmentTable(List<Appointment> data, boolean includeType) {
        TableView<Appointment> table = new TableView<>(FXCollections.observableArrayList(data));
        table.getStyleClass().add("modern-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().add(column("Appointment",Appointment::title));
        if (includeType) table.getColumns().add(column("Type",Appointment::type));
        table.getColumns().add(column("Starts",a -> format(a.start())));
        table.getColumns().add(column("Ends",a -> format(a.end())));
        table.setPlaceholder(text("No appointments to display.","muted"));
        return table;
    }

    private Node customersView() throws SQLException {
        List<Customer> records = repo.customers();
        VBox root=page("Customers","Manage customer information and account locations.",null);
        HBox columns=new HBox(18);VBox.setVgrow(columns,Priority.ALWAYS);
        VBox tableCard=card();HBox.setHgrow(tableCard,Priority.ALWAYS);
        TableView<Customer> table=new TableView<>(FXCollections.observableArrayList(records));
        table.getStyleClass().add("modern-table");table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().addAll(column("Name",Customer::name),column("Phone",Customer::phone),
                column("Region",Customer::region),column("Country",Customer::country));
        VBox.setVgrow(table,Priority.ALWAYS);
        TextField search=new TextField();search.setPromptText("Search customers...");search.getStyleClass().add("search-field");
        search.textProperty().addListener((ob,o,n) -> table.setItems(FXCollections.observableArrayList(records.stream()
                .filter(c->c.name().toLowerCase(Locale.ROOT).contains(n.toLowerCase(Locale.ROOT))).toList())));
        tableCard.getChildren().addAll(text("Customer directory", "section-title"),search,table);
        VBox form=card();form.setPrefWidth(320);form.getStyleClass().add("form-panel");
        Label heading=text("New customer","section-title");
        TextField name=new TextField(),phone=new TextField(),address=new TextField(),postal=new TextField(),region=new TextField(),country=new TextField();
        name.setPromptText("Customer / business name"); phone.setPromptText("Phone number");
        address.setPromptText("Street address");postal.setPromptText("Postal code");
        region.setPromptText("State or region");country.setPromptText("Country");
        form.getChildren().addAll(heading,fieldLabel("NAME *"),name,fieldLabel("PHONE"),phone,
                fieldLabel("ADDRESS"),address,fieldLabel("POSTAL CODE"),postal,fieldLabel("REGION"),region,
                fieldLabel("COUNTRY"),country);
        Label err=text("","error-text"); err.setWrapText(true);form.getChildren().add(err);
        Button save=primary("Save customer"),clear=secondary("Clear"),delete=danger("Delete");
        form.getChildren().add(new HBox(8,save,clear,delete));
        ScrollPane scroll=new ScrollPane(form);scroll.setPrefWidth(350);scroll.setFitToWidth(true);scroll.getStyleClass().add("form-scroll");
        columns.getChildren().addAll(tableCard,scroll);root.getChildren().add(columns);
        final long[] id={0};
        Runnable reset=()->{id[0]=0;heading.setText("New customer");table.getSelectionModel().clearSelection();
            for(TextField f:List.of(name,phone,address,postal,region,country)) f.clear();err.setText("");};
        table.getSelectionModel().selectedItemProperty().addListener((ob,o,c)->{
            if(c==null)return;id[0]=c.id();heading.setText("Edit customer #"+c.id());name.setText(c.name());phone.setText(c.phone());
            address.setText(c.address());postal.setText(c.postalCode());region.setText(c.region());country.setText(c.country());err.setText("");
        });
        clear.setOnAction(e->reset.run());
        save.setOnAction(e->{try{repo.saveCustomer(new Customer(id[0],name.getText(),address.getText(),postal.getText(),
                phone.getText(),region.getText(),country.getText()));openPage("Customers");}catch(Exception ex){err.setText(friendly(ex));}});
        delete.setOnAction(e->{if(id[0]==0){err.setText("Select a customer first.");return;}
            if(confirm("Delete customer?","Customers assigned to appointments cannot be deleted until those appointments are removed."))
                try{repo.deleteCustomer(id[0]);openPage("Customers");}catch(Exception ex){err.setText(friendly(ex));}
        });return root;
    }

    private Node contactsView() throws SQLException {
        List<Contact> records=repo.contacts();
        VBox root=page("Contacts","People who own and manage appointments.",null);
        HBox columns=new HBox(18);VBox.setVgrow(columns,Priority.ALWAYS);
        VBox tableCard=card();HBox.setHgrow(tableCard,Priority.ALWAYS);
        TableView<Contact> table=new TableView<>(FXCollections.observableArrayList(records));
        table.getStyleClass().add("modern-table");table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().addAll(column("Name",Contact::name),column("Email",Contact::email));
        VBox.setVgrow(table,Priority.ALWAYS);
        tableCard.getChildren().addAll(text("Team directory","section-title"),text(records.size()+" contacts","muted"),table);
        VBox form=card();form.getStyleClass().add("form-panel");form.setPrefWidth(315);
        Label heading=text("New contact","section-title");TextField name=new TextField(),email=new TextField();
        name.setPromptText("Full name");email.setPromptText("Email address");
        Label err=text("","error-text");err.setWrapText(true);
        Button save=primary("Save contact"),clear=secondary("Clear"),delete=danger("Delete");
        form.getChildren().addAll(heading,fieldLabel("NAME *"),name,fieldLabel("EMAIL"),email,err,new HBox(8,save,clear,delete));
        ScrollPane scroll=new ScrollPane(form);scroll.setFitToWidth(true);scroll.setPrefWidth(345);scroll.getStyleClass().add("form-scroll");
        columns.getChildren().addAll(tableCard,scroll);root.getChildren().add(columns);
        final long[] id={0};
        Runnable reset=()->{id[0]=0;heading.setText("New contact");name.clear();email.clear();err.setText("");table.getSelectionModel().clearSelection();};
        table.getSelectionModel().selectedItemProperty().addListener((ob,o,c)->{
            if(c==null)return;id[0]=c.id();name.setText(c.name());email.setText(c.email());heading.setText("Edit contact #"+c.id());err.setText("");});
        clear.setOnAction(e->reset.run());
        save.setOnAction(e->{try{if(!email.getText().isBlank()&&!email.getText().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
                    throw new IllegalArgumentException("Enter a valid email address.");
                repo.saveContact(new Contact(id[0],name.getText(),email.getText()));openPage("Contacts");}
            catch(Exception ex){err.setText(friendly(ex));}});
        delete.setOnAction(e->{if(id[0]==0){err.setText("Select a contact first.");return;}
            if(confirm("Delete contact?","Contacts assigned to appointments cannot be deleted until those appointments are removed."))
                try{repo.deleteContact(id[0]);openPage("Contacts");}catch(Exception ex){err.setText(friendly(ex));}
        });return root;
    }

    private Node reportsView() throws SQLException {
        List<Appointment> appointments=repo.appointments();List<Customer> customers=repo.customers();List<Contact> contacts=repo.contacts();
        VBox root=page("Reports","Understand activity across your appointments and customers.",null);
        HBox row=new HBox(18);VBox.setVgrow(row,Priority.ALWAYS);
        VBox a=card();HBox.setHgrow(a,Priority.ALWAYS);
        a.getChildren().add(text("Appointments by type","section-title"));
        Map<String,Long> types=appointments.stream().collect(Collectors.groupingBy(Appointment::type,TreeMap::new,Collectors.counting()));
        a.getChildren().add(reportTable(types));
        VBox b=card();HBox.setHgrow(b,Priority.ALWAYS);
        b.getChildren().add(text("Appointments by month","section-title"));
        DateTimeFormatter month=DateTimeFormatter.ofPattern("MMMM yyyy");
        Map<YearMonth,Long> monthlyCounts=appointments.stream().collect(Collectors.groupingBy(
                x->YearMonth.from(x.start().atZone(ZoneId.systemDefault())),TreeMap::new,Collectors.counting()));
        Map<String,Long> months=new LinkedHashMap<>();
        monthlyCounts.forEach((period,count)->months.put(period.format(month),count));
        b.getChildren().add(reportTable(months));row.getChildren().addAll(a,b);
        HBox lower=new HBox(18);VBox.setVgrow(lower,Priority.ALWAYS);
        VBox c=card();HBox.setHgrow(c,Priority.ALWAYS);
        c.getChildren().add(text("Customers by region","section-title"));
        Map<String,Long> regions=customers.stream().collect(Collectors.groupingBy(x->x.region().isBlank()?"Unspecified":x.region(),TreeMap::new,Collectors.counting()));
        c.getChildren().add(reportTable(regions));
        VBox d=card();HBox.setHgrow(d,Priority.ALWAYS);
        d.getChildren().add(text("Contact workload","section-title"));
        Map<String,Long> contactCounts=new TreeMap<>();
        for(Contact contact:contacts) contactCounts.put(contact.name() + " (#" + contact.id() + ")",
                appointments.stream().filter(x->x.contactId()==contact.id()).count());
        d.getChildren().add(reportTable(contactCounts));lower.getChildren().addAll(c,d);
        root.getChildren().addAll(row,lower);
        HBox detail = new HBox(18);
        VBox locationCard = card(); locationCard.setPrefWidth(310);
        locationCard.getChildren().add(text("Appointments by location", "section-title"));
        Map<String,Long> locations = appointments.stream().collect(Collectors.groupingBy(
                x -> x.location().isBlank() ? "Unspecified" : x.location(), TreeMap::new, Collectors.counting()));
        locationCard.getChildren().add(reportTable(locations));
        VBox contactCard = card(); HBox.setHgrow(contactCard, Priority.ALWAYS);
        contactCard.getChildren().add(text("Contact schedule", "section-title"));
        ComboBox<Contact> selectedContact = new ComboBox<>(FXCollections.observableArrayList(contacts));
        selectedContact.setPromptText("Select a contact"); selectedContact.setPrefWidth(260);
        TableView<Appointment> contactMeetings = appointmentTable(List.of(), true);
        contactMeetings.setMinHeight(190);
        selectedContact.valueProperty().addListener((obs,o,n) -> contactMeetings.setItems(FXCollections.observableArrayList(
                appointments.stream().filter(appointment -> n != null && appointment.contactId() == n.id()).toList())));
        if (!contacts.isEmpty()) selectedContact.getSelectionModel().selectFirst();
        contactCard.getChildren().addAll(selectedContact, contactMeetings);
        detail.getChildren().addAll(locationCard,contactCard);
        root.getChildren().add(detail);
        Label footer=text("Report counts reflect all locally stored records. Dates use your computer's time zone.","hint");
        root.getChildren().add(footer);
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("reports-scroll");
        return scroll;
    }

    private TableView<ReportItem> reportTable(Map<String,Long> values) {
        List<ReportItem> items=values.entrySet().stream().map(e->new ReportItem(e.getKey(),e.getValue())).toList();
        TableView<ReportItem> table=new TableView<>(FXCollections.observableArrayList(items));
        table.getStyleClass().add("modern-table");table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().addAll(column("Category",ReportItem::label),column("Total",x->Long.toString(x.count())));
        table.setPlaceholder(text("No data yet.","muted"));VBox.setVgrow(table,Priority.ALWAYS);table.setMinHeight(165);return table;
    }
    private record ReportItem(String label,long count) { }

    private <T> TableColumn<T,String> column(String title,Function<T,String> getter) {
        TableColumn<T,String> col=new TableColumn<>(title);
        col.setCellValueFactory(v->new SimpleStringProperty(Optional.ofNullable(getter.apply(v.getValue())).orElse("")));
        return col;
    }
    private HBox dateTimeRow(DatePicker date,ComboBox<LocalTime> time) {
        HBox box=new HBox(8,date,time);HBox.setHgrow(date,Priority.ALWAYS);date.setMaxWidth(Double.MAX_VALUE);
        time.setPrefWidth(112);return box;
    }
    private ComboBox<LocalTime> times() {
        List<LocalTime> values=new ArrayList<>();for(int h=0;h<24;h++)for(int m=0;m<60;m+=30)values.add(LocalTime.of(h,m));
        ComboBox<LocalTime> cb=new ComboBox<>(FXCollections.observableArrayList(values));cb.setMaxWidth(Double.MAX_VALUE);
        DateTimeFormatter fmt=DateTimeFormatter.ofPattern("h:mm a");
        cb.setConverter(new StringConverter<>() {
            public String toString(LocalTime t){return t==null?"":t.format(fmt);}
            public LocalTime fromString(String s){return LocalTime.parse(s,fmt);}
        });
        return cb;
    }
    private String format(Instant i){return when.format(i.atZone(ZoneId.systemDefault()));}
    private Label text(String s,String style){Label l=new Label(s);l.getStyleClass().add(style);return l;}
    private Label fieldLabel(String s){return text(s,"field-label");}
    private Button primary(String s){Button b=new Button(s);b.getStyleClass().add("btn-primary");return b;}
    private Button secondary(String s){Button b=new Button(s);b.getStyleClass().add("btn-secondary");return b;}
    private Button danger(String s){Button b=new Button(s);b.getStyleClass().add("btn-danger");return b;}
    private VBox card(){VBox c=new VBox(14);c.getStyleClass().add("card");return c;}
    private Scene scene(Node node,int width,int height){
        Scene s=new Scene((javafx.scene.Parent)node,width,height);
        s.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/northstar/scheduler/theme.css")).toExternalForm());
        return s;
    }
    private boolean confirm(String title,String details){
        Alert alert=new Alert(Alert.AlertType.CONFIRMATION,details,ButtonType.CANCEL,ButtonType.OK);
        alert.setTitle(title);alert.setHeaderText(title);return alert.showAndWait().filter(x->x==ButtonType.OK).isPresent();
    }
    private String friendly(Throwable e){
        if(e instanceof SQLException && e.getMessage()!=null && e.getMessage().contains("FOREIGN KEY"))
            return "This record is assigned to an appointment. Remove or reassign that appointment first.";
        return e.getMessage()==null ? e.getClass().getSimpleName() : e.getMessage();
    }
    private void error(Throwable e){Alert a=new Alert(Alert.AlertType.ERROR,friendly(e),ButtonType.OK);
        a.setTitle("Scheduling error");a.setHeaderText("The operation could not be completed");a.showAndWait();}
}
