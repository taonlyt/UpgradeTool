/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bambu.tool;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 *
 * @author luotao
 */
public class UpgradeClient {

    /**
     * 入口
     *
     * @param args
     */
    public static void main(String[] args) {
        /**
         * 扫描网卡获取IP地址(待完成）
         */
        List<String> hostList = new ArrayList();//升级主机，通过扫描获得。
        /**
         * 尝试连接
         */
        String confFile = System.getProperty("user.dir") + "/conf.properties";
        Properties prop = new Properties();
        try {
            /**
             * 加重配置文件 conf.properties
             */
            InputStream in = new BufferedInputStream(new FileInputStream(confFile));
            prop.load(new InputStreamReader(in, "UTF-8"));
            in.close();

            /**
             * 测试开关是否开启
             */
            Boolean isTest = Boolean.parseBoolean(prop.getProperty("upgrade.host.test.on"));
            if (isTest) {
                String hosts = prop.getProperty("upgrade.host.test");
                String[] ips = hosts.split(",");
                hostList.addAll(Arrays.asList(ips));
            }
            int maxPoolNum = Integer.parseInt(prop.getProperty("upgrade.thread.pool.max"));
            ExecutorService pool = Executors.newFixedThreadPool(maxPoolNum);

            for (String ip : hostList) {
                Callable taskTransfer = new FileTransfer(prop, ip);
                Future taskResult = pool.submit(taskTransfer);
            }
            pool.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
