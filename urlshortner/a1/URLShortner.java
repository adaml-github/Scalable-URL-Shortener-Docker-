import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import redis.clients.jedis.Jedis; 
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class URLShortner { 
  
  static final File WEB_ROOT = new File(".");
  static final String DEFAULT_FILE = "index.html";
  static final String FILE_NOT_FOUND = "404.html";
  static final String METHOD_NOT_SUPPORTED = "not_supported.html";
  static final String REDIRECT_RECORDED = "redirect_recorded.html";
  static final String REDIRECT = "redirect.html";
  static final String NOT_FOUND = "notfound.html";
  static final String DATABASE = "database.txt";
  // port to listen connection
  static final int PORT = 8080;
  private final String POSTGRES_URL = "jdbc:postgresql://postgres:5432/app";
  private final String POSTGRES_UPASS = "root";
  
  private Jedis jedis;
  private Connection postgres;
  // verbose mode
  static final boolean verbose = false;
  
  URLShortner(){
    //Connecting to Redis server 
    this.jedis = new Jedis("redis"); 
    System.out.println("Connected to Redis");
    while (true) {
      try {
        postgres = DriverManager.getConnection(POSTGRES_URL, POSTGRES_UPASS, POSTGRES_UPASS);
        System.out.println("Connected to the PostgreSQL server successfully.");
        
        Statement statement = postgres.createStatement();
        statement.execute(URLShortnerTable.CREATE_TABLE);
        System.out.println("URLShortner Table created");
        break;
      } catch (SQLException e) {
          System.out.println("failed to connect postgres: "+e.getMessage());
      }
     
    }
  }

  public static void main(String[] args) throws Exception {
    try {
      ServerSocket serverConnect = new ServerSocket(PORT);
      System.out.println("Server started.\nListening for connections on port : " + PORT + " ...\n");
      
      URLShortner urlShortner = new URLShortner();
      
      // we listen until user halts server execution
      while (true) {
        if (verbose) { System.out.println("Connecton opened. (" + new Date() + ")"); }
        urlShortner.handle(serverConnect.accept());
      }
    } catch (IOException e) {
      System.err.println("Server Connection error : " + e.getMessage());
    } catch (Exception e) {
      System.err.println("Restarting.... " + e);
    }
  }

  public void handle(Socket connect) {
    BufferedReader in = null; PrintWriter out = null; BufferedOutputStream dataOut = null;
    
    try {
      in = new BufferedReader(new InputStreamReader(connect.getInputStream()));
      out = new PrintWriter(connect.getOutputStream());
      dataOut = new BufferedOutputStream(connect.getOutputStream());
      
      String input = in.readLine();
      
      if(verbose)System.out.println("first line: "+input);
      Pattern pput = Pattern.compile("^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$");
      Matcher mput = pput.matcher(input);
      if(mput.matches()){
        String shortResource=mput.group(1);
        String longResource=mput.group(2);
        String httpVersion=mput.group(3);

        save(shortResource, longResource);
        
        File file = new File(WEB_ROOT, REDIRECT_RECORDED);
        int fileLength = (int) file.length();
        String contentMimeType = "text/html";
        //read content to return to client
        byte[] fileData = readFileData(file, fileLength);
          
        out.println("HTTP/1.1 200 OK");
        out.println("Server: Java HTTP Server/Shortner : 1.0");
        out.println("Date: " + new Date());
        out.println("Content-type: " + contentMimeType);
        out.println("Content-length: " + fileLength);
        out.println(); 
        out.flush(); 

        dataOut.write(fileData, 0, fileLength);
        dataOut.flush();
      } else {
        Pattern pget = Pattern.compile("^(\\S+)\\s+/(\\S+)\\s+(\\S+)$");
        Matcher mget = pget.matcher(input);
        if(mget.matches()){
          String method=mget.group(1);
          String shortResource=mget.group(2);
          String httpVersion=mget.group(3);

          String longResource = find(shortResource);
          
          if(longResource!=null){
            File file = new File(WEB_ROOT, REDIRECT);
            int fileLength = (int) file.length();
            String contentMimeType = "text/html";
  
            //read content to return to client
            byte[] fileData = readFileData(file, fileLength);
            
            // out.println("HTTP/1.1 301 Moved Permanently");
            out.println("HTTP/1.1 307 Temporary Redirect");
            out.println("Location: "+longResource);
            out.println("Server: Java HTTP Server/Shortner : 1.0");
            out.println("Date: " + new Date());
            out.println("Content-type: " + contentMimeType);
            out.println("Content-length: " + fileLength);
            out.println(); 
            out.flush(); 
  
            dataOut.write(fileData, 0, fileLength);
            dataOut.flush();
          } else {
            File file = new File(WEB_ROOT, FILE_NOT_FOUND);
            int fileLength = (int) file.length();
            String content = "text/html";
            byte[] fileData = readFileData(file, fileLength);
            
            out.println("HTTP/1.1 404 File Not Found");
            out.println("Server: Java HTTP Server/Shortner : 1.0");
            out.println("Date: " + new Date());
            out.println("Content-type: " + content);
            out.println("Content-length: " + fileLength);
            out.println(); 
            out.flush(); 
            
            dataOut.write(fileData, 0, fileLength);
            dataOut.flush();
          }
        }
      }
    } catch (Exception e) {
      System.out.println("Server error :( " + e);
    } finally {
      try {
        in.close();
        out.close();
        connect.close(); // we close socket connection
      } catch (Exception e) {
        System.err.println("Error closing stream : " + e.getMessage());
      } 
      
      if (verbose) {
        System.out.println("Connection closed.\n");
      }
    }
  }

  private  String find(String shortURL){
    String longURL =null;
    if (jedis.exists(shortURL)) {
      System.out.println("Found in redis: " + shortURL + " " + jedis.get(shortURL));
      return jedis.get(shortURL);
    }

    String sqlGet = "SELECT " + URLShortnerTable.COLUMN_LONG + " FROM " + URLShortnerTable.TABLE_NAME +" WHERE " 
    +URLShortnerTable.COLUMN_SHORT +" = '" + shortURL + "'";
    try (Statement stmt = postgres.createStatement()) {
      ResultSet result = stmt.executeQuery(sqlGet);
      while (result.next()) {
          longURL = result.getString(URLShortnerTable.COLUMN_LONG);
      }
    } catch (Exception e) {
      System.err.println(sqlGet);
      System.err.println("Could not retrieve from Postgres: "+e);
    }
    System.out.println("Found in postgres: "+longURL);
    return longURL;
  }

  private void save(String shortURL,String longURL){
    jedis.set(shortURL, longURL);
    try {
      URLShortnerTable.insert(postgres, shortURL, longURL);
    } catch (SQLException e) {
      System.err.println("Error inserting into DB: " +e);
    }
    return;
  }
  
  private static byte[] readFileData(File file, int fileLength) throws IOException {
    FileInputStream fileIn = null;
    byte[] fileData = new byte[fileLength];
    
    try {
      fileIn = new FileInputStream(file);
      fileIn.read(fileData);
    } finally {
      if (fileIn != null) 
        fileIn.close();
    }
    
    return fileData;
  }
  
  public static class URLShortnerTable {
    public static String TABLE_NAME = "UrlShortner";
    public static String COLUMN_SHORT = "short";
    public static String COLUMN_LONG = "long";

    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
            COLUMN_SHORT + " varchar(100)," +
            COLUMN_LONG + " varchar(100)" +
            ")";
    public static final String DELETE_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;

    public static void insert(Connection connection, String shortURL, String longURL) throws SQLException {
        String sqlInsert = "INSERT INTO " + TABLE_NAME + "(" + COLUMN_SHORT + ", " + COLUMN_LONG + ") VALUES(?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sqlInsert)) {
            ps.setString(1, shortURL);
            ps.setString(2, longURL);
            ps.execute();
        }
    }
}
  
}
