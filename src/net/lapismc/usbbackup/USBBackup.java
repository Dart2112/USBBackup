/*
 * Copyright 2019 Benjamin Martin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.lapismc.usbbackup;

import net.lapismc.YamlDNA.file.FileConfiguration;
import net.lapismc.YamlDNA.file.YamlConfiguration;
import net.lapismc.usbbackup.util.MyLogger;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class USBBackup {

    public Path remotePath;
    public Path localPath;
    public MyLogger log;
    private String remoteName;
    private List<String> exclude;
    private USBBackup usbBackup;

    public USBBackup() {
        log = new MyLogger();
        config();
        usbBackup = this;
        Runnable runnable = () -> {
            if (!(remotePath.toFile().canRead() && localPath.toFile().canRead() && localPath.toFile().canWrite())) {
                log.info("Cannot read/write files \n Close this instance and try again");
                log.completed = true;
                waitForExit();
                return;
            }
            try {
                if (remoteName != null && !Files.getFileStore(remotePath).name().equals(remoteName)) {
                    String newRemotePath = null;
                    for (Path p : FileSystems.getDefault().getRootDirectories()) {
                        try {
                            FileStore fs = Files.getFileStore(p);
                            if (Objects.equals(fs.name(), remoteName)) {
                                newRemotePath = p.toString();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            log.error("Failed to check for new path");
                        }
                    }
                    if (newRemotePath != null) {
                        log.info("The drive we are looking for last had a name of "
                                + remoteName + ", but we think the drive might have moved from "
                                + Files.getFileStore(remotePath).name() + " to " + newRemotePath);
                        log.info("Please change the value in the config if this is the case");
                    } else {
                        log.info("The remote name doesn't match our records! \nDelete name from config or update the path");
                    }
                    log.completed = true;
                    waitForExit();
                    return;
                }
                if (remoteName == null) {
                    remoteName = Files.getFileStore(remotePath).name();
                    File configFile = new File("config.yml");
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                    config.set("remotePath", remoteName + ";" + remotePath.toString());
                    config.save(configFile);
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            log.info("\n");
            log.info("Backup Starting");
            log.denominator = 0;
            log.numerator = 0;
            log.completed = false;
            for (File f : remotePath.toFile().listFiles()) {
                processFile(f);
            }
            for (File f : localPath.toFile().listFiles()) {
                processLocalFile(f);
            }
            log.allFilesStarted = true;
            log.info("All files started!");
            while (!log.completed) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
            waitForExit();
        };
        new Thread(runnable).start();
    }

    private void waitForExit() {
        System.out.print("Completed, Press enter to exit\n");
        new Thread(() -> {
            try {
                Thread.sleep(10 * 1000);
            } catch (InterruptedException ignored) {
            }
            System.exit(0);
        }).start();
        try {
            //noinspection ResultOfMethodCallIgnored
            System.in.read();
        } catch (IOException ignored) {
        }
        System.exit(0);
    }

    private void processFile(File f) {
        for (String s : exclude) {
            if (f.getAbsolutePath().contains(s)) {
                return;
            }
        }
        if (f.isHidden()) {
            return;
        }
        if (!f.isDirectory()) {
            log.denominator++;
            new Monitor(f.getPath().replace(remotePath.toAbsolutePath().toString(), ""), usbBackup);
        } else {
            File dir = new File(localPath.toString() + File.separator + f.getPath().replace(remotePath.toAbsolutePath().toString(), ""));
            if (!dir.exists()) {
                if (dir.mkdir())
                    log.info("Made dir " + dir.getAbsolutePath().replace(localPath.toString(), ""));
            }
            for (File file : f.listFiles()) {
                processFile(file);
            }
        }
    }

    private void processLocalFile(File f) {
        File remote = new File(f.getPath().replace(localPath.toString(), remotePath.toString()));
        if (f.isDirectory()) {
            if (!f.getName().contains(".git")) {
                for (File file : f.listFiles()) {
                    processLocalFile(file);
                }
            }
        }
        if (!remote.exists() && !f.getPath().contains(".git")) {
            if (FileUtils.deleteQuietly(f)) {
                log.info("Deleted file " + f.getAbsolutePath().replace(localPath.toString(), ""));
            } else {
                log.error("Failed to delete file " + f.getAbsolutePath().replace(localPath.toString(), ""));
            }
        }
    }

    private void config() {
        File configFile = new File("config.yml");
        FileConfiguration config;
        if (!configFile.exists()) {
            try {
                if (!configFile.createNewFile()) {
                    log.error("Could not create config file");
                }
                config = YamlConfiguration.loadConfiguration(configFile);
                config.set("remotePath", " ");
                config.set("localPath", " ");
                exclude = new ArrayList<>();
                exclude.add("~");
                exclude.add("Thumbs.db");
                exclude.add("System Volume Information");
                config.set("exclude", exclude);
                config.save(configFile);
            } catch (IOException e) {
                e.printStackTrace();
                log.error("Failed to generate default config");
            }
            log.info("Default config generated, please enter values then run again");
            System.exit(0);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        if (config.getString("remotePath").contains(";")) {
            String[] remote = config.getString("remotePath").split(";");
            remotePath = new File(remote[1]).toPath();
            remoteName = remote[0];
        } else {
            remoteName = null;
            remotePath = new File(config.getString("remotePath")).toPath();
        }
        localPath = new File(config.getString("localPath")).toPath();
        exclude = config.getStringList("exclude");
    }

    public enum Type {
        Local, Remote
    }

}
