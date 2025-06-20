package com.nextgen;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
@EnableScheduling
@RequestMapping("/api")
public class App {

    static final String DB_URL = "jdbc:postgresql://localhost:5433/numberGenerator";
    static final String DB_USER = "postgres";
    static final String DB_PASSWORD = "admin@123";
    static final List<String> MONTHS = Arrays.asList("L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W");

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @GetMapping("/generate")
    public Map<String, String> generateCode(@RequestParam String username) {
        Map<String, String> response = new HashMap<>();

        if (username == null || username.trim().isEmpty()) {
            logToHistory("ERROR: Username is empty", "unknown");
            response.put("error", "❌ Username cannot be empty.");
            return response;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String query = "SELECT * FROM config ORDER BY id ASC LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                if (!rs.next()) {
                    logToHistory("ERROR: No config found", username);
                    response.put("error", "❌ No config data found.");
                    return response;
                }

                int id = rs.getInt("id");
                String site = rs.getString("site");
                String area = rs.getString("area");
                String year = rs.getString("year");
                String month = rs.getString("month");
                int day = rs.getInt("day");
                int serial = rs.getInt("serial_number");
                int serialLength = rs.getInt("serial_length");
                String orderString = rs.getString("code_order");

                if (site == null || area == null || year == null || month == null || orderString == null) {
                    logToHistory("ERROR: Null fields in config", username);
                    response.put("error", "❌ Config table contains null fields.");
                    return response;
                }

                List<String> codeOrder = Arrays.asList(orderString.split(","));
                if (codeOrder.stream().anyMatch(s -> s == null || s.trim().isEmpty())) {
                    logToHistory("ERROR: Invalid code_order format", username);
                    response.put("error", "❌ Invalid code_order format in config.");
                    return response;
                }

                int nextSerial = serial + 1;
                int maxSerial = (int) Math.pow(10, serialLength) - 1;

                int newDay = day;
                String newMonth = month;
                int newYear = Integer.parseInt(year);

                if (nextSerial > maxSerial) {
                    nextSerial = 1;
                    newDay++;
                    if (newDay > 31) {
                        newDay = 1;
                        int monthIndex = MONTHS.indexOf(month);
                        if (monthIndex == -1 || monthIndex + 1 >= MONTHS.size()) {
                            newMonth = MONTHS.get(0);
                            newYear += 1;
                        } else {
                            newMonth = MONTHS.get(monthIndex + 1);
                        }
                    }
                }

                Map<String, String> values = new HashMap<>();
                values.put("site", site);
                values.put("area", area);
                values.put("year", String.valueOf(newYear));
                values.put("month", newMonth);
                values.put("day", String.format("%02d", newDay));
                values.put("serial", String.format("%0" + serialLength + "d", nextSerial));

                StringBuilder code = new StringBuilder();
                for (String part : codeOrder) {
                    code.append(values.getOrDefault(part.trim(), ""));
                }

                String generatedCode = code.toString();

                // Check duplicate
                String checkSQL = "SELECT COUNT(*) FROM history WHERE generated_code = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSQL)) {
                    checkStmt.setString(1, generatedCode);
                    ResultSet checkRs = checkStmt.executeQuery();
                    if (checkRs.next() && checkRs.getInt(1) > 0) {
                        logToHistory("ERROR: Duplicate code - " + generatedCode, username);
                        response.put("error", "❌ Duplicate code detected: " + generatedCode);
                        return response;
                    }
                }

                // Update config
                String update = "UPDATE config SET serial_number = ?, day = ?, month = ?, year = ? WHERE id = ?";
                try (PreparedStatement updateStmt = conn.prepareStatement(update)) {
                    updateStmt.setInt(1, nextSerial);
                    updateStmt.setInt(2, newDay);
                    updateStmt.setString(3, newMonth);
                    updateStmt.setString(4, String.valueOf(newYear));
                    updateStmt.setInt(5, id);
                    updateStmt.executeUpdate();
                }

                logToHistory(generatedCode, username);
                response.put("code", generatedCode);
                response.put("created_by", username.trim());
                response.put("status", "✅ Code generated successfully");
                return response;

            } catch (Exception ex) {
                String errorMessage = "ERROR: Exception - " + ex.getMessage();
                logToHistory(errorMessage, username);
                response.put("error", "❌ Internal error occurred.");
                return response;
            }

        } catch (SQLException e) {
            String errorMessage = "ERROR: DB Error - " + e.getMessage();
            logToHistory(errorMessage, username);
            response.put("error", "❌ Database connection failed.");
            return response;
        }
    }

    // Log success or failure into history
    private void logToHistory(String code, String user) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String insertSQL = "INSERT INTO history (generated_code, created_by, created_in) VALUES (?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {
                insertStmt.setString(1, code);
                insertStmt.setString(2, user);
                insertStmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                insertStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[HISTORY_LOG_FAIL] Could not log to history: " + e.getMessage());
        }
    }

    // 🕒 Archive history older than 30 days
    @Scheduled(cron = "0 0 2 * * ?")
    public void archiveOldHistory() {
        System.out.println("[ARCHIVE_JOB] Starting archive...");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String selectSQL = "SELECT * FROM history WHERE created_in < NOW() - INTERVAL '30 days'";
            String insertSQL = "INSERT INTO archive_history (generated_code, created_by, created_in) VALUES (?, ?, ?)";
            String deleteSQL = "DELETE FROM history WHERE created_in < NOW() - INTERVAL '30 days'";

            int archivedCount = 0;

            try (PreparedStatement selectStmt = conn.prepareStatement(selectSQL);
                 ResultSet rs = selectStmt.executeQuery()) {

                while (rs.next()) {
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {
                        insertStmt.setString(1, rs.getString("generated_code"));
                        insertStmt.setString(2, rs.getString("created_by"));
                        insertStmt.setTimestamp(3, rs.getTimestamp("created_in"));
                        if (insertStmt.executeUpdate() > 0) archivedCount++;
                    }
                }
            }

            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSQL)) {
                int deleted = deleteStmt.executeUpdate();
                System.out.println("[ARCHIVE_JOB] ✅ Archived: " + archivedCount + ", Deleted: " + deleted);
            }

        } catch (SQLException e) {
            System.err.println("[ARCHIVE_ERROR] ❌ SQL: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[ARCHIVE_ERROR] ❌ General: " + e.getMessage());
        }
    }
}
