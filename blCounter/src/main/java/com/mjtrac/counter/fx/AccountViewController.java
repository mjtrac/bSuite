/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Counter — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.counter.fx;

import com.mjtrac.counter.entity.CounterUser;
import com.mjtrac.counter.service.CounterUserService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import org.springframework.stereotype.Component;

/** Mirrors account/password.html + the removed AccountController. */
@Component
public class AccountViewController {

    private final CounterUserService userService;
    private final AuthContext authContext;

    @FXML private Label messageLabel;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;

    public AccountViewController(CounterUserService userService, AuthContext authContext) {
        this.userService = userService;
        this.authContext = authContext;
    }

    @FXML
    private void initialize() {
        hideMessage();
    }

    @FXML
    private void handleChangePassword() {
        String current = currentPasswordField.getText() == null ? "" : currentPasswordField.getText();
        String newPassword = newPasswordField.getText() == null ? "" : newPasswordField.getText();
        String confirm = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        if (!newPassword.equals(confirm)) {
            showError("New passwords do not match.");
            return;
        }
        if (newPassword.length() < 8) {
            showError("New password must be at least 8 characters.");
            return;
        }

        CounterUser user = authContext.getCurrentUser();
        if (user == null) {
            showError("No signed-in user found.");
            return;
        }

        boolean ok = userService.changeOwnPassword(user.getId(), current, newPassword);
        if (!ok) {
            showError("Current password is incorrect.");
            return;
        }

        currentPasswordField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
        showOk("Password changed successfully.");
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
