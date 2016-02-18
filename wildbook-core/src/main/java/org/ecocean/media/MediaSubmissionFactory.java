package org.ecocean.media;

import java.util.List;

import org.ecocean.LocationFactory;
import org.ecocean.security.UserFactory;

import com.samsix.database.Database;
import com.samsix.database.DatabaseException;
import com.samsix.database.RecordSet;
import com.samsix.database.SqlFormatter;
import com.samsix.database.SqlInsertFormatter;
import com.samsix.database.SqlRelationType;
import com.samsix.database.SqlStatement;
import com.samsix.database.SqlUpdateFormatter;
import com.samsix.database.SqlWhereFormatter;
import com.samsix.database.Table;

public class MediaSubmissionFactory {
    public final static String TABLENAME_MEDIASUBMISSION = "mediasubmission";
    public final static String ALIAS_MEDIASUBMISSION = "ms";

    public final static String TABLENAME_MEDIASUB_MEDIA = "mediasubmission_media";
    public final static String ALIAS_MEDIASUB_MEDIA = "msm";

    private MediaSubmissionFactory() {
        // prevent instantiation
    }

    public static SqlStatement getStatement()
    {
        SqlStatement sql = new SqlStatement(TABLENAME_MEDIASUBMISSION, ALIAS_MEDIASUBMISSION);
        UserFactory.addAsLeftJoin(ALIAS_MEDIASUBMISSION, UserFactory.PK_USERS, sql);
        return sql;
    }

    public static void save(final Database db,
                            final MediaSubmission media)
        throws DatabaseException
    {
        Table table = db.getTable(TABLENAME_MEDIASUBMISSION);
        if (media.getId() == null) {
            SqlInsertFormatter formatter;
            formatter = new SqlInsertFormatter();
            fillFormatter(db, formatter, media);
            media.setId(table.insertSequencedRow(formatter, "id"));
        } else {
            SqlUpdateFormatter formatter;
            formatter = new SqlUpdateFormatter();
            formatter.append("id", media.getId());
            fillFormatter(db, formatter, media);
            SqlWhereFormatter where = new SqlWhereFormatter();
            where.append("id", media.getId());
            table.updateRow(formatter.getUpdateClause(), where.getWhereClause());
        }
    }


    private static void fillFormatter(final Database db,
                                      final SqlFormatter formatter,
                                      final MediaSubmission media)
    {
        formatter.append("description", media.getDescription());
        formatter.append("subemail", media.getEmail());
        formatter.append("msdate", media.getMsDate());
        formatter.append("subname", media.getName());
        formatter.append("mstime", media.getMsTime());
        formatter.append("submissionid", media.getSubmissionid());
        formatter.append("timesubmitted", media.getTimeSubmitted());
        if (media.getUser() != null) {
            formatter.append("userid", media.getUser().getId());
        }
        formatter.append("status", media.getStatus());

        LocationFactory.fillFormatterWithLocNoId(formatter, media.getLocation());
    }


    public static List<MediaAsset> getMedia(final Database db,
                                            final long msid) throws DatabaseException
    {
//        String sql = "SELECT ma.* FROM mediasubmission_media m"
//                + " INNER JOIN mediaasset ma ON ma.id = m.mediaid"
//                + " WHERE m.mediasubmissionid = " + msid;
        SqlStatement sql = new SqlStatement(TABLENAME_MEDIASUB_MEDIA, ALIAS_MEDIASUB_MEDIA);
        sql.addInnerJoin(ALIAS_MEDIASUB_MEDIA,
                         "mediaid",
                         MediaAssetFactory.TABLENAME_MEDIAASSET,
                         MediaAssetFactory.ALIAS_MEDIAASSET,
                         MediaAssetFactory.PK_MEDIAASSET);
        sql.addSelectTable(MediaAssetFactory.ALIAS_MEDIAASSET);
        sql.addCondition(ALIAS_MEDIASUB_MEDIA, "mediasubmissionid", SqlRelationType.EQUAL, msid);

        return db.selectList(sql, (rs) -> {
            return MediaAssetFactory.valueOf(rs);
        });
    }


    public static MediaSubmission readMediaSubmission(final RecordSet rs) throws DatabaseException
    {
        MediaSubmission ms = new MediaSubmission();

        ms.setDescription(rs.getString("description"));
        ms.setEmail(rs.getString("subemail"));
        ms.setMsTime(rs.getLocalTime("mstime"));
        ms.setId(rs.getInteger("id"));
        ms.setName(rs.getString("subname"));
        ms.setMsDate(rs.getLocalDate("msdate"));
        ms.setSubmissionid(rs.getString("submissionid"));
        ms.setTimeSubmitted(rs.getLongObj("timesubmitted"));
        ms.setUser(UserFactory.readSimpleUser(rs));
        ms.setStatus(rs.getString("status"));

        ms.setLocation(LocationFactory.readLocation(rs));

        return ms;
    }
}
