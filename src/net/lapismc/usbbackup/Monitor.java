/*
 * Copyright 2018 Benjamin Martin
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

import net.lapismc.usbbackup.util.ChecksumFile;

public class Monitor {

    private ChecksumFile file;
    private USBBackup main;
    private boolean started = false;

    public Monitor(String relativePath, USBBackup main) {
        file = new ChecksumFile(relativePath, main);
        this.main = main;
        Thread thread = new Thread(run());
    }

    private Runnable run() {
        return new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    if (file.done) {
                        break;
                    }
                    if (!file.done && file.remoteChecksum != null) {
                        if (!started) {
                            started = true;
                        } else {
                            try {
                                file.thread.interrupt();
                                main.log.numerator++;
                            } catch (SecurityException ignored) {
                            }
                        }
                    }
                    if (i == 10) {
                        try {
                            file.thread.interrupt();
                            main.log.numerator++;
                        } catch (SecurityException ignored) {
                        }
                    }
                    try {
                        wait(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

}
