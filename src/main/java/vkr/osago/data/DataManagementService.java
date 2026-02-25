package vkr.osago.data;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class DataManagementService {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public DataManagementService(JdbcTemplate jdbc, DataSource dataSource) {
        if (jdbc == null) throw new IllegalArgumentException("jdbc is required");
        if (dataSource == null) throw new IllegalArgumentException("dataSource is required");
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    public boolean tableExists(String schema, String table) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = ? and table_name = ?",
                Integer.class,
                schema,
                table
        );
        return count != null && count > 0;
    }

    public long createUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        return jdbc.queryForObject(
                "insert into insurance.users(email, password_hash, first_name, last_name, status, self_registered) " +
                        "values (?, ?, ?, ?, ?, true) returning id",
                Long.class,
                email.trim().toLowerCase(),
                "$2a$10$testhash",
                "Test",
                "User",
                "CLIENT"
        );
    }

    public long createVehicle(long ownerUserId, String regNumber, String vin) {
        return jdbc.queryForObject(
                "insert into insurance.vehicles(owner_user_id, brand, model, vin, reg_number) " +
                        "values (?, ?, ?, ?, ?) returning id",
                Long.class,
                ownerUserId,
                "Audi",
                "A4",
                vin,
                regNumber
        );
    }

    public long createPolicy(
            long userId,
            long vehicleId,
            long categoryId,
            long regionId,
            long tariffVersionId,
            int powerHp,
            int termMonths,
            BigDecimal premium
    ) {
        return jdbc.queryForObject(
                "insert into insurance.policies(number, user_id, type, status, start_date, end_date, vehicle_id, tariff_version_id, " +
                        "vehicle_category_id, region_id, power_hp, unlimited_drivers, term_months, premium_amount, consent_accuracy, consent_personal_data) " +
                        "values (?, ?, ?::insurance.policy_type, ?::insurance.policy_status, ?, ?, ?, ?, ?, ?, ?, false, ?, ?, true, true) " +
                        "returning id",
                Long.class,
                null,
                userId,
                "OSAGO",
                "DRAFT",
                LocalDate.now(),
                LocalDate.now().plusMonths(termMonths),
                vehicleId,
                tariffVersionId,
                categoryId,
                regionId,
                powerHp,
                termMonths,
                premium
        );
    }

    public long createPolicyApplication(long userId, long vehicleId, long policyId) {
        return jdbc.queryForObject(
                "insert into insurance.policy_applications(user_id, policy_type, vehicle_id, status, issued_policy_id) " +
                        "values (?, ?::insurance.policy_type, ?, ?, ?) returning id",
                Long.class,
                userId,
                "OSAGO",
                vehicleId,
                "NEW",
                policyId
        );
    }

    public long createClaim(long userId, long policyId, String number, String description) {
        return jdbc.queryForObject(
                "insert into insurance.claims(number, user_id, policy_id, status, description, accident_type, " +
                        "contact_phone, consent_personal_data, consent_accuracy, accident_at, accident_place) " +
                        "values (?, ?, ?, ?::insurance.claim_status, ?, ?::insurance.accident_type, ?, true, true, now(), ?) returning id",
                Long.class,
                number,
                userId,
                policyId,
                "NEW",
                description,
                "COLLISION",
                "+79001234567",
                "Moscow"
        );
    }

    public long createPayment(long policyId, BigDecimal amount, String status) {
        return jdbc.queryForObject(
                "insert into insurance.payments(policy_id, amount, status, provider, external_id) " +
                        "values (?, ?, ?::insurance.payment_status, ?, ?) returning id",
                Long.class,
                policyId,
                amount,
                status,
                "TEST",
                "ext-" + System.nanoTime()
        );
    }

    public long createNotification(long recipientId, String type, String title, String message) {
        return jdbc.queryForObject(
                "insert into insurance.notifications(recipient_id, type, title, message, body) values (?, ?, ?, ?, ?) returning id",
                Long.class,
                recipientId,
                type,
                title,
                message,
                message
        );
    }

    public List<Map<String, Object>> userNotifications(long recipientId) {
        return jdbc.queryForList(
                "select id, type, title, message, is_read from insurance.notifications " +
                        "where recipient_id = ? order by created_at desc",
                recipientId
        );
    }

    public int markNotificationRead(long notificationId) {
        return jdbc.update(
                "update insurance.notifications set is_read = true, read_at = now() where id = ?",
                notificationId
        );
    }

    public boolean compareAndSetPolicyStatus(long policyId, String expectedStatus, String newStatus) {
        int updated = jdbc.update(
                "update insurance.policies set status = ?::insurance.policy_status where id = ? and status = ?::insurance.policy_status",
                newStatus,
                policyId,
                expectedStatus
        );
        return updated == 1;
    }

    public void updatePolicyAndApplicationInTransaction(
            long policyId,
            String policyStatus,
            long applicationId,
            String applicationStatus,
            boolean forceFailure
    ) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            try (PreparedStatement policyPs = connection.prepareStatement(
                    "update insurance.policies set status = ?::insurance.policy_status where id = ?"
            )) {
                policyPs.setString(1, policyStatus);
                policyPs.setLong(2, policyId);
                policyPs.executeUpdate();
            }

            if (forceFailure) {
                throw new IllegalStateException("forced transaction failure");
            }

            try (PreparedStatement appPs = connection.prepareStatement(
                    "update insurance.policy_applications set status = ?, updated_at = now() where id = ?"
            )) {
                appPs.setString(1, applicationStatus);
                appPs.setLong(2, applicationId);
                appPs.executeUpdate();
            }

            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly(connection);
            throw new IllegalStateException("transaction failed", ex);
        } catch (RuntimeException ex) {
            rollbackQuietly(connection);
            throw ex;
        } finally {
            closeQuietly(connection);
        }
    }

    public PolicyBackup backupPolicy(long policyId) {
        return jdbc.queryForObject(
                "select number, user_id, type::text, status::text, start_date, end_date, vehicle_id, tariff_version_id, " +
                        "vehicle_category_id, region_id, power_hp, unlimited_drivers, term_months, premium_amount, " +
                        "consent_accuracy, consent_personal_data " +
                        "from insurance.policies where id = ?",
                (rs, rowNum) -> mapBackup(rs),
                policyId
        );
    }

    public int deletePolicy(long policyId) {
        return jdbc.update("delete from insurance.policies where id = ?", policyId);
    }

    public long restorePolicy(PolicyBackup backup) {
        if (backup == null) {
            throw new IllegalArgumentException("backup is required");
        }
        return jdbc.queryForObject(
                "insert into insurance.policies(number, user_id, type, status, start_date, end_date, vehicle_id, tariff_version_id, " +
                        "vehicle_category_id, region_id, power_hp, unlimited_drivers, term_months, premium_amount, " +
                        "consent_accuracy, consent_personal_data) " +
                        "values (?, ?, ?::insurance.policy_type, ?::insurance.policy_status, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class,
                backup.number(),
                backup.userId(),
                backup.type(),
                backup.status(),
                backup.startDate(),
                backup.endDate(),
                backup.vehicleId(),
                backup.tariffVersionId(),
                backup.vehicleCategoryId(),
                backup.regionId(),
                backup.powerHp(),
                backup.unlimitedDrivers(),
                backup.termMonths(),
                backup.premiumAmount(),
                backup.consentAccuracy(),
                backup.consentPersonalData()
        );
    }

    public long findFirstTariffVersionId() {
        Long id = jdbc.queryForObject(
                "select id from insurance.osago_tariff_versions order by id limit 1",
                Long.class
        );
        if (id == null) throw new IllegalStateException("tariff version not found");
        return id;
    }

    public long findFirstRegionId() {
        Long id = jdbc.queryForObject(
                "select id from insurance.ref_regions order by id limit 1",
                Long.class
        );
        if (id == null) throw new IllegalStateException("region not found");
        return id;
    }

    public long findFirstVehicleCategoryId() {
        Long id = jdbc.queryForObject(
                "select id from insurance.ref_vehicle_categories order by id limit 1",
                Long.class
        );
        if (id == null) throw new IllegalStateException("vehicle category not found");
        return id;
    }

    private PolicyBackup mapBackup(ResultSet rs) throws SQLException {
        return new PolicyBackup(
                rs.getString("number"),
                rs.getLong("user_id"),
                rs.getString("type"),
                rs.getString("status"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                getNullableLong(rs, "vehicle_id"),
                getNullableLong(rs, "tariff_version_id"),
                getNullableLong(rs, "vehicle_category_id"),
                getNullableLong(rs, "region_id"),
                getNullableInt(rs, "power_hp"),
                rs.getBoolean("unlimited_drivers"),
                getNullableInt(rs, "term_months"),
                rs.getBigDecimal("premium_amount"),
                rs.getBoolean("consent_accuracy"),
                rs.getBoolean("consent_personal_data")
        );
    }

    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private void rollbackQuietly(Connection connection) {
        if (connection == null) return;
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Ignore rollback exception to keep original failure.
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) return;
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // Connection may already be closed.
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // No-op.
        }
    }

    public record PolicyBackup(
            String number,
            long userId,
            String type,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            Long vehicleId,
            Long tariffVersionId,
            Long vehicleCategoryId,
            Long regionId,
            Integer powerHp,
            boolean unlimitedDrivers,
            Integer termMonths,
            BigDecimal premiumAmount,
            boolean consentAccuracy,
            boolean consentPersonalData
    ) {
    }
}
