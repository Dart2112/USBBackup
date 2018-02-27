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

package net.lapismc.usbbackup.util;

import net.lapismc.usbbackup.USBBackup;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MyLogger {

    public Integer numerator = 0;
    public Integer denominator = 0;
    private Thread thread;
    private Runnable task = new Runnable() {
        @Override
        public void run() {
            while (denominator == 0 || numerator < denominator) {
                System.out.print(numerator + "/" + denominator + " Completed" + "\r");
            }
            System.out.print("Completed                \n");
        }
    };

    public MyLogger(USBBackup main) {
        thread = new Thread(task);
        thread.start();
    }

    public void info(String message) {
        if (message.equals("\n")) {
            System.out.println("                   \n");
            return;
        }
        String timeStamp = new SimpleDateFormat("[dd/MM/yyyy HH:mm:ss]").format(new Date());
        System.out.println(timeStamp + " INFO: " + message);
        System.out.print(numerator + "/" + denominator + " Completed" + "\r");
    }

    public void error(String message) {
        String timeStamp = new SimpleDateFormat("[dd/MM/yyyy HH:mm:ss]").format(new Date());
        System.out.println(timeStamp + " ERROR: " + message);
        System.out.print(numerator + "/" + denominator + " Completed" + "\r");
    }

}
