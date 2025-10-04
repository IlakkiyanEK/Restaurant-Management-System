package mySQL;
import java.sql.*;
import java.util.*;

public class restaurent {
    private static Connection conn = DBConnection.getConnection();
    private static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        if (conn == null) {
            System.out.println("❌ Cannot connect to database. Exiting...");
            return;
        }

        int choice;
        do {
            System.out.println("\n--- 🍴 Restaurant Management System ---");
            System.out.println("1. View Menu");
            System.out.println("2. Add Menu Item");
            System.out.println("3. Edit Menu Item");
            System.out.println("4. Take Order");
            System.out.println("5. View Daily Sales");
            System.out.println("0. Exit");
            System.out.print("Enter your choice: ");
            choice = sc.nextInt();
            sc.nextLine(); // consume newline

            switch (choice) {
                case 1 -> viewMenu();
                case 2 -> addMenuItem();
                case 3 -> editMenuItem();
                case 4 -> takeOrder();
                case 5 -> viewDailySales();
                case 0 -> System.out.println("✅ Exiting...");
                default -> System.out.println("Invalid choice! Try again.");
            }
        } while (choice != 0);
    }

    private static void viewMenu() {
        String sql = "SELECT * FROM menu";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            System.out.println("\n--- MENU ---");
            while (rs.next()) {
                System.out.printf("%d. %s - ₹%.2f%n",
                        rs.getInt("item_id"),
                        rs.getString("name"),
                        rs.getDouble("price"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void addMenuItem() {
        System.out.print("Enter item name: ");
        String name = sc.nextLine();
        System.out.print("Enter item price: ");
        double price = sc.nextDouble();

        String sql = "INSERT INTO menu (name, price) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setDouble(2, price);
            ps.executeUpdate();
            System.out.println("✅ Menu item added successfully!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void editMenuItem() {
        viewMenu();
        System.out.print("Enter item ID to edit: ");
        int id = sc.nextInt();
        sc.nextLine();

        System.out.print("Enter new item name: ");
        String name = sc.nextLine();
        System.out.print("Enter new item price: ");
        double price = sc.nextDouble();

        String sql = "UPDATE menu SET name=?, price=? WHERE item_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setDouble(2, price);
            ps.setInt(3, id);
            ps.executeUpdate();
            System.out.println("✅ Menu item updated successfully!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void takeOrder() {
        Map<Integer, Integer> orderItems = new HashMap<>();
        String choice;

        do {
            viewMenu();
            System.out.print("Enter item ID to order: ");
            int id = sc.nextInt();
            System.out.print("Enter quantity: ");
            int qty = sc.nextInt();
            orderItems.put(id, orderItems.getOrDefault(id, 0) + qty);

            System.out.print("Add more items? (y/n): ");
            choice = sc.next();
        } while (choice.equalsIgnoreCase("y"));

        try {
            conn.setAutoCommit(false);

            // 1️⃣ Create a new order
            String insertOrderSQL = "INSERT INTO orders (order_date) VALUES (NOW())";
            PreparedStatement psOrder = conn.prepareStatement(insertOrderSQL, Statement.RETURN_GENERATED_KEYS);
            psOrder.executeUpdate();
            ResultSet rs = psOrder.getGeneratedKeys();
            rs.next();
            int orderId = rs.getInt(1);

            double grandTotal = 0;

            // 2️⃣ Insert each ordered item
            for (Map.Entry<Integer, Integer> entry : orderItems.entrySet()) {
                int itemId = entry.getKey();
                int qty = entry.getValue();

                String fetchPrice = "SELECT name, price FROM menu WHERE item_id=?";
                PreparedStatement psFetch = conn.prepareStatement(fetchPrice);
                psFetch.setInt(1, itemId);
                ResultSet rsItem = psFetch.executeQuery();

                if (rsItem.next()) {
                    String name = rsItem.getString("name");
                    double price = rsItem.getDouble("price");
                    double total = price * qty;
                    grandTotal += total;

                    String insertItemSQL = "INSERT INTO order_items (order_id, item_id, quantity, total_price) VALUES (?, ?, ?, ?)";
                    PreparedStatement psItem = conn.prepareStatement(insertItemSQL);
                    psItem.setInt(1, orderId);
                    psItem.setInt(2, itemId);
                    psItem.setInt(3, qty);
                    psItem.setDouble(4, total);
                    psItem.executeUpdate();

                    System.out.printf("%s x %d = ₹%.2f%n", name, qty, total);
                }
            }

            System.out.printf("💰 Grand Total: ₹%.2f%n", grandTotal);
            conn.commit();
            conn.setAutoCommit(true);

        } catch (SQLException e) {
            try {
                conn.rollback();
                System.out.println("❌ Transaction rolled back due to error.");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    private static void viewDailySales() {
        String sql = """
            SELECT SUM(oi.total_price) AS total_sales
            FROM order_items oi
            JOIN orders o ON oi.order_id = o.order_id
            WHERE DATE(o.order_date) = CURDATE()
        """;

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next() && rs.getDouble("total_sales") > 0) {
                System.out.printf("📊 Today's total sales: ₹%.2f%n", rs.getDouble("total_sales"));
            } else {
                System.out.println("No sales recorded today.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
