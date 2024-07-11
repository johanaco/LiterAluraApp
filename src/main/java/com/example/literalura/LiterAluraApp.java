package com.example.literalura;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.sql.*;
import java.util.Scanner;

public class LiterAluraApp {
    private static final String API_URL = "https://openlibrary.org/search.json";
    private static final String DB_URL = "jdbc:sqlite:literalura.db";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            if (conn != null) {
                createNewDatabase(conn);
                createNewTables(conn);
                showMenu(conn);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createNewDatabase(Connection conn) {
        try {
            if (conn != null) {
                DatabaseMetaData meta = conn.getMetaData();
                System.out.println("The driver name is " + meta.getDriverName());
                System.out.println("A new database has been created.");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void createNewTables(Connection conn) {
        String createBooksTable = "CREATE TABLE IF NOT EXISTS books (\n"
                + " id INTEGER PRIMARY KEY,\n"
                + " title TEXT NOT NULL,\n"
                + " author TEXT NOT NULL,\n"
                + " language TEXT,\n"
                + " year INTEGER,\n"
                + " search_count INTEGER DEFAULT 0\n"
                + ");";

        String createAuthorsTable = "CREATE TABLE IF NOT EXISTS authors (\n"
                + " id INTEGER PRIMARY KEY,\n"
                + " name TEXT NOT NULL,\n"
                + " birth_date TEXT,\n"
                + " death_date TEXT\n"
                + ");";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createBooksTable);
            stmt.execute(createAuthorsTable);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void showMenu(Connection conn) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nMenu:");
            System.out.println("1. Buscar libros por título");
            System.out.println("2. Buscar autores por nombre");
            System.out.println("3. Listar libros registrados");
            System.out.println("4. Listar autores registrados");
            System.out.println("5. Listar autores vivos");
            System.out.println("6. Listar libros por idioma");
            System.out.println("7. Listar autores por año");
            System.out.println("8. Mostrar los 10 libros más buscados");
            System.out.println("9. Generar estadísticas");
            System.out.println("10. Salir");
            System.out.print("Elige una opción: ");
            int choice = scanner.nextInt();
            scanner.nextLine();  // Consume newline

            try {
                switch (choice) {
                    case 1:
                        System.out.print("Introduce el título del libro: ");
                        String title = scanner.nextLine();
                        fetchBooksFromApiAndSaveToDb(conn, "title=" + title);
                        break;
                    case 2:
                        System.out.print("Introduce el nombre del autor: ");
                        String author = scanner.nextLine();
                        fetchBooksFromApiAndSaveToDb(conn, "author=" + author);
                        break;
                    case 3:
                        listAllBooks(conn);
                        break;
                    case 4:
                        listAllAuthors(conn);
                        break;
                    case 5:
                        listLivingAuthors(conn);
                        break;
                    case 6:
                        System.out.print("Introduce el idioma del libro: ");
                        String language = scanner.nextLine();
                        listBooksByLanguage(conn, language);
                        break;
                    case 7:
                        System.out.print("Introduce el año: ");
                        int year = scanner.nextInt();
                        listAuthorsByYear(conn, year);
                        break;
                    case 8:
                        listTop10Books(conn);
                        break;
                    case 9:
                        generateStatistics(conn);
                        break;
                    case 10:
                        System.out.println("Saliendo...");
                        return;
                    default:
                        System.out.println("Opción no válida.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void fetchBooksFromApiAndSaveToDb(Connection conn, String query) throws Exception {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet request = new HttpGet(API_URL + "?q=" + query);
        CloseableHttpResponse response = httpClient.execute(request);
        String jsonResponse = EntityUtils.toString(response.getEntity());

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode docs = objectMapper.readTree(jsonResponse).get("docs");

        String insertBookSql = "INSERT INTO books(title, author, language, year) VALUES(?,?,?,?)";
        String insertAuthorSql = "INSERT INTO authors(name) VALUES(?) ON CONFLICT(name) DO NOTHING";

        for (JsonNode doc : docs) {
            String title = doc.get("title").asText();
            String author = doc.has("author_name") ? doc.get("author_name").get(0).asText() : "Unknown";
            String language = doc.has("language") ? doc.get("language").get(0).asText() : "Unknown";
            int year = doc.has("first_publish_year") ? doc.get("first_publish_year").asInt() : 0;

            try (PreparedStatement bookPstmt = conn.prepareStatement(insertBookSql);
                 PreparedStatement authorPstmt = conn.prepareStatement(insertAuthorSql)) {
                bookPstmt.setString(1, title);
                bookPstmt.setString(2, author);
                bookPstmt.setString(3, language);
                bookPstmt.setInt(4, year);
                bookPstmt.executeUpdate();

                authorPstmt.setString(1, author);
                authorPstmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
    }


    private static void listAllBooks(Connection conn) {
        String sql = "SELECT id, title, author FROM books";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                System.out.println(rs.getInt("id") + "\t" +
                        rs.getString("title") + "\t" +
                        rs.getString("author"));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
    private static void listAllAuthors(Connection conn) {
        String sql = "SELECT id, name FROM authors";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                System.out.println(rs.getInt("id") + "\t" +
                        rs.getString("name"));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
    private static void listLivingAuthors(Connection conn) {
        String sql = "SELECT id, name FROM authors WHERE death_date IS NULL";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                System.out.println(rs.getInt("id") + "\t" +
                        rs.getString("name"));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
    private static void listBooksByLanguage(Connection conn, String language) {
        String sql = "SELECT id, title, author FROM books WHERE language = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, language);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                System.out.println(rs.getInt("id") + "\t" +
                        rs.getString("title") + "\t" +
                        rs.getString("author"));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
    private static void listAuthorsByYear(Connection conn, int year) {
        String sql = "SELECT id, name FROM authors WHERE strftime('%Y', birth_date) = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(year));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                System.out.println(rs.getInt("id") + "\t" +
                        rs.getString("name"));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
    private static void listTop10Books(Connection conn) {
        String sql = "SELECT id, title, author, search_count FROM books ORDER BY search_count DESC LIMIT 10";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                System.out.println(rs.getInt("id") + "\t" +
                        rs.getString("title") + "\t" +
                        rs.getString("author") + "\t" +
                        rs.getInt("search_count"));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void generateStatistics(Connection conn) {
        String sql = "SELECT COUNT(*) AS total_books, COUNT(DISTINCT author) AS total_authors FROM books";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                System.out.println("Total de libros: " + rs.getInt("total_books"));
                System.out.println("Total de autores: " + rs.getInt("total_authors"));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

}

