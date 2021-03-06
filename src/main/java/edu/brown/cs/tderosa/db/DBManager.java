package edu.brown.cs.tderosa.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.brown.cs.tderosa.livingcity.LatLng;
import edu.brown.cs.tderosa.livingcity.Place;
import edu.brown.cs.tderosa.livingcity.Story;
import edu.brown.cs.tderosa.utils.CSV;
import edu.brown.cs.tderosa.utils.RandomString;

public class DBManager {
  private String db;
  private Connection conn;
  private Map<String, Place> placeCache;
  private Map<String, Story> storyCache;
  private RandomString randomString;

  /**
   * Constructor for database manager.
   * 
   * @param db Path to the database.
   * @throws ClassNotFoundException throws a class not found exception
   * @throws SQLException throws a sql exception
   */
  public DBManager(String db, String placeCSV) throws ClassNotFoundException, SQLException {
    this.db = db;
    setUpDB();
//    updatePlaces(placeCSV);
    placeCache = getPlaces();
    storyCache = getAllTextStories();
    randomString = new RandomString(5);
  }

  /**
   * Accesses the database.
   * 
   * @throws ClassNotFoundException throws a class not found exception
   * @throws SQLException throws a sql exception
   */
  public void setUpDB() throws ClassNotFoundException, SQLException {
    Class.forName("org.sqlite.JDBC");
    String urlToDB = "jdbc:sqlite:" + db;
    conn = DriverManager.getConnection(urlToDB);
    Statement stat = conn.createStatement();
    stat.executeUpdate("PRAGMA foreign_keys = ON;");
    stat.close();
  }

  /**
   * Closes the connection.
   * 
   * @throws SQLException throws a sql exception
   */
  public void close() throws SQLException {
    conn.close();
  }
  
  public void updatePlaces(String placeFile) throws SQLException {
    Statement stat = conn.createStatement();
    stat.executeUpdate("PRAGMA foreign_keys = ON;");
    stat.execute("DROP TABLE IF EXISTS picture");
    stat.close();
    
    CSV reader = new CSV(placeFile);
    List<String[]> rows = reader.parse();
    
    String query = "INSERT OR IGNORE INTO 'place' VALUES(?,?,?,?,?,?);";
    PreparedStatement prep = conn.prepareStatement(query);
    
    for (String[] row: rows) {
     prep.setString(1, row[0]);
     prep.setString(2, row[1]);
     prep.setString(3, row[2]);
     prep.setString(4, row[3]);
     prep.setDouble(5, Double.parseDouble(row[4]));
     prep.setDouble(6, Double.parseDouble(row[5]));
     prep.addBatch();
    }
    
    prep.executeBatch();
    prep.close();
  }
  
  public Collection<Place> places() {
    Collection<Place> p = placeCache.values();
    return p;
  }

  public Place getPlaceById(String id) throws SQLException {
    Place place = placeCache.get(id);
    if (place == null) {
      String query = "SELECT * FROM place WHERE id = ?;";
      PreparedStatement prep = conn.prepareStatement(query);
      prep.setString(1, id);
      
      String name, intro, pic_path;
      ResultSet rs = prep.executeQuery();
      List<Place> places = new ArrayList<Place>();
      if (rs == null) {
        return null;
      }

      while (rs.next()) {
        name = rs.getString(2);
        intro = rs.getString(3);
        pic_path = rs.getString(4);
        LatLng point = new LatLng(rs.getDouble(5), rs.getDouble(6));
        place = new Place(id, name, intro, pic_path, point);
        places.add(place);
        placeCache.put(id, place);
      }
      rs.close();
      prep.close();
    }
    
    return place;
  }
  
  public Map<String, Place> getPlaces() throws SQLException {
    String query = "SELECT * FROM place;";
    PreparedStatement prep = conn.prepareStatement(query);
    
    ResultSet rs = prep.executeQuery();
    Map<String, Place> places = new HashMap<String, Place>();
    if (rs == null) {
      return null;
    }

    String id, name, intro, pic_path;
    while (rs.next()) {
      id = rs.getString(1);
      name = rs.getString(2);
      intro = rs.getString(3);
      pic_path = rs.getString(4);
      LatLng point = new LatLng(rs.getDouble(5), rs.getDouble(6));
      Place place = new Place(id, name, intro, pic_path, point);
      places.put(id, place);
    }

    rs.close();
    prep.close();

    return places;
  }
  
  public Story insertTextStory(Place p, String date, String author, String authorAbt, String text) throws SQLException {
    String storyID = randomString.nextString();
    while (storyCache.containsKey(storyID)) {
      storyID = randomString.nextString();
    }
    
    
    String query = "INSERT OR IGNORE INTO 'story' VALUES(?,?,?,?,?,?);";
      
    PreparedStatement prep = conn.prepareStatement(query);
    prep.setString(1, storyID);
    prep.setString(2, date);
    prep.setString(3, author);
    prep.setString(4, authorAbt);
    prep.setString(5, text);
    prep.setString(6, p.id());
    
    prep.addBatch();
    prep.executeBatch();
    prep.close();
    String[] dateArray = date.split("-");
    
    Story story = new Story(storyID, dateArray, author, authorAbt, text, null);
    story.setPlace(getPlaceById(p.id()));
    
    return story;
  }
  
  public Map<String, Story> getAllTextStories() throws SQLException {
    String query = "SELECT * FROM story;";
    PreparedStatement prep = conn.prepareStatement(query);

    ResultSet rs = prep.executeQuery();
    Map<String, Story> s = new HashMap<String, Story>();
    if (rs == null) {return null;}
    
    String id, author, authorAbt, text, placeID;
    String[] date;
    while (rs.next()) {
      id = rs.getString(1);
      date = rs.getString(2).split("-");
      author = rs.getString(3);
      authorAbt = rs.getString(4);
      text = rs.getString(5);
      placeID = rs.getString(6);
      Story story = new Story(id, date, author, authorAbt, text, null);
      story.setPlace(getPlaceById(placeID));
      s.put(id, story);
    }
    
    return s;
  }
  
  public List<Place> getPlaceByLatLng(LatLng northEast, LatLng southWest) throws SQLException {
    String query =
        "SELECT * FROM place WHERE" + " lat <= ? AND lat >= ? AND lng <= ? " + "AND lng >= ?;";

    PreparedStatement prep = conn.prepareStatement(query);
    prep.setDouble(1, northEast.lat());
    prep.setDouble(2, southWest.lat());
    prep.setDouble(3, northEast.lng());
    prep.setDouble(4, southWest.lng());

    String id, name, intro, pic_path;
    ResultSet rs = prep.executeQuery();
    List<Place> places = new ArrayList<Place>();
    if (rs == null) {
      return null;
    }

    while (rs.next()) {
      id = rs.getString(1);
      name = rs.getString(2);
      intro = rs.getString(3);
      pic_path = rs.getString(4);
      LatLng point = new LatLng(rs.getDouble(5), rs.getDouble(6));
      places.add(new Place(id, name, intro, pic_path, point));
    }

    rs.close();
    prep.close();

    return places;
  }
  
  public List<Story> getTextStoriesByPlace(String placeID) throws SQLException {
    Place p = getPlaceById(placeID);
    String query = "SELECT * FROM story WHERE place = ?;";
    PreparedStatement prep = conn.prepareStatement(query);
    prep.setString(1, placeID);
    
    String id, authorName, authorAbt, text, date;
    ResultSet rs = prep.executeQuery();
    List<Story> stories = new ArrayList<Story>();
    
    while (rs.next()) {
      id = rs.getString(1);
      date = rs.getString(2);
      String[] dateArray = date.split("-");
      authorName = rs.getString(3);
      authorAbt = rs.getString(4);
      text = rs.getString(5);
      Story s = new Story(id, dateArray, authorName, authorAbt, text, null);
      storyCache.put(id, s);
      stories.add(s);
    }
  
    
    query = "SELECT * FROM audio_stories WHERE place = ?;";
    prep = conn.prepareStatement(query);
    prep.setString(1, placeID);
    rs = prep.executeQuery();
    String path;
    
    while (rs.next()) {
      id = rs.getString(1);
      date = rs.getString(2);
      String[] dateArray = date.split("-");
      authorName = rs.getString(3);
      authorAbt = rs.getString(4);
      path = rs.getString(5);
      Story s = new Story(id, dateArray, authorName, authorAbt, null, path);
      storyCache.put(id, s);
      stories.add(s);
    }
    
    rs.close();
    prep.close();
    p.setStories(stories);    
    return stories;
  }

  public void addPicture(byte[] picture) throws SQLException {
    String query = "INSERT OR IGNORE INTO 'picture' VALUES ('p1', ?);";
    PreparedStatement prep = conn.prepareStatement(query);
    prep.setBytes(1, picture);
    prep.executeBatch();
    prep.close();
  }
}
