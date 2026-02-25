package vkr.osago.data;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class DataManagementServiceUnitTests {

    @Test
    void constructorAndTableCheckShouldWork() {
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate();
        DataSource ds = mock(DataSource.class);

        assertThrows(IllegalArgumentException.class, () -> new DataManagementService(null, ds));
        assertThrows(IllegalArgumentException.class, () -> new DataManagementService(jdbc, null));

        DataManagementService service = new DataManagementService(jdbc, ds);
        jdbc.tableExistsResult = 1;
        assertTrue(service.tableExists("insurance", "users"));
        jdbc.tableExistsResult = 0;
        assertFalse(service.tableExists("insurance", "unknown"));
    }

    @Test
    void createAndLookupMethodsShouldWork() {
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate();
        DataManagementService service = new DataManagementService(jdbc, mock(DataSource.class));

        assertThrows(IllegalArgumentException.class, () -> service.createUser(" "));
        assertEquals(10L, service.createUser("u@test.local"));
        assertEquals(10L, service.createVehicle(1L, "A123AA196", "VIN-1"));
        assertEquals(10L, service.createPolicy(1L, 1L, 1L, 1L, 1L, 150, 12, new BigDecimal("10000.00")));
        assertEquals(10L, service.createPolicyApplication(1L, 1L, 1L));
        assertEquals(10L, service.createClaim(1L, 1L, "CLM-1", "desc"));
        assertEquals(10L, service.createPayment(1L, new BigDecimal("10000.00"), "NEW"));
        assertEquals(10L, service.createNotification(1L, "NEW_MESSAGE", "title", "msg"));

        assertEquals(1, service.userNotifications(1L).size());
        assertEquals(1, service.markNotificationRead(1L));
        assertTrue(service.compareAndSetPolicyStatus(1L, "DRAFT", "ACTIVE"));

        assertEquals(1L, service.findFirstRegionId());
        assertEquals(2L, service.findFirstVehicleCategoryId());
        assertEquals(3L, service.findFirstTariffVersionId());

        jdbc.regionId = null;
        assertThrows(IllegalStateException.class, service::findFirstRegionId);
    }

    @Test
    void backupRestoreAndDeleteShouldWork() {
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate();
        DataManagementService service = new DataManagementService(jdbc, mock(DataSource.class));

        DataManagementService.PolicyBackup backup = service.backupPolicy(10L);
        assertNotNull(backup);
        assertEquals("EEE 000000001", backup.number());

        assertEquals(1, service.deletePolicy(10L));
        assertEquals(10L, service.restorePolicy(backup));
        assertThrows(IllegalArgumentException.class, () -> service.restorePolicy(null));
    }

    @Test
    void transactionShouldCommitAndRollback() throws Exception {
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate();
        DataSource ds = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement ps1 = mock(PreparedStatement.class);
        PreparedStatement ps2 = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(startsWith("update insurance.policies"))).thenReturn(ps1);
        when(connection.prepareStatement(startsWith("update insurance.policy_applications"))).thenReturn(ps2);

        DataManagementService service = new DataManagementService(jdbc, ds);
        service.updatePolicyAndApplicationInTransaction(1L, "ACTIVE", 2L, "APPROVED", false);
        verify(connection, atLeastOnce()).commit();

        assertThrows(IllegalStateException.class, () ->
                service.updatePolicyAndApplicationInTransaction(1L, "ACTIVE", 2L, "APPROVED", true)
        );
        verify(connection, atLeastOnce()).rollback();
    }

    @Test
    void transactionShouldHandleSqlException() throws Exception {
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate();
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("db down"));
        DataManagementService service = new DataManagementService(jdbc, ds);

        assertThrows(IllegalStateException.class, () ->
                service.updatePolicyAndApplicationInTransaction(1L, "ACTIVE", 2L, "APPROVED", false)
        );
    }

    private static class FakeJdbcTemplate extends JdbcTemplate {
        int tableExistsResult = 1;
        Long regionId = 1L;
        Long categoryId = 2L;
        Long tariffId = 3L;

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (requiredType == Integer.class) {
                return requiredType.cast(tableExistsResult);
            }
            if (requiredType == Long.class) {
                if (sql.contains("ref_regions")) return requiredType.cast(regionId);
                if (sql.contains("ref_vehicle_categories")) return requiredType.cast(categoryId);
                if (sql.contains("osago_tariff_versions")) return requiredType.cast(tariffId);
                return requiredType.cast(10L);
            }
            if (requiredType == String.class) {
                return requiredType.cast("DRAFT");
            }
            return null;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            if (requiredType == Long.class) {
                if (sql.contains("ref_regions")) return requiredType.cast(regionId);
                if (sql.contains("ref_vehicle_categories")) return requiredType.cast(categoryId);
                if (sql.contains("osago_tariff_versions")) return requiredType.cast(tariffId);
                return requiredType.cast(10L);
            }
            return null;
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            try {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("number")).thenReturn("EEE 000000001");
                when(rs.getLong(anyString())).thenReturn(1L);
                when(rs.getInt(anyString())).thenReturn(12);
                when(rs.wasNull()).thenReturn(false);
                when(rs.getObject("start_date", LocalDate.class)).thenReturn(LocalDate.now());
                when(rs.getObject("end_date", LocalDate.class)).thenReturn(LocalDate.now().plusMonths(12));
                when(rs.getBoolean(anyString())).thenReturn(true);
                when(rs.getBigDecimal("premium_amount")).thenReturn(new BigDecimal("9999.99"));
                when(rs.getString("type")).thenReturn("OSAGO");
                when(rs.getString("status")).thenReturn("DRAFT");
                return rowMapper.mapRow(rs, 0);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public int update(String sql, Object... args) {
            return 1;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return List.of(Map.of("id", 1L));
        }
    }
}
