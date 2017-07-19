/*
 * Copyright 2017 Benjamin Martin
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
import net.lapismc.usbbackup.util.ChecksumFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class USBBackup {

    public Path remotePath;
    public Path localPath;
    public List<String> exclude;
    public Log log = LogFactory.getLog(USBBackup.class);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private USBBackup usbBackup;
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
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
            for (File file : f.listFiles()) {
                processLocalFile(file);
            }
        }
        if (!remote.exists()) {
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
        remotePath = new File(config.getString("remotePath")).toPath();
        localPath = new File(config.getString("localPath")).toPath();
        exclude = config.getStringList("exclude");
    }

    public enum Type {
        Local, Remote
    }

}
