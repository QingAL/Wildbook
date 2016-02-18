package org.ecocean.admin.media;

import java.nio.file.Path;

import org.ecocean.Global;
import org.ecocean.media.AssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.mmutil.MediaUtilities;

import com.samsix.database.Database;
import com.samsix.database.DatabaseException;
import com.samsix.database.SqlUpdateFormatter;
import com.samsix.database.Table;
import com.samsix.util.UtilException;
import com.samsix.util.app.AbstractApplication;

public class RecreateResized extends AbstractApplication {
    private Integer storeid;
    private boolean nullOnly;
    private long total;
    private long count = 0;

    @Override
    protected void addOptions() {
        super.addOptions();

        addRequiredOption("s", "storeid", "id of local asset store to use");
        addFlagOption("nullOnly", "create only missing entries in the thumb and midsize");
    }


    @Override
    protected void checkOptions() {
        super.checkOptions();

        if (hasOption("s")) {
            storeid = Integer.parseInt(getOptionValue("s"));
        }
        nullOnly = hasOption("nullOnly");
    }

    @Override
    public void run() throws UtilException {
        super.run();

        Global.INST.init(null, null);

        //
        // TODO: Deal with different thumb and mid asset stores?
        // For now we are storing the mid in the thumb asset store which is
        // assumed to be the main asset store. Weird yes. Just trying to get this
        // working.
        //
        AssetStore store;
        if (storeid != null) {
            store = AssetStore.get(storeid);
        } else {
            store = AssetStore.getDefault();
        }

        try (Database db = Global.INST.getDb()) {
            String criteria = "type = 1 and store = " + storeid;
            if (nullOnly) {
                criteria += " midpath is null or thumbpath is null";
            }

            Table table = db.getTable(MediaAssetFactory.TABLENAME_MEDIAASSET);
            total = table.getCount(criteria);

            table.select((rs) -> {
                MediaAsset ma = MediaAssetFactory.valueOf(rs);

                reportProgress(ma);

                SqlUpdateFormatter formatter = new SqlUpdateFormatter();
                try {
                    final Path file = store.getFullPath(ma.getPath());

                    if (! nullOnly || (nullOnly && ma.getThumbPath() == null)) {
                        store.deleteFrom(ma.getThumbPath());
                        Path thumb = MediaUtilities.createThumbnail(file, store, ma.getPath(), null);
                        formatter.append("thumbpath", thumb.toString());
                    }

                    if (! nullOnly || (nullOnly && ma.getMidPath() == null)) {
                        store.deleteFrom(ma.getMidPath());
                        Path mid = MediaUtilities.createMidSize(file, store, ma.getPath(), null);
                        formatter.append("midpath", mid.toString());
                    }
                } catch (Exception ex) {
                    throw new DatabaseException("Can't create resized images", ex);
                }

                table.updateRow(formatter.getUpdateClause(), "id = " + ma.getID());
            }, criteria);

            System.out.println("\nFinished!\n");

        } catch (DatabaseException ex) {
            throw new UtilException("Trouble creating mid size images", ex);
        }
    }

    private void reportProgress(final MediaAsset ma) {
        System.out.print("\rProcessing " + ++count + " of " + total + " (id:" + ma.getID() + ")...........");
    }

    public static void main(final String args[])
    {
        launch(new RecreateResized(), args);
    }
}
