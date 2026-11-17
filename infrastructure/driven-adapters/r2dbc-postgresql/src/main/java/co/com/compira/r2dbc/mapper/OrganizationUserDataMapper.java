package co.com.compira.r2dbc.mapper;

import co.com.compira.model.user.OrganizationUser;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrganizationUserDataMapper {
    private static final String ID = "id";
    private static final String EMAIL = "email";
    private static final String FIRST_NAME = "first_name";
    private static final String LAST_NAME = "last_name";
    private static final String PHONE_NUMBER = "phone_number";
    private static final String STATUS = "status";
    private static final String TEAM_ID = "team_id";
    private static final String TEAM_NAME = "team_name";
    private static final String CREATED_AT = "created_at";
    private static final String LAST_LOGIN_AT = "last_login_at";

    public OrganizationUser toDomain(Map<String, Object> row, List<String> roles) {
        return new OrganizationUser(
                uuid(row, ID),
                string(row, EMAIL),
                string(row, FIRST_NAME),
                string(row, LAST_NAME),
                string(row, PHONE_NUMBER),
                string(row, STATUS),
                roles,
                uuid(row, TEAM_ID),
                string(row, TEAM_NAME),
                dateTime(row, CREATED_AT),
                dateTime(row, LAST_LOGIN_AT));
    }

    private UUID uuid(Map<String, Object> row, String key) {
        return (UUID) row.get(key);
    }

    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private OffsetDateTime dateTime(Map<String, Object> row, String key) {
        return (OffsetDateTime) row.get(key);
    }
}
