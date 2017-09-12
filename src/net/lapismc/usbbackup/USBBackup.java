/*
 * Copyright 2017 Benjamin Martin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lapismc.usbbackup;

import net.lapismc.YamlDNA.file.FileConfiguration;
import net.lapismc.YamlDNA.file.YamlConfiguration;
import net.lapismc.usbbackup.util.ChecksumFile;
import net.lapismc.usbbackup.util.MyLogger;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class USBBackup {

    public Path remotePath;
    public String remoteName;
    public Path localPath;
    public List<String> exclude;
    public MyLogger log = new MyLogger();
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private USBBackup usbBackup;
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (!(remotePath.toFile().canRead() && localPath.toFile().canRead() && localPath.toFile().canWrite())) {
                log.info("Cannot read/write files \n Will try again in 5 mins");
                return;
            }
            try {
                if (remoteName != null && !Files.getFileStore(remotePath).name().equals(remoteName)) {
                    log.info("The remote name doesnt match our records! \nDelete name from config or update the path");
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
            log.info("Backup Starting");
            for (File f : remotePath.toFile().listFiles()) {
                processFile(f);
            }
            for (File f : localPath.toFile().listFiles()) {
                processLocalFile(f);
            }
            log.info("All files started! \n");
        }
    };

    public USBBackup() {
        config();
        usbBackup = this;
        scheduler.scheduleWithFixedDelay(runnable, 0l, 5l, TimeUnit.MINUTES);
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
            new ChecksumFile(f.getPath().replace(remotePath.toAbsolutePath().toString(), ""), usbBackup);
        } else {
            File dir = new File(localPath.toString() + File.separator + f.getPath().replace(remotePath.toAbsolutePath().toString(), ""));
            if (!dir.exists()) {
                dir.mkdir();
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
                configFile.createNewFile();
                config = YamlConfiguration.loadConfiguration(configFile);
                config.set("remotePath", " ");
                config.set("localPath", " ");
                exclude = new ArrayList<>();
                exclude.add("~");
                exclude.add("Thumbs.db");
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
