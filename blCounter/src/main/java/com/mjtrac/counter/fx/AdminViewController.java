/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Counter — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.counter.fx;

import com.mjtrac.counter.entity.CounterUser;
import com.mjtrac.counter.repository.CounterUserRepository;
import com.mjtrac.counter.service.AuditLogService;
import com.mjtrac.counter.service.CounterUserService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Mirrors admin/dashboard.html + user-form.html + the removed AdminController. Admin-only. */
@Component
public class AdminViewController {

    private final CounterUserService userService;
    private final CounterUserRepository userRepo;
    private final AuditLogService auditLog;
    private final AuthContext authContext;

    @FXML private Label messageLabel;
    @FXML private TableView<CounterUser> userTable;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox adminRoleCheck;
    @FXML private CheckBox counterOpRoleCheck;
    @FXML private CheckBox viewerRoleCheck;

    public AdminViewController(CounterUserService userService, CounterUserRepository userRepo,
                                AuditLogService auditLog, AuthContext authContext) {
        this.userService = userService;
        this.userRepo = userRepo;
        this.auditLog = auditLog;
        this.authContext = authContext;
    }

    @FXML
    private void initialize() {
        hideMessage();
        buildColumns();
        refresh();
    }

    private String actorUsername() {
        return authContext.getCurrentUser() != null ? authContext.getCurrentUser().getUsername() : "(system)";
    }

    private void buildColumns() {
        TableColumn<CounterUser, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("username"));
        usernameCol.setPrefWidth(140);

        TableColumn<CounterUser, Void> rolesCol = new TableColumn<>("Roles");
        rolesCol.setPrefWidth(280);
        rolesCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                CounterUser user = getTableView().getItems().get(getIndex());
                CheckBox admin = roleCheckBox("ADMIN", user, CounterUser.Role.ADMIN);
                CheckBox counterOp = roleCheckBox("COUNTER_OP", user, CounterUser.Role.COUNTER_OPERATOR);
                CheckBox viewer = roleCheckBox("VIEWER", user, CounterUser.Role.VIEWER);
                setGraphic(new HBox(10, admin, counterOp, viewer));
            }
        });

        TableColumn<CounterUser, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().isEnabled() ? "Enabled" : "Disabled"));
        statusCol.setPrefWidth(80);

        TableColumn<CounterUser, Void> toggleCol = new TableColumn<>("Enable/Disable");
        toggleCol.setPrefWidth(110);
        toggleCol.setCellFactory(col -> new TableCell<>() {
            private final Button toggleButton = new Button();
            { toggleButton.setOnAction(e -> handleToggle(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                CounterUser u = getTableView().getItems().get(getIndex());
                toggleButton.setText(u.isEnabled() ? "Disable" : "Enable");
                setGraphic(toggleButton);
            }
        });

        TableColumn<CounterUser, Void> resetCol = new TableColumn<>("Reset Password");
        resetCol.setPrefWidth(220);
        resetCol.setCellFactory(col -> new TableCell<>() {
            private final PasswordField field = new PasswordField();
            private final Button resetButton = new Button("Reset");
            private final HBox box = new HBox(6, field, resetButton);
            {
                field.setPromptText("New password (min 8)");
                field.setPrefWidth(140);
                resetButton.setOnAction(e -> {
                    handleResetPassword(getTableView().getItems().get(getIndex()), field.getText());
                    field.clear();
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        userTable.getColumns().setAll(List.of(usernameCol, rolesCol, statusCol, toggleCol, resetCol));
    }

    private CheckBox roleCheckBox(String label, CounterUser user, CounterUser.Role role) {
        CheckBox box = new CheckBox(label);
        box.setSelected(user.getRoles().contains(role));
        box.setOnAction(e -> handleRoleToggle(user, role, box.isSelected()));
        return box;
    }

    private void refresh() {
        userTable.setItems(FXCollections.observableArrayList(userRepo.findAll()));
    }

    @FXML
    private void handleCreateUser() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        Set<CounterUser.Role> roles = EnumSet.noneOf(CounterUser.Role.class);
        if (adminRoleCheck.isSelected()) roles.add(CounterUser.Role.ADMIN);
        if (counterOpRoleCheck.isSelected()) roles.add(CounterUser.Role.COUNTER_OPERATOR);
        if (viewerRoleCheck.isSelected()) roles.add(CounterUser.Role.VIEWER);

        if (username.isEmpty()) {
            showError("Username is required.");
            return;
        }
        if (password.length() < 12) {
            showError("Password is required and must be at least 12 characters.");
            return;
        }
        if (roles.isEmpty()) {
            showError("Please select at least one role.");
            return;
        }

        try {
            userService.createUser(username, password, roles);
            auditLog.log("USER_CREATED", actorUsername(), username + " roles=" + roles);
            usernameField.clear();
            passwordField.clear();
            adminRoleCheck.setSelected(false);
            counterOpRoleCheck.setSelected(false);
            viewerRoleCheck.setSelected(false);
            refresh();
            showOk("Created user \"" + username + "\".");
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void handleToggle(CounterUser user) {
        userService.setEnabled(user.getId(), !user.isEnabled());
        auditLog.log(user.isEnabled() ? "USER_DISABLED" : "USER_ENABLED", actorUsername(), user.getUsername());
        String verb = user.isEnabled() ? "Disabled" : "Enabled";
        refresh();
        showOk(verb + " user \"" + user.getUsername() + "\".");
    }

    private void handleRoleToggle(CounterUser user, CounterUser.Role role, boolean selected) {
        Set<CounterUser.Role> newRoles = EnumSet.copyOf(user.getRoles());
        if (selected) newRoles.add(role); else newRoles.remove(role);
        if (newRoles.isEmpty()) {
            showError("A user must have at least one role.");
            refresh();
            return;
        }
        userService.updateRoles(user.getId(), newRoles);
        auditLog.log("ROLES_UPDATED", actorUsername(), user.getUsername() + " -> " + newRoles);
        refresh();
        showOk("Updated roles for \"" + user.getUsername() + "\".");
    }

    private void handleResetPassword(CounterUser user, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            showError("New password must be at least 8 characters.");
            return;
        }
        userService.changePassword(user.getId(), newPassword);
        auditLog.log("PASSWORD_RESET", actorUsername(), user.getUsername());
        showOk("Password reset for \"" + user.getUsername() + "\".");
    }

    private void showOk(String text) {
        messageLabel.getStyleClass().setAll("msg-ok");
        messageLabel.setText(text);
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void showError(String text) {
        messageLabel.getStyleClass().setAll("msg-error");
        messageLabel.setText(text);
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void hideMessage() {
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);
    }
}
