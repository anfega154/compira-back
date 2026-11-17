package co.com.compira.r2dbc;

import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.RegisterUserCommand;
import co.com.compira.model.auth.RoleCode;

final class AuthenticationPersistenceTestData {
    static final String EMAIL = "first.login@compira.co";
    static final String SUBJECT = "first-login-subject";
    static final String RESET_USERS = "TRUNCATE users CASCADE";
    static final String DISABLE_USER = "UPDATE users SET status = 'DISABLED' WHERE email = :email";

    private AuthenticationPersistenceTestData() { }

    static RegisterUserCommand registration() {
        return new RegisterUserCommand(EMAIL, "TemporaryPass123!", "First", "Login", "+573001112233",
                MfaChannel.EMAIL, RoleCode.COLLABORATOR);
    }
}
