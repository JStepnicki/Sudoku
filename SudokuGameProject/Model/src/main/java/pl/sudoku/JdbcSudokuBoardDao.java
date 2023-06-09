package pl.sudoku;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.sudoku.exception.JdbcException;
import pl.sudoku.exception.SudokuBoardException;

public class JdbcSudokuBoardDao implements Dao<SudokuBoard>, AutoCloseable {
    private final Logger log = LoggerFactory.getLogger(JdbcSudokuBoardDao.class);
    private Connection conn;
    private String name;
    private final ResourceBundle bundle = ResourceBundle.getBundle("Language");

    public String getName() {
        return name;
    }

    private String dbUrl = "jdbc:derby:myDB;create=true";

    public void setName(String name) {
        this.name = name;
    }

    public JdbcSudokuBoardDao() throws JdbcException, SQLException {

        try {
            conn = DriverManager.getConnection(dbUrl);
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new JdbcException(bundle.getString("errorWithConnection"), e);
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE boards ("
                    + "ID_BOARD INT NOT NULL GENERATED ALWAYS AS IDENTITY, "
                    + "BOARD_NAME VARCHAR(100) NOT NULL UNIQUE, "
                    + "CONSTRAINT PK_boards PRIMARY KEY (ID_BOARD))");
            conn.commit();
        } catch (SQLException e) {
            if (e.getSQLState().equals("X0Y32")) {
                log.info(bundle.getString("tableBoardsExist"));
                conn.rollback();
            } else {
                throw new JdbcException(e);
            }
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE fields ("
                    + "ID_BOARD INT NOT NULL, "
                    + "FOREIGN KEY (ID_BOARD) REFERENCES boards (ID_BOARD), "
                    + "x INT NOT NULL, "
                    + "y INT NOT NULL, "
                    + "val INT NOT NULL)");
            conn.commit();
        } catch (SQLException e) {
            if (e.getSQLState().equals("X0Y32")) {
                log.info(bundle.getString("tableFieldsExist"));
                conn.rollback();
            } else {
                throw new JdbcException(e);
            }
        }
    }


    @Override
    public SudokuBoard read() throws JdbcException, SQLException {
        if (name == null) {
            throw new JdbcException(bundle.getString("nameIsNull"));
        }
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(new String(
                     "SELECT x, y, val FROM fields f JOIN boards b ON"
                             + " f.ID_BOARD = b.ID_BOARD WHERE BOARD_NAME = '" + name + "'"))) {
            SudokuBoard sudokuBoard = new SudokuBoard(new BacktrackingSudokuSolver());
            conn.commit();
            while (rs.next()) {
                sudokuBoard.set(rs.getInt("x"),
                        rs.getInt("y"), rs.getInt("val"));
            }
            return sudokuBoard;

        } catch (SQLException | SudokuBoardException e) {
            conn.rollback();
            throw new JdbcException(e);
        }
    }

    @Override
    public void write(SudokuBoard obj) throws JdbcException, SQLException {
        if (name == null) {
            throw new JdbcException(bundle.getString("nameIsNull"));
        }
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(new String("INSERT INTO boards(BOARD_NAME) VALUES ('" + name + "')"),
                    Statement.RETURN_GENERATED_KEYS);
            int id = -1;
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                while (rs.next()) {
                    id = rs.getInt(1);
                }
                for (int row = 0; row < 9; row++) {
                    for (int column = 0; column < 9; column++) {
                        stmt.execute(new String(
                                "INSERT INTO fields(ID_BOARD,x,y,val) "
                                        + "VALUES (" + id + ", " + row + ", " + column + ", "
                                        + obj.get(row, column) + ")"));
                    }
                }
                conn.commit();
            } catch (SQLException | SudokuBoardException e) {
                throw new JdbcException(e);
            }
        } catch (SQLException e) {
            //23505 record with the same unqique value exists
            if (e.getSQLState().equals("23505")) {
                throw new JdbcException(bundle.getString("recordWithSameValueExist"), e);
            }
            try {
                conn.rollback();
            } catch (SQLException e1) {
                throw new JdbcException(e1);
            }
        }
    }

    @Override
    public void close() throws JdbcException {
        try {
            conn.commit();
            conn.close();
            DriverManager.getConnection("jdbc:derby:myDB;shutdown=true");
        } catch (SQLException e) {
            //08006 is code for successful shutdown
            if (!e.getSQLState().equals("08006")) {
                throw new JdbcException(e);
            }

        }
    }


    public ArrayList<String> getBoardsNames() throws JdbcException, SQLException {
        ArrayList<String> names = new ArrayList<>();
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            ResultSet results = stmt.executeQuery("select BOARD_NAME from boards");
            conn.commit();
            while (results.next()) {
                names.add(results.getString("BOARD_NAME"));
            }
            results.close();
        } catch (SQLException sqlExcept) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                throw new JdbcException(e);
            }
        }
        return names;
    }
}



