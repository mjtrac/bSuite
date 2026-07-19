/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.model.User;
import com.mjtrac.ballot.repository.UserRepository;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/** Mirrors templates/auth/login.html / the removed AuthController form-login flow. */
@Component
public class LoginViewController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthContext authContext;
    private final Navigator navigator;

    @Value("${app.login-title:pbss Ballot Builder}")
    private String loginTitle;

    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button signInButton;

    public LoginViewController(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                AuthContext authContext,
                                Navigator navigator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authContext = authContext;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        titleLabel.setText(loginTitle);
        hideMessage();
    }

    @FXML
    private void handleSignIn() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        Optional<User> match = userRepository.findByUsername(username)
            .filter(User::isEnabled)
            .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()));

        if (match.isEmpty()) {
            showError("Invalid username or password.");
            passwordField.clear();
            return;
        }

        authContext.setCurrentUser(match.get());
        try {
            navigator.showShell();
        } catch (IOException e) {
            showError("Could not open the dashboard: " + e.getMessage());
        }
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
