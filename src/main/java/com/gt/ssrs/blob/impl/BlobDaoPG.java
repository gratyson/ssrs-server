package com.gt.ssrs.blob.impl;

import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.exception.DaoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.List;

public class BlobDaoPG implements BlobDao {

    private static final int DELETE_BATCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(BlobDaoPG.class);

    private static final String SAVE_IMAGE_SQL =
            "BEGIN TRANSACTION; " +
                    "INSERT INTO images (image_name, image_oid) VALUES (?, ?) " +
                    "ON CONFLICT (image_name) DO UPDATE " +
                    "     SET image_oid = excluded.image_oid; " +
                    "COMMIT TRANSACTION;";

    private static final String LOAD_IMAGE_SQL =
            "SELECT image_oid FROM images WHERE image_name = ?";

    private static final String SAVE_AUDIO_SQL =
            "BEGIN TRANSACTION; " +
                    "INSERT INTO audio (audio_file_name, audio_oid) VALUES (?, ?) " +
                    "ON CONFLICT (audio_file_name) DO UPDATE " +
                    "     SET audio_oid = excluded.audio_oid; " +
                    "COMMIT TRANSACTION;";

    private static final String LOAD_AUDIO_SQL =
            "SELECT audio_oid FROM audio WHERE audio_file_name = ?";

    private static final String DELETE_IMAGE_SQL_PREFIX =
            "DELETE FROM images WHERE image_name IN (";
    private static final String DELETE_AUDIO_SQL_PREFIX =
            "DELETE FROM audio WHERE audio_file_name IN (";

    private final Connection databaseConnection;

    public BlobDaoPG(Connection blobDatabaseConnection) {

        this.databaseConnection = blobDatabaseConnection;
    }

    @Override
    public void saveImageFile(String name, ByteBuffer bytes) {
        saveBlobFile(SAVE_IMAGE_SQL, name, bytes);
    }

    @Override
    public void deleteImageFile(String name) {
        deleteBlobFiles(DELETE_IMAGE_SQL_PREFIX, List.of(name));
    }

    @Override
    public void deleteAudioFile(String name) {
        deleteBlobFiles(DELETE_AUDIO_SQL_PREFIX, List.of(name));
    }

    @Override
    public void deleteAudioFiles(List<String> names) { deleteBlobFiles(DELETE_AUDIO_SQL_PREFIX, names);}

    @Override
    public int saveAudioFile(String name, ByteBuffer bytes) {
        return saveBlobFile(SAVE_AUDIO_SQL, name, bytes);
    }

    private int saveBlobFile(String saveSql, String name, ByteBuffer bytes) {
        try {
            databaseConnection.beginRequest();
            PreparedStatement ps = databaseConnection.prepareStatement(saveSql);

            ps.setString(1, name);
            ps.setBlob(2, new ByteArrayInputStream(bytes.array()), bytes.array().length);

            int result = ps.executeUpdate();
            databaseConnection.commit();
            databaseConnection.endRequest();
            return result;

        } catch (SQLException ex) {
            String errMsg = "Error attempting to save file " + name + " to blob server. ";
            try {
                databaseConnection.rollback();
                databaseConnection.endRequest();
            } catch (SQLException ex2) {
                errMsg += "Failed to rollback transaction, connection in likely in an unstable state.";
            }

            log.error(errMsg, ex);
            throw new DaoException(errMsg, ex);
        }
    }

    @Override
    public ByteBuffer loadImageFile(String name) {
        return loadBlobFile(LOAD_IMAGE_SQL, name);
    }

    @Override
    public ByteBuffer loadAudioFile(String name) {
        return loadBlobFile(LOAD_AUDIO_SQL, name);
    }

    private ByteBuffer loadBlobFile(String loadSql, String name) {
        try {
            ByteBuffer bytes;

            PreparedStatement ps = databaseConnection.prepareStatement(loadSql);
            ps.setString(1, name);

            ResultSet rs = ps.executeQuery();
            if (rs != null && rs.next()) {
                Blob blob = rs.getBlob(1);
                bytes = ByteBuffer.wrap(blob.getBinaryStream().readAllBytes());
            } else {
                log.warn("No binary data available for image file " + name);
                bytes = ByteBuffer.allocate(0);
            }

            return bytes;
        } catch (SQLException | IOException ex) {
            String errMsg = "Error attempting to load file " + name + " from blob server";
            log.error(errMsg, ex);
            throw new DaoException(errMsg, ex);
        }
    }

    private void deleteBlobFiles(String deleteSqlPrefix, List<String> names) {
        if (names == null || names.size() == 0) {
            return;  // nothing to delete, empty array will also cause syntax error
        }

        for(int offset = 0; offset < names.size(); offset += DELETE_BATCH_SIZE) {
            int chunkSize = names.size() > offset + DELETE_BATCH_SIZE ? DELETE_BATCH_SIZE : names.size() - offset;

            try {
                PreparedStatement ps = databaseConnection.prepareStatement(buildDeleteSql(deleteSqlPrefix, chunkSize));
                for(int i = 0; i < chunkSize; i++) {
                    ps.setString(i + 1, names.get(i + offset));
                }

                ps.executeUpdate();
            } catch (SQLException ex) {
                String errMsg = "Error attempting to delete files [" + names + "] from blob server";
                log.error(errMsg, ex);
                throw new DaoException(errMsg, ex);
            }
        }
    }

    private String buildDeleteSql(String deleteSqlPrefix, int idCount) {
        StringBuilder sql = new StringBuilder("BEGIN TRANSACTION; ");
        sql.append(deleteSqlPrefix);

        for (int i = 0; i < idCount; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append("); ");
        sql.append("COMMIT TRANSACTION;");

        return sql.toString();
    }
}
