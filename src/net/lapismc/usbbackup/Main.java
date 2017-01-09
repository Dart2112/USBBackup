package net.lapismc.usbbackup;

import net.lapismc.YamlDNA.file.YamlConfiguration;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;

public class Main {

    static ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);
    static HashMap<String, Long> fromMap = new HashMap<>();
    static HashMap<String, Long> toMap = new HashMap<>();
    static HashMap<String, Long> finishedMap = new HashMap<>();
    static YamlConfiguration config;
    static File configFile = new File("." + File.separator + "config.yml");
    static String toPath;
    static String fromPath;

    public static void main(String[] args) {

        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
            if (config.contains("Stop")) {
                config.set("Stop", false);
                try {
                    config.save(configFile);
                } catch (IOException e) {
                    return;
                }
            }
        }

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (!configFile.exists()) {
                    try {
                        configFile.createNewFile();
                        config = YamlConfiguration.loadConfiguration(configFile);
                        config.set("PathFrom", "");
                        config.set("PathTo", "");
                        config.set("Exclude", "");
                        config.set("MaxSizeCheckSum", 50);
                        config.set("MinutesBetweenRuns", 5);
                        config.set("Stop", false);
                        config.save(configFile);
                        scheduledExecutorService.shutdown();
                        fromMap = null;
                        toMap = null;
                        finishedMap = null;
                        config = null;
                        System.gc();
                        System.exit(0);
                    } catch (IOException e) {
                        return;
                    }
                }
                config = YamlConfiguration.loadConfiguration(configFile);
                if (config.getString("PathFrom").equalsIgnoreCase("")) {
                    return;
                }
                if (config.getBoolean("Stop")) {
                    scheduledExecutorService.shutdown();
                    fromMap = null;
                    toMap = null;
                    finishedMap = null;
                    config = null;
                    System.gc();
                    System.exit(0);
                }
                fromPath = config.getString("PathFrom");
                File from = new File(fromPath);
                if (!from.canRead()) {
                    return;
                }
                toPath = config.getString("PathTo");
                File to = new File(toPath);
                if (!to.canRead()) {
                    return;
                }
                File[] fromArray = from.listFiles();
                File[] toArray = to.listFiles();
                for (File f : fromArray) {
                    if (!(f.getName().contains("~") || f.isHidden())) {
                        if (f.isDirectory()) {
                            addDir(f.getPath().replace(fromPath, ""), type.From);
                        } else {
                            addFile(f.getPath().replace(fromPath, ""), type.From);
                        }
                    }
                }
                if (config.contains("OldHashes") && config.getStringList("OldHashes").size() > 1) {
                    System.out.println("Getting old hashes!");
                    List<String> oldHashes = config.getStringList("OldHashes");
                    for (String s : oldHashes) {
                        String[] stringArray = s.split(":");
                        Long l = Long.parseLong(stringArray[0]);
                        String path = stringArray[1];
                        toMap.put(path, l);
                    }
                } else {
                    System.out.println("No old hashes found, getting them now");
                    for (File f : toArray) {
                        if (f.isDirectory()) {
                            addDir(f.getPath().replace(toPath, ""), type.To);
                        } else {
                            addFile(f.getPath().replace(toPath, ""), type.To);
                        }
                    }
                }
                for (String s : fromMap.keySet()) {
                    System.out.println("Comparing file at " + s);
                    Long remote = fromMap.get(s);
                    if (toMap.containsKey(s)) {
                        Long current = toMap.get(s);
                        if (!current.equals(remote)) {
                            copyFile(s);
                            System.out.println("Files are different, overwriting");
                        } else {
                            System.out.println("Files are same, ignoring");
                        }
                        toMap.remove(s);
                    } else {
                        System.out.println("File doesn't exist, copying");
                        copyFile(s);
                    }
                    if (toMap.containsKey(s)) {
                        System.out.println("File doesn't need to exist anymore, deleting");
                        File f = new File(toPath + s);
                        f.delete();
                        toMap.remove(s);
                    }
                }
                for (String s : toMap.keySet()) {
                    File f = new File(toPath + s);
                    f.delete();
                }
                File finished = new File(toPath);
                File[] finishedArray = finished.listFiles();
                System.out.println("Getting checksums of currently sorted files ready for next time");
                for (File f : finishedArray) {
                    if (f.isDirectory()) {
                        addDir(f.getPath().replace(toPath, ""), type.Finished);
                    } else {
                        addFile(f.getPath().replace(toPath, ""), type.Finished);
                    }
                }
                System.out.println("\n");
                List<String> finishedList = new ArrayList<>();
                for (String s : finishedMap.keySet()) {
                    Long l = finishedMap.get(s);
                    finishedList.add(l + ":" + s);
                }
                config.set("OldHashes", finishedList);
                try {
                    config.save(configFile);
                } catch (IOException e) {
                    return;
                }
            }
        };

        final ScheduledFuture<?> scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(runnable, 0l, Long.parseLong(config.getString("MinutesBetweenRuns")),
                TimeUnit.MINUTES);
    }

    public static void copyFile(String relativePath) {
        String newPath = toPath + relativePath;
        File f = new File(fromPath + relativePath);
        try {
            FileUtils.copyFile(f, new File(newPath));
        } catch (IOException e) {
            return;
        }
    }

    public static void addDir(String relativePath, type type) {
        System.out.println("loading files in " + relativePath);
        File dir;
        String path;
        switch (type) {
            case From:
                path = fromPath;
                dir = new File(fromPath + relativePath);
                File file = new File(dir.getPath().replace(fromPath, toPath));
                file.mkdir();
                break;
            case To:
                path = toPath;
                dir = new File(toPath + relativePath);
                break;
            case Finished:
                path = toPath;
                dir = new File(toPath + relativePath);
                break;
            default:
                path = null;
                dir = null;
        }
        if (!dir.canRead()) {
            return;
        }
        File[] files = dir.listFiles();
        for (File f : files) {
            if (f.isDirectory()) {
                addDir(f.getPath().replace(path, ""), type);
            } else {
                addFile(f.getPath().replace(path, ""), type);
            }
        }
    }

    public static void addFile(String relativePath, type type) {
        File file;
        switch (type) {
            case To:
                file = new File(toPath + relativePath);
                break;
            case From:
                file = new File(fromPath + relativePath);
                break;
            case Finished:
                file = new File(toPath + relativePath);
                break;
            default:
                file = null;
                break;
        }
        if (isExcluded(relativePath) || file.getName().contains("~") || file.isHidden()) {
            return;
        }
        Long l;
        if (file.length() / 1024 / 1024 > config.getInt("MaxSizeCheckSum")) {
            System.out.println("File " + file.getName() + " Greater than 100 MB, Repalcing checksum with size");
            l = file.length();
        } else {
            System.out.println("Getting checksum of " + file.getName());
            l = doChecksum(file);
        }
        System.out.println("Checksum of " + file.getName() + " is " + l);
        switch (type) {
            case From:
                fromMap.put(file.getPath().replace(fromPath, ""), l);
                break;
            case To:
                toMap.put(file.getPath().replace(toPath, ""), l);
                break;
            case Finished:
                finishedMap.put(file.getPath().replace(toPath, ""), l);
                break;
        }
    }

    public static boolean isExcluded(String relativePath) {
        List<String> excludeList = config.getStringList("Exclude");
        for (String exclude : excludeList) {
            if (relativePath.contains(exclude)) {
                return true;
            }
        }
        return false;
    }

    public static Long doChecksum(File file) {
        try {
            CheckedInputStream cis = null;
            FileInputStream fis = null;
            long fileSize = 0;
            try {
                // Computer Adler-32 checksum
                fis = new FileInputStream(file);
                cis = new CheckedInputStream(fis, new Adler32());
                fileSize = file.length();
            } catch (FileNotFoundException e) {
                cis.close();
                fis.close();
            }
            byte[] buf = new byte[128];
            while (cis.read(buf) >= 0) {
            }
            Long l = cis.getChecksum().getValue();
            cis.close();
            fis.close();
            return l;
        } catch (IOException e) {
            return null;
        }
    }

    public enum type {
        From, To, Finished;
    }

}
