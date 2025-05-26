import javax.swing.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

class User {
    private final String username;
    private final String password;
    private final String cellNumber;

    public User(String username, String password, String cellNumber) {
        this.username = username;
        this.password = password;
        this.cellNumber = cellNumber;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getCellNumber() { return cellNumber; }

    public static boolean isValidUsername(String username) {
        return username.contains("_") && username.length() <= 5;
    }

    public static boolean isValidPassword(String password) {
        if (password.length() < 8) return false;
        if (!password.matches(".*[A-Z].*")) return false;
        if (!password.matches(".*[0-9].*")) return false;
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) return false;
        return true;
    }

    public static boolean isValidCellNumber(String cell) {
        return cell.matches("^\\+27\\d{9,10}$");
    }
}

final class Message {
    private static int messageCount = 0;
    private final String messageID;
    private final String recipient;
    private final String messageText;
    private final String messageHash;
    private final String date;
    private final String time;

    public Message(String recipient, String messageText) {
        this.messageID = generateMessageID();
        this.recipient = recipient;
        this.messageText = messageText;
        this.messageHash = createMessageHash();
        LocalDateTime now = LocalDateTime.now();
        this.date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        this.time = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        messageCount++;
    }

    private String generateMessageID() {
        return String.format("%010d", new java.util.Random().nextInt(1000000000));
    }

    public static boolean checkRecipientCell(String cell) {
        return User.isValidCellNumber(cell);
    }

    public String createMessageHash() {
        String idPart = messageID.substring(0, 2);
        String msgPart = messageText.length() > 2
            ? messageText.substring(0, 1).toUpperCase() + messageText.substring(messageText.length() - 1).toUpperCase()
            : messageText.toUpperCase();
        return idPart + ":" + msgPart;
    }

    public static boolean isValidMessage(String msg) {
        return msg.length() <= 250;
    }

    public String getMessageID() { return messageID; }
    public String getRecipient() { return recipient; }
    public String getMessageText() { return messageText; }
    public String getMessageHash() { return messageHash; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public static int getMessageCount() { return messageCount; }
}

public class MainApp {
    private static final String DB_URL = "jdbc:sqlite:messages.db";
    private static User currentUser = null;

    public static void main(String[] args) {
        setupDatabase();
        while (true) {
            String[] mainOptions = {"Register", "Login", "Exit"};
            int mainChoice = JOptionPane.showOptionDialog(null, "Welcome! Please choose an option:",
                    "Main Menu", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, mainOptions, mainOptions[0]);
            if (mainChoice == 0) {
                registerUser();
            } else if (mainChoice == 1) {
                if (loginUser()) {
                    userMenu();
                }
            } else {
                JOptionPane.showMessageDialog(null, "Goodbye!");
                break;
            }
        }
    }

    // --- SCHEMA MIGRATION LOGIC ---
    private static void setupDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Create users table if not exists
            String sqlUsers = "CREATE TABLE IF NOT EXISTS users (" +
                    "username TEXT PRIMARY KEY," +
                    "password TEXT," +
                    "cell TEXT" +
                    ")";
            Statement stmt = conn.createStatement();
            stmt.execute(sqlUsers);

            // Create messages table if not exists (with only id column, will migrate below)
            String sqlMessages = "CREATE TABLE IF NOT EXISTS messages (id TEXT PRIMARY KEY)";
            stmt.execute(sqlMessages);

            // Get existing columns
            Set<String> columns = new HashSet<>();
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(messages)");
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }

            // Add missing columns
            String[] needed = {"recipient TEXT", "message TEXT", "hash TEXT", "date TEXT", "time TEXT"};
            String[] colNames = {"recipient", "message", "hash", "date", "time"};
            for (int i = 0; i < needed.length; i++) {
                if (!columns.contains(colNames[i])) {
                    stmt.execute("ALTER TABLE messages ADD COLUMN " + needed[i]);
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database setup error: " + e.getMessage());
        }
    }

    private static void registerUser() {
        String username;
        while (true) {
            username = JOptionPane.showInputDialog("Enter username:");
            if (username == null) return;
            if (User.isValidUsername(username)) {
                JOptionPane.showMessageDialog(null, "Username successfully captured.");
                break;
            } else {
                JOptionPane.showMessageDialog(null, "Username is not correctly formatted, please ensure that your username contains an underscore and is no more than five characters in length.");
            }
        }

        String password;
        while (true) {
            password = JOptionPane.showInputDialog("Enter password:");
            if (password == null) return;
            if (User.isValidPassword(password)) {
                JOptionPane.showMessageDialog(null, "Password successfully captured.");
                break;
            } else {
                JOptionPane.showMessageDialog(null, "Password is not correctly formatted; please ensure that the password contains at least eight characters, a capital letter, a number, and a special character.");
            }
        }

        String cell;
        while (true) {
            cell = JOptionPane.showInputDialog("Enter cell number (e.g. +27839868976):");
            if (cell == null) return;
            if (User.isValidCellNumber(cell)) {
                JOptionPane.showMessageDialog(null, "Cell phone number successfully captured.");
                break;
            } else {
                JOptionPane.showMessageDialog(null, "Cell number is incorrectly formatted or does not contain international code, please correct the number and try again.");
            }
        }

        // Save user to DB
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT INTO users (username, password, cell) VALUES (?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, cell);
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(null, "Registration successful! You can now log in.");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Registration failed: " + e.getMessage());
        }
    }

    private static boolean loginUser() {
        String username = JOptionPane.showInputDialog("Login - Enter username:");
        if (username == null) return false;
        String password = JOptionPane.showInputDialog("Login - Enter password:");
        if (password == null) return false;

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                currentUser = new User(rs.getString("username"), rs.getString("password"), rs.getString("cell"));
                JOptionPane.showMessageDialog(null, "Welcome " + currentUser.getUsername() + ", it is great to see you again.");
                return true;
            } else {
                JOptionPane.showMessageDialog(null, "Username or password incorrect, please try again.");
                return false;
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Login failed: " + e.getMessage());
            return false;
        }
    }

    private static void userMenu() {
        while (true) {
            String[] userOptions = {"Send Message(s)", "View Messages", "Logout"};
            int userChoice = JOptionPane.showOptionDialog(null, "Welcome, " + currentUser.getUsername() + "! Choose an option:",
                    "User Menu", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, userOptions, userOptions[0]);
            if (userChoice == 0) {
                sendMessages();
            } else if (userChoice == 1) {
                showAllMessages();
            } else {
                JOptionPane.showMessageDialog(null, "Logged out.");
                currentUser = null;
                break;
            }
        }
    }

    private static void sendMessages() {
        int numMessages = 0;
        while (true) {
            try {
                String numStr = JOptionPane.showInputDialog("How many messages do you want to send?");
                if (numStr == null) return;
                numMessages = Integer.parseInt(numStr);
                if (numMessages > 0) break;
            } catch (Exception e) {
                // ignore and re-prompt
            }
        }

        for (int i = 0; i < numMessages; i++) {
            String rec;
            while (true) {
                rec = JOptionPane.showInputDialog("Enter recipient cell number (e.g. +27839868976):");
                if (rec == null) return;
                if (Message.checkRecipientCell(rec)) {
                    JOptionPane.showMessageDialog(null, "Cell phone number successfully captured.");
                    break;
                } else {
                    JOptionPane.showMessageDialog(null, "Cell phone number incorrectly formatted or does not contain international code. Please correct the number and try again.");
                }
            }

            String msg;
            while (true) {
                msg = JOptionPane.showInputDialog("Enter message (max 250 chars):");
                if (msg == null) return;
                if (Message.isValidMessage(msg)) {
                    JOptionPane.showMessageDialog(null, "Message ready to send.");
                    break;
                } else {
                    JOptionPane.showMessageDialog(null, "Message exceeds 250 characters, please reduce size.");
                }
            }

            Message m = new Message(rec, msg);

            // Show message details
            JOptionPane.showMessageDialog(null,
                "Message ID: " + m.getMessageID() +
                "\nRecipient: " + m.getRecipient() +
                "\nMessage: " + m.getMessageText() +
                "\nMessage Hash: " + m.getMessageHash() +
                "\nDate: " + m.getDate() +
                "\nTime: " + m.getTime()
            );

            // Save to DB
            saveMessageToDB(m);
        }

        JOptionPane.showMessageDialog(null, "Total messages sent: " + Message.getMessageCount() + "\nMessages saved to SQLite database.");
    }

    private static void saveMessageToDB(Message m) {
        String sql = "INSERT INTO messages (id, recipient, message, hash, date, time) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, m.getMessageID());
            pstmt.setString(2, m.getRecipient());
            pstmt.setString(3, m.getMessageText());
            pstmt.setString(4, m.getMessageHash());
            pstmt.setString(5, m.getDate());
            pstmt.setString(6, m.getTime());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Error saving message: " + e.getMessage());
        }
    }

    private static void showAllMessages() {
        StringBuilder sb = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM messages");
            int count = 0;
            while (rs.next()) {
                sb.append("Message ID: ").append(rs.getString("id")).append("\n");
                sb.append("Recipient: ").append(rs.getString("recipient")).append("\n");
                sb.append("Message: ").append(rs.getString("message")).append("\n");
                sb.append("Hash: ").append(rs.getString("hash")).append("\n");
                sb.append("Date: ").append(rs.getString("date")).append("\n");
                sb.append("Time: ").append(rs.getString("time")).append("\n");
                sb.append("--------------------------------------------------\n");
                count++;
            }
            if (count == 0) {
                sb.append("No messages found in the database.");
            }
        } catch (SQLException e) {
            sb.append("Error reading messages: ").append(e.getMessage());
        }
        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new java.awt.Dimension(600, 400));
        JOptionPane.showMessageDialog(null, scrollPane, "All Messages", JOptionPane.INFORMATION_MESSAGE);
    }
}