
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = messageID + recipient + messageText + date + time;
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if(hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 12); // Shorten for display
        } catch (Exception ex) {
            return "HASH_ERR";
        }
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
    private static final String USERS_FILE = "users.json";
    private static final String MESSAGES_FILE = "messages.json";
    private static User currentUser = null;
    private static List<User> users = new ArrayList<>();
    private static List<Message> messages = new ArrayList<>();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        loadUsers();
        loadMessages();
        while (true) {
            String menu = "Main Menu:\n1. Register\n2. Login\n3. Exit\nEnter your choice (1-3):";
            String input = JOptionPane.showInputDialog(menu);
            if (input == null) break;
            switch (input) {
                case "1":
                    registerUser();
                    break;
                case "2":
                    if (loginUser()) {
                        userMenu();
                    }
                    break;
                case "3":
                    JOptionPane.showMessageDialog(null, "Goodbye!");
                    saveUsers();
                    saveMessages();
                    return;
                default:
                    JOptionPane.showMessageDialog(null, "Invalid choice. Please enter 1, 2, or 3.");
            }
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

        // Check for duplicate username
        for (User u : users) {
            if (u.getUsername().equals(username)) {
                JOptionPane.showMessageDialog(null, "Username already exists. Please choose another.");
                return;
            }
        }

        users.add(new User(username, password, cell));
        saveUsers();
        JOptionPane.showMessageDialog(null, "Registration successful! You can now log in.");
    }

    private static boolean loginUser() {
        String username = JOptionPane.showInputDialog("Login - Enter username:");
        if (username == null) return false;
        String password = JOptionPane.showInputDialog("Login - Enter password:");
        if (password == null) return false;

        for (User u : users) {
            if (u.getUsername().equals(username) && u.getPassword().equals(password)) {
                currentUser = u;
                JOptionPane.showMessageDialog(null, "Welcome " + currentUser.getUsername() + ", it is great to see you again.");
                return true;
            }
        }
        JOptionPane.showMessageDialog(null, "Username or password incorrect, please try again.");
        return false;
    }

    private static void userMenu() {
        while (true) {
            String menu = "User Menu:\n1. Send Message(s)\n2. View Messages\n3. Logout\nEnter your choice (1-3):";
            String input = JOptionPane.showInputDialog(menu);
            if (input == null) return;
            switch (input) {
                case "1":
                    sendMessages();
                    break;
                case "2":
                    showAllMessages();
                    break;
                case "3":
                    JOptionPane.showMessageDialog(null, "Logged out.");
                    currentUser = null;
                    return;
                default:
                    JOptionPane.showMessageDialog(null, "Invalid choice. Please enter 1, 2, or 3.");
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

            // Save to JSON
            messages.add(m);
        }
        saveMessages();
        JOptionPane.showMessageDialog(null, "Total messages sent: " + Message.getMessageCount() + "\nMessages saved to JSON file.");
    }

    private static void showAllMessages() {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Message m : messages) {
            sb.append("Message ID: ").append(m.getMessageID()).append("\n");
            sb.append("Recipient: ").append(m.getRecipient()).append("\n");
            sb.append("Message: ").append(m.getMessageText()).append("\n");
            sb.append("Hash: ").append(m.getMessageHash()).append("\n");
            sb.append("Date: ").append(m.getDate()).append("\n");
            sb.append("Time: ").append(m.getTime()).append("\n");
            sb.append("--------------------------------------------------\n");
            count++;
        }
        if (count == 0) {
            sb.append("No messages found.");
        }
        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new java.awt.Dimension(600, 400));
        JOptionPane.showMessageDialog(null, scrollPane, "All Messages", JOptionPane.INFORMATION_MESSAGE);
    }

    // JSON persistence
    private static void saveUsers() {
        try (Writer writer = Files.newBufferedWriter(Paths.get(USERS_FILE))) {
            gson.toJson(users, writer);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error saving users: " + e.getMessage());
        }
    }

    private static void loadUsers() {
        try (Reader reader = Files.newBufferedReader(Paths.get(USERS_FILE))) {
            users = gson.fromJson(reader, new TypeToken<List<User>>(){}.getType());
            if (users == null) users = new ArrayList<>();
        } catch (IOException e) {
            users = new ArrayList<>();
        }
    }

    private static void saveMessages() {
        try (Writer writer = Files.newBufferedWriter(Paths.get(MESSAGES_FILE))) {
            gson.toJson(messages, writer);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error saving messages: " + e.getMessage());
        }
    }

    private static void loadMessages() {
        try (Reader reader = Files.newBufferedReader(Paths.get(MESSAGES_FILE))) {
            messages = gson.fromJson(reader, new TypeToken<List<Message>>(){}.getType());
            if (messages == null) messages = new ArrayList<>();
        } catch (IOException e) {
            messages = new ArrayList<>();
        }
    }
}