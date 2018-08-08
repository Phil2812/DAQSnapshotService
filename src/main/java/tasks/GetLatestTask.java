package tasks;

import org.apache.log4j.Logger;
import rcms.utilities.daqaggregator.data.DAQ;
import rcms.utilities.daqaggregator.persistence.PersistenceFormat;
import rcms.utilities.daqaggregator.persistence.StructureSerializer;
import utils.DAQSetup;
import utils.SetupManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import utils.Helpers;

/**
 * @author Michail Vougioukas (michail.vougioukas@cern.ch) Discovers latest available setups and sets pointer on
 * DAQSetup objects
 */

public class GetLatestTask implements Runnable {

    SetupManager setupManager;

    private static final Logger logger = Logger.getLogger(GetLatestTask.class);

    public GetLatestTask(SetupManager setupManager) {
        this.setupManager = setupManager;
    }

    @Override
    public void run() {
        Date tic = new Date();

        // act upon a copy of setups and only call setup manager objects just to set values, once the discovery jobs
        // have finished

        List<DAQSetup> setups = setupManager.getAvailableSetups();

        Map<String, String> map = new HashMap<String, String>();

        for (DAQSetup setup : setups) {
            try {
                String result = findLatestSnapshot(setup.getSnapshotPath());
                if (result != null) {
                    map.put(setup.getName(), result);
                }
            } catch (Exception e) {
                logger.warn("Failed finding the latest snapshot for setup: " + setup.getName());
            }
        }

        setupManager.updateLatestSnapshot(map);

        Date toc = new Date();
        logger.debug("Latest setup discovery task for " + map.size() + " setups took " + (toc.getTime() - tic.getTime())
                + " milliseconds");
    }

    private String findLatestSnapshot(String setupSnapshotPath) {
        String path; // path to a smile file
        String ret = "";

        try {
            // implementation based on the temporal ordering of directories and files in the time-based hierarchy

            File root = new File(setupSnapshotPath);

            // case when no snapshots have been produced for this setup
            if (root.length() == 0) {
                return null;
            }

            File[] years = root.listFiles(); // dirs of year

            int maxYear = Helpers.getMax(years); // position for max year

            File[] months = years[maxYear].listFiles();

            int maxMonth = Helpers.getMax(months); // position for max month

            File[] days = months[maxMonth].listFiles();

            int maxDay = Helpers.getMax(days); // position for max day

            File[] hours = days[maxDay].listFiles();

            int maxHour = Helpers.getMax(hours); // position for max hour

            File[] snapshots = hours[maxHour].listFiles();

            // if snapshots in this hour were not found (in practice should not occur)
            if (snapshots.length == 0) {
                return null;
            }

            int maxSnapshotTimestamp = Helpers.getMax(snapshots); // position for snapshot file at max unix timestamp

            // if newest snapshot in this hour was not found (first file discovered is tmp)
            if (snapshots.length == -1) {
                return null;
            }

            path = snapshots[maxSnapshotTimestamp].getAbsolutePath();

            logger.debug("Newest snapshot in " + setupSnapshotPath + " > " + snapshots[maxSnapshotTimestamp].getName());

        } catch (RuntimeException e) {
            e.printStackTrace();
            logger.error("Could not find latest snapshot under root: " + setupSnapshotPath);
            return null;
        }

        // deserialization of snapshot
        ret = Helpers.deserializeSnapshot(path); // can be null or a deserialized snapshot in json string
        logger.debug("Deserialized: " + ret.substring(0, 500));
        return ret;

    }

}
