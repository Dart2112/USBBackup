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

package net.lapismc.usbbackup.util;

import net.lapismc.usbbackup.USBBackup;
import net.lapismc.usbbackup.USBBackup.Type;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;

public class ChecksumFile {

    private String relativePath;
    private USBBackup main;
    private Long remoteChecksum;
    private Long localChecksum;
    private boolean force = false;
    private Thread thread = new Thread(runChecksum());

    public ChecksumFile(String relativePath, USBBackup main) {
        this.relativePath = relativePath;
        this.main = main;
        thread.start();
    }

    public Runnable runChecksum() {
        return new Runnable() {
            @Override
            public void run() {
                File local = new File(main.localPath.toAbsolutePath() + File.separator + relativePath);
                if (local.exists()) {
                    localChecksum = getChecksum(local, Type.Local);
                } else {
                    force = true;
                    main.log.info("Local file not found, forcing copy of " + relativePath);
                }
                File remote = new File(main.remotePath.toAbsolutePath() + relativePath);
                remoteChecksum = getChecksum(remote, Type.Remote);
                copyFile();
            }
        };
    }

    private void copyFile() {
        if (!force) {
            if (localChecksum.equals(remoteChecksum)) {
                return;
            }
        }
        File dest = new File(main.localPath.toAbsolutePath().toString() + File.separator + relativePath);
        File from = new File(main.remotePath.toAbsolutePath().toString() + File.separator + relativePath);
        try {
            if (!dest.exists()) {
                dest.createNewFile();
            }
            if (!from.exists()) {
                return;
            }
            FileUtils.copyFile(from, dest);
        } catch (IOException e) {
            main.log.error(e.getMessage());
            main.log.error("Transfer failed, See above error!");
        }
        main.log.info("File copied at " + relativePath);
    }

    private Long getChecksum(File file, Type type) {
        if (!file.exists()) {
            return 0l;
        }
        if (file.length() > 1000000) {
            return file.length();
        }
        try {
            CheckedInputStream cis = null;
            FileInputStream fis = null;
            long fileSize = 0;
            try {
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
            main.log.error(e.getMessage());
            main.log.error("Failed to get checksum for " + relativePath + " " + type);
            return null;
        }
    }

}
