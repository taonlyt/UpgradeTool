package com.bambu.tool;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

/**
 *
 * @author luotao
 */
public class FileTransfer implements Callable {

    private Socket clientSocket;
    private String filePath;
    private String upgradeSuccessMessage;
    private String upgradeFailMessage;
    private String correctHostMessage;
    private String host;
    private int port;
    private int socketTimeOut;
    private DataInputStream inReader;
    private DataOutputStream outWriter;

    public FileTransfer(Properties properties, String host) {
        this.host = host;
        this.port = Integer.parseInt(properties.getProperty("upgrade.host.connect.port"));
        this.upgradeSuccessMessage = properties.getProperty("upgrade.success.response");
        this.upgradeFailMessage = properties.getProperty("upgrade.fail.response");
        this.correctHostMessage = properties.getProperty("upgrade.host.correct.response");
        this.filePath = properties.getProperty("upgrade.data.file");
        this.socketTimeOut = Integer.parseInt(properties.getProperty("upgrade.connect.timeout"));
        this.clientSocket = createConnect(host, port, socketTimeOut);
    }

    /**
     * 连接服务器
     *
     * @param ip
     * @param port
     * @param timeout
     * @param hostCorrectRsp
     * @return
     */
    private Socket createConnect(String ip, int port, int timeout) {
        Socket socket = null;
        try {
            socket = new Socket(ip, port);
            socket.setSoTimeout(timeout);//设置连接超时时间

        } catch (IOException e) {
            e.printStackTrace();
        }
        return socket;
    }

    /**
     * 释放连接资源
     */
    private void destory() {
        try {
            if (outWriter != null) {
                outWriter.close();
            }
            if (inReader != null) {
                inReader.close();
            }
            if (clientSocket != null) {
                clientSocket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Object call() throws Exception {
        StringBuilder resultBuffer = new StringBuilder();
        ResponseMessage rspMessage = new ResponseMessage();
        try {
            File file = new File(filePath);
            inReader = new DataInputStream(clientSocket.getInputStream());
            outWriter = new DataOutputStream(clientSocket.getOutputStream());
            String outMessage = "";
            String connectMsg = inReader.readUTF();
            if (this.correctHostMessage.equals(connectMsg)) {
                outMessage = "[Info] connect to " + host + " successful.";
                resultBuffer.append(outMessage).append("\n");
                System.out.println(outMessage);
                /**
                 * 合法主机，可以升级。
                 */
                if (file.exists()) {
                    /**
                     * 发送升级文件
                     */
                    rspMessage = sendFile(file);
                } else {
                    rspMessage.setResponseCode(0);
                    rspMessage.setResponseMessage("[Info] upgrade file not found:" + file.getName());
                }
            } else {
                /**
                 * 非法主机，不能升级。
                 */
                rspMessage.setResponseCode(0);
                rspMessage.setResponseMessage("[Info] upgrade host invalid :" + host);
            }
            /**
             * 所有文件传输完后，开始读取返回信息。
             */
            String upgradeResult = rspMessage.getResponseMessage();
            if (upgradeResult != null) {
                if (upgradeResult.contains(upgradeSuccessMessage)) {
                    /**
                     * 1. 升级成功
                     */
                    outMessage = "[OK] " + host + " upgrade successful. [file:" + file.getName() + "][size:" + file.length() + "]";
                    outWriter.writeUTF("at!r\\r");//发送重启命令
                    outWriter.flush();
                } else if (upgradeResult.contains(upgradeFailMessage)) {
                    /**
                     * 2. 升级失败
                     */
                    outMessage = "[Fail] " + host + " upgrade fail.";
                } else {
                    /**
                     * 3. socket 超时后直接推出，暂时不是处理。
                     */
                    outMessage = "[Error] " + host + " upgrade error.";
                }
            }
            rspMessage.setLogMessage(rspMessage.getLogMessage() + "\n" + outMessage);
            if (outMessage.trim().length() > 0) {
                resultBuffer.append(rspMessage.getLogMessage()).append("\n");
            }
            writeToLogFile(resultBuffer.toString(), "upgrade_" + host + ".log");
        } catch (Exception e) {
            e.printStackTrace();
        }
        /**
         * 释放所有连接资源
         */
        destory();

        return resultBuffer.toString();
    }

    /**
     * 写日志文件
     *
     * @param content
     * @param filename
     * @throws IOException
     */
    private void writeToLogFile(String content, String filename) throws IOException {
        BufferedWriter bufwriter = null;
        String file = System.getProperty("user.dir") + "/logs";
        File fileObj = new File(file);
        if (!fileObj.exists()) {
            fileObj.mkdir();
        }
        try {
            OutputStreamWriter writerStream = new OutputStreamWriter(new FileOutputStream(file + "/" + filename), "UTF-8");
            bufwriter = new BufferedWriter(writerStream);
            bufwriter.write(content);
            bufwriter.newLine();
            bufwriter.close();
            System.out.println("[Info] upgrade log file：" + file + "/" + filename);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bufwriter != null) {
                bufwriter.close();
            }
        }
    }

    /**
     * 扫描目录，返回目录及其子目录所有文件集合。
     *
     * @param rootFile
     * @param fileList
     * @return
     */
    private List<File> scanFiles(File rootFile, List<File> fileList) {
        if (rootFile.isDirectory()) {
            File[] subList = rootFile.listFiles();
            for (File file : subList) {
                scanFiles(file, fileList);
            }
        } else if (rootFile.isFile()) {
            fileList.add(rootFile);
        }
        return fileList;
    }

    /**
     * 向服务端传输文件
     *
     * @param clientSocket
     * @param file
     * @throws Exception
     */
    private ResponseMessage sendFile(File file) throws InterruptedException {
        FileInputStream fileInputStream = null;
        StringBuilder logBuffer = new StringBuilder();
        ResponseMessage respMeesage = new ResponseMessage();
        String logMsg = "";
        try {
            if (file.exists()) {
                fileInputStream = new FileInputStream(file);

                // 开始传输文件  
                byte[] bytes = new byte[1024 * 10];
                int length = 0;
                long progress = 0;

                logMsg = "[Info] start to send file " + file.getName() + " ......";
                logBuffer.append(logMsg).append("\n");
                System.out.println(logMsg);

                logMsg = "[Info] progress:";
                logBuffer.append(logMsg);
                System.out.print(logMsg);

                while ((length = fileInputStream.read(bytes, 0, bytes.length)) != -1) {
                    outWriter.write(bytes, 0, length);
                    outWriter.flush();
                    progress += length;
                    logBuffer.append("| ").append(100 * progress / file.length()).append("% |");
                    System.out.print("| " + (100 * progress / file.length()) + "% |");
                }
                logBuffer.append("\n");
                System.out.println();

                logMsg = "[Info] " + file.getName() + " send completed ......\n[Info] wait upgrade result .......\n";
                logBuffer.append(logMsg);
                System.out.print(logMsg);
                Thread.sleep(3000);//等待3秒
                String rspMessage = "successful";//inReader.readUTF();//读取返回信息
                respMeesage.setResponseCode(1);
                respMeesage.setResponseMessage(rspMessage);
            }
            respMeesage.setLogMessage(logBuffer.toString());
        } catch (IOException e) {
            respMeesage.setResponseCode(0);
            respMeesage.setResponseMessage("[Fail] send host: " + host + " file fail: " + file.getName());
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException ex) {

                }
            }
        }
        return respMeesage;
    }

}
